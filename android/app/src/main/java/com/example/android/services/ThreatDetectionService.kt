package com.example.android.services

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.telephony.PhoneStateListener
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.example.android.R
import com.example.android.activities.MainActivity
import com.example.android.db.DatabaseRepository
import com.example.android.ml.AudioClassifier
import com.example.android.ml.YoloV8TFLiteDetector
import com.example.android.models.ClassificationResult
import com.example.android.models.ThreatEvent
import com.example.android.utils.AudioMonitoringService
import com.example.android.utils.CameraHelper
import com.example.android.utils.LocationHelper
import com.example.android.utils.NotificationHelper
import com.example.android.utils.SafeWordHelper
import com.example.android.utils.SharedPrefsHelper
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.sqrt

/**
 * 24x7 Silent Foreground Service for continuous protection
 */
class ThreatDetectionService : LifecycleService() {

    companion object {
        private const val TAG = "ThreatDetectionService"
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "silent_protection_channel"
        private const val WARNING_CHANNEL_ID = "warning_channel"

        private const val SAMPLE_RATE = 16000
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        private const val BUFFER_SIZE_SECONDS = 1
        private const val BUFFER_SIZE = SAMPLE_RATE * BUFFER_SIZE_SECONDS
        private const val CONSECUTIVE_DETECTIONS = 2
    }

    private var threatThreshold = 0.45f

    private var isRunning = false
    private var isPausedByCall = false
    private var foregroundStartFailed = false
    private var audioRecord: AudioRecord? = null
    private var recordingJob: Job? = null
    private var unverifiedSosJob: Job? = null
    
    private lateinit var classifier: AudioClassifier
    private lateinit var incidentRecorder: AudioMonitoringService

    // Lazy-load YOLO model only when camera verification is needed,
    // not during service onCreate. Loading TFLite models is I/O-heavy
    // and was causing main thread jank at startup.
    // Uses a backing flag to avoid attempting load during onDestroy.
    private var yoloDetectorLoaded = false
    private var yoloDetector: YoloV8TFLiteDetector? = null
        get() {
            if (field == null && !yoloDetectorLoaded) {
                yoloDetectorLoaded = true
                try {
                    field = YoloV8TFLiteDetector(this@ThreatDetectionService)
                    Log.d(TAG, "YOLOv8 model loaded lazily on demand")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to load YOLO model lazily: ${e.message}")
                }
            }
            return field
        }

    private var safeWordHelper: SafeWordHelper? = null

    private var consecutiveThreats = 0
    private var lastDetectionTime = 0L
    private var lastLocationCheckTime = 0L
    private var lastHazardNotificationTime = 0L
    
    private var wakeLock: PowerManager.WakeLock? = null
    private lateinit var prefsHelper: SharedPrefsHelper
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var repository: DatabaseRepository
    
    private var telephonyManager: TelephonyManager? = null
    private var phoneStateListener: PhoneStateListener? = null
    private var telephonyCallback: Any? = null // Using Any to avoid class loading errors on old APIs

    override fun onCreate() {
        super.onCreate()
        prefsHelper = SharedPrefsHelper(this)
        repository = DatabaseRepository(this)
        threatThreshold = prefsHelper.sensitivity

        createNotificationChannel()
        NotificationHelper.createNotificationChannels(this)

        val notification = createSilentNotification()
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                var foregroundTypes = ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE or ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    foregroundTypes = foregroundTypes or ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA
                }
                startForeground(NOTIFICATION_ID, notification, foregroundTypes)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start foreground service: ${e.message}")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && e is android.app.ForegroundServiceStartNotAllowedException) {
                Log.e(TAG, "Android 14 ForegroundServiceStartNotAllowedException caught. Stopping service gracefully to prevent crash.")
            }
            foregroundStartFailed = true
            stopSelf()
            return
        }

        classifier = AudioClassifier()
        classifier.initialize(this)
        incidentRecorder = AudioMonitoringService(this)
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager?
        wakeLock = powerManager?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "SafeGuardAI::ThreatDetectionWakeLock")
        wakeLock?.acquire(24 * 60 * 60 * 1000L /*24 hours*/)

        // YOLO model loading removed from onCreate — now lazy-loaded
        // on first call to runVisualVerification. This cuts ~500ms from
        // service startup time on the main thread.

        safeWordHelper = SafeWordHelper(
            context = this,
            prefsHelper = prefsHelper,
            onTriggerWord = {
                Log.w(TAG, "TRIGGER WORD DETECTED: Dispatching SOS instantly!")
                unverifiedSosJob?.cancel()
                unverifiedSosJob = null

                val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager?
                nm?.cancel(1002)

                val fakeResult = ClassificationResult(0.0f, 1.0f, 1, 1.0f, 0L)
                dispatchConfirmedEmergency(fakeResult, false)
                safeWordHelper?.stopListening()
            },
            onSafeWord = {
                Log.w(TAG, "SAFE WORD DETECTED: Cancelling SOS buffer!")
                unverifiedSosJob?.cancel()
                unverifiedSosJob = null

                val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager?
                nm?.cancel(1002)
                safeWordHelper?.stopListening()
            }
        )
        
        registerCallStateListener()
    }

    private fun registerCallStateListener() {
        telephonyManager = getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager?
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "READ_PHONE_STATE permission denied. Cannot detect phone calls.")
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val callback = object : TelephonyCallback(), TelephonyCallback.CallStateListener {
                override fun onCallStateChanged(state: Int) {
                    handleCallStateChange(state)
                }
            }
            telephonyCallback = callback
            telephonyManager?.registerTelephonyCallback(mainExecutor, callback)
        } else {
            phoneStateListener = object : PhoneStateListener() {
                @Deprecated("Deprecated in Java")
                override fun onCallStateChanged(state: Int, phoneNumber: String?) {
                    handleCallStateChange(state)
                }
            }
            telephonyManager?.listen(phoneStateListener, PhoneStateListener.LISTEN_CALL_STATE)
        }
    }

    private fun handleCallStateChange(state: Int) {
        when (state) {
            TelephonyManager.CALL_STATE_OFFHOOK, TelephonyManager.CALL_STATE_RINGING -> {
                Log.w(TAG, "Phone call active. Pausing background audio monitoring.")
                isPausedByCall = true
                stopAudioMonitoring()
            }
            TelephonyManager.CALL_STATE_IDLE -> {
                Log.i(TAG, "Phone call ended. Resuming audio monitoring.")
                isPausedByCall = false
                startAudioMonitoring()
            }
        }
    }

    private fun unregisterCallStateListener() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (telephonyCallback as? TelephonyCallback)?.let { telephonyManager?.unregisterTelephonyCallback(it) }
        } else {
            phoneStateListener?.let { telephonyManager?.listen(it, PhoneStateListener.LISTEN_NONE) }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        
        if (foregroundStartFailed) {
            Log.e(TAG, "Bailing out of onStartCommand because foreground start failed.")
            return START_NOT_STICKY
        }

        if (intent != null && "ACTION_CANCEL_SOS" == intent.action) {
            Log.i(TAG, "User cancelled the unverified SOS buffer. False alarm stopped.")
            unverifiedSosJob?.cancel()
            unverifiedSosJob = null
            
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager?
            nm?.cancel(1002)
            safeWordHelper?.stopListening()
            return START_STICKY
        }

        if (!isRunning && !isPausedByCall) {
            startAudioMonitoring()
        }
        return START_STICKY
    }

    private fun startAudioMonitoring() {
        if (isRunning) return

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "Recording permission missing.")
            return
        }

        try {
            val minBufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
            audioRecord = AudioRecord(MediaRecorder.AudioSource.MIC, SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT,
                    max(minBufferSize, BUFFER_SIZE * 2))

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "AudioRecord initialization failed.")
                return
            }

            audioRecord?.startRecording()
            isRunning = true
            
            recordingJob = lifecycleScope.launch(Dispatchers.IO) {
                audioRecordingLoop()
            }
            Log.d(TAG, "Monitoring started.")
        } catch (e: Exception) {
            Log.e(TAG, "Error starting audio: ${e.message}")
        }
    }

    private fun stopAudioMonitoring() {
        isRunning = false
        recordingJob?.cancel()
        recordingJob = null
        
        try {
            if (audioRecord?.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                audioRecord?.stop()
            }
            audioRecord?.release()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping AudioRecord: ${e.message}")
        }
        audioRecord = null
    }

    private suspend fun audioRecordingLoop() {
        val audioBuffer = ShortArray(BUFFER_SIZE)
        while (isRunning) {
            if (isPausedByCall) {
                delay(1000)
                continue
            }

            try {
                val samplesRead = audioRecord?.read(audioBuffer, 0, BUFFER_SIZE) ?: 0
                if (samplesRead > 0) {
                    processAudioChunk(audioBuffer)
                    checkHazardZoneProximity()
                } else if (samplesRead == AudioRecord.ERROR_INVALID_OPERATION) {
                    Log.w(TAG, "Microphone temporarily lost (possible conflict).")
                    // Do not poll here anymore. The TelephonyManager handles phone calls.
                    // We simply break the loop. If it's a transient error, we could retry, but
                    // let's rely on the call state idle trigger to wake us back up.
                    break
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in recording thread: ${e.message}")
                break
            }
        }
    }

    private fun processAudioChunk(audioData: ShortArray) {
        if (!hasVoiceActivity(audioData)) {
            consecutiveThreats = 0
            return
        }

        val result = classifier.classify(audioData)
        if (result != null && result.isDistress && result.confidence >= threatThreshold) {
            consecutiveThreats++
            if (consecutiveThreats >= CONSECUTIVE_DETECTIONS) {
                onThreatDetected(result)
                consecutiveThreats = 0
            }
        } else if (consecutiveThreats > 0) {
            consecutiveThreats--
        }
    }

    private fun checkHazardZoneProximity() {
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastLocationCheckTime < 120000) return
        lastLocationCheckTime = currentTime

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) return

        fusedLocationClient.lastLocation.addOnSuccessListener { location: Location? ->
            if (location == null) return@addOnSuccessListener
            
            repository.getAllEvents { hazardZones: List<ThreatEvent> ->
                var nearHazard = false
                for (zone in hazardZones) {
                    val distance = LocationHelper.calculateDistance(location.latitude, location.longitude,
                            zone.latitude, zone.longitude)
                    if (distance < 500) {
                        nearHazard = true
                        if (currentTime - lastHazardNotificationTime > 3600000) {
                            NotificationHelper.showEmergencyNotification(this@ThreatDetectionService, "⚠️ High Risk Area Detected",
                                    "You are entering a location where a previous threat was reported. Protection sensitivity has been increased.")
                            lastHazardNotificationTime = currentTime
                        }
                        break
                    }
                }
                val baseSensitivity = prefsHelper.sensitivity
                threatThreshold = if (nearHazard) (baseSensitivity - 0.20f) else baseSensitivity
            }
        }
    }

    private fun hasVoiceActivity(audioData: ShortArray): Boolean {
        var sum = 0.0
        for (sample in audioData) {
            sum += sample * sample
        }
        return sqrt(sum / audioData.size) > 100.0  // Lowered from 500 to detect quieter/speaker-playback sounds
    }

    private fun onThreatDetected(result: ClassificationResult) {
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastDetectionTime < 45000) return
        lastDetectionTime = currentTime

        Log.i(TAG, "Audio Threat Detected! Checking Proximity Sensor...")

        isPausedByCall = true
        stopAudioMonitoring()

        val sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager?
        val proximitySensor = sensorManager?.getDefaultSensor(Sensor.TYPE_PROXIMITY)

        if (proximitySensor == null) {
            runVisualVerification(result)
            resumeMonitoringAfterDelay()
            return
        }

        var sensorChecked = false
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                if (sensorChecked) return
                sensorChecked = true
                sensorManager.unregisterListener(this)

                val distance = event.values[0]
                if (distance < proximitySensor.maximumRange && distance < 2.0f) {
                    Log.w(TAG, "Phone is concealed (pocket/bag). Bypassing YOLO Camera check. Dispatching SOS!")
                    dispatchConfirmedEmergency(result, true)
                } else {
                    Log.i(TAG, "Phone is in open space. Waking up YOLO Visual Filter...")
                    runVisualVerification(result)
                }
                resumeMonitoringAfterDelay()
            }
            override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {}
        }

        lifecycleScope.launch(Dispatchers.Main) {
            delay(1000)
            if (!sensorChecked) {
                sensorChecked = true
                sensorManager.unregisterListener(listener)
                Log.w(TAG, "Proximity sensor timeout. Proceeding with Visual Verification.")
                runVisualVerification(result)
                resumeMonitoringAfterDelay()
            }
        }
        sensorManager.registerListener(listener, proximitySensor, SensorManager.SENSOR_DELAY_FASTEST)
    }

    private fun resumeMonitoringAfterDelay() {
        lifecycleScope.launch(Dispatchers.Main) {
            delay(60000)
            isPausedByCall = false
            startAudioMonitoring()
        }
    }

    // Reusable camera helper instance to avoid memory leaks from repeated creation
    private var cameraHelper: CameraHelper? = null

    private fun getOrCreateCameraHelper(): CameraHelper {
        if (cameraHelper == null) {
            cameraHelper = CameraHelper(this)
        }
        return cameraHelper!!
    }

    private fun shutdownCameraHelper() {
        cameraHelper?.shutdown()
        cameraHelper = null
    }

    private fun runVisualVerification(result: ClassificationResult) {
        // Try both cameras: rear first (attacker direction), then front (user's face/state)
        captureFromCamera(result, false)
    }

    private fun captureFromCamera(result: ClassificationResult, useFrontCamera: Boolean) {
        val helper = getOrCreateCameraHelper()
        val cameraLabel = if (useFrontCamera) "front" else "rear"

        helper.startCameraAndCapture(this, useFrontCamera, object : CameraHelper.PhotoCallback {
            override fun onPhotoCaptured(bitmap: Bitmap) {
                var confirmedThreat = false
                if (yoloDetector != null) {
                    confirmedThreat = yoloDetector!!.detectVisualThreat(bitmap)
                }

                if (confirmedThreat) {
                    Log.w(TAG, "YOLO verified threat from $cameraLabel camera! Dispatching SOS...")
                    shutdownCameraHelper()
                    dispatchConfirmedEmergency(result, true)
                } else if (!useFrontCamera) {
                    // Rear camera didn't detect a threat — try the front camera next
                    Log.d(TAG, "No threat from rear camera, trying front camera...")
                    captureFromCamera(result, true)
                } else {
                    // Both cameras cleared — start the unverified SOS buffer
                    Log.w(TAG, "Both cameras cleared. Starting 15-second Unverified SOS Buffer!")
                    shutdownCameraHelper()
                    triggerUnverifiedSOSBuffer(result)
                }
            }

            override fun onError(e: Exception) {
                if (!useFrontCamera) {
                    // Rear camera failed — try front camera as fallback
                    Log.w(TAG, "Rear camera failed (${e.message}), trying front camera...")
                    captureFromCamera(result, true)
                } else {
                    // Both cameras failed — proceed with audio-only emergency
                    Log.e(TAG, "Both cameras failed. Defaulting to audio-only emergency...")
                    shutdownCameraHelper()
                    dispatchConfirmedEmergency(result, true)
                }
            }
        })
    }

    private fun triggerUnverifiedSOSBuffer(result: ClassificationResult) {
        // Cancel any existing SOS buffer first to prevent double dispatch
        unverifiedSosJob?.let {
            it.cancel()
            unverifiedSosJob = null
        }
        val cancelIntent = Intent(this, ThreatDetectionService::class.java).apply {
            action = "ACTION_CANCEL_SOS"
        }
        val cancelPendingIntent = PendingIntent.getService(this, 1002, cancelIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

        val builder = NotificationCompat.Builder(this, WARNING_CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setContentTitle("🚨 Are you safe?")
                .setContentText("Threat detected. SOS dispatching in 15s! Tap to Cancel or say the Safe Word.")
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setDefaults(0)
                .setVibrate(longArrayOf(0, 500, 500, 500, 500, 500))
                .addAction(android.R.drawable.ic_menu_close_clear_cancel, "IM SAFE (CANCEL)", cancelPendingIntent)
        
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager?
        nm?.notify(1002, builder.build())

        unverifiedSosJob = lifecycleScope.launch(Dispatchers.Main) {
            delay(15000)
            Log.w(TAG, "Unverified SOS Buffer expired. Dispatching Emergency Protocols!")
            nm?.cancel(1002)
            safeWordHelper?.stopListening()
            dispatchConfirmedEmergency(result, false)
        }
        
        safeWordHelper?.startListening()
    }

    private fun dispatchConfirmedEmergency(result: ClassificationResult, callPolice: Boolean) {
        incidentRecorder.startIncidentRecording()

        // Use fresh high-accuracy GPS, with 5-second fallback to last known
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            val cancellationTokenSource = com.google.android.gms.tasks.CancellationTokenSource()
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                cancellationTokenSource.cancel()
            }, 5000)

            fusedLocationClient.getCurrentLocation(
                com.google.android.gms.location.Priority.PRIORITY_HIGH_ACCURACY,
                cancellationTokenSource.token
            ).addOnSuccessListener { location: Location? ->
                if (location != null) {
                    storeAndSyncThreat(result, location)
                } else {
                    // Fallback to last known
                    fusedLocationClient.lastLocation.addOnSuccessListener { lastLoc: Location? ->
                        if (lastLoc != null) storeAndSyncThreat(result, lastLoc)
                    }
                }
            }.addOnFailureListener {
                // Fallback to last known on failure
                fusedLocationClient.lastLocation.addOnSuccessListener { lastLoc: Location? ->
                    if (lastLoc != null) storeAndSyncThreat(result, lastLoc)
                }
            }
        }

        val emergencyIntent = Intent(this, EmergencyResponseService::class.java).apply {
            putExtra("confidence", result.confidence)
            putExtra("distress_prob", result.distressProbability)
            putExtra("call_police", callPolice)
        }

        ContextCompat.startForegroundService(this, emergencyIntent)

        prefsHelper.incrementThreatCount()
        prefsHelper.incrementDetectionCount()
    }

    private fun storeAndSyncThreat(result: ClassificationResult, location: Location) {
        val event = ThreatEvent(System.currentTimeMillis(), result.confidence,
                result.distressProbability, location.latitude, location.longitude)
        repository.insertEvent(event)

        // Push to Firebase Realtime Database for Web Dashboard Sync
        try {
            val database = FirebaseDatabase.getInstance()
            val threatsRef = database.getReference("threats")
            val threatId = threatsRef.push().key
            if (threatId != null) {
                val threatData = mapOf(
                    "id" to threatId,
                    "timestamp" to event.timestamp,
                    "confidence" to event.confidence,
                    "distressProbability" to event.distressProbability,
                    "latitude" to event.latitude,
                    "longitude" to event.longitude,
                    "type" to "Audio Threat",
                    "status" to "critical"
                )
                threatsRef.child(threatId).setValue(threatData)
                Log.i(TAG, "Successfully pushed threat payload to Firebase: $threatId")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to push threat to Firebase (Offline mode active): ${e.message}")
        }
    }

    override fun onDestroy() {
        stopAudioMonitoring()
        if (!foregroundStartFailed) {
            unregisterCallStateListener()
        }
        if (this::classifier.isInitialized) {
            classifier.close()
        }
        // Only close YOLO if it was actually loaded (avoid triggering lazy init on destroy)
        if (yoloDetectorLoaded) {
            yoloDetector?.close()
        }
        if (this::incidentRecorder.isInitialized) {
            incidentRecorder.destroy()
        }
        safeWordHelper?.destroy()
        shutdownCameraHelper()
        
        if (wakeLock?.isHeld == true) {
            wakeLock?.release()
        }
        super.onDestroy()
    }

    override fun onBind(intent: Intent): IBinder? {
        super.onBind(intent)
        return null
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "Background Protection", NotificationManager.IMPORTANCE_MIN)
            channel.setShowBadge(false)
            
            val warningChannel = NotificationChannel(WARNING_CHANNEL_ID, "Unverified Threats", NotificationManager.IMPORTANCE_HIGH)
            warningChannel.setSound(null, null)
            warningChannel.enableVibration(true)

            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
            manager?.createNotificationChannel(warningChannel)
        }
    }

    private fun createSilentNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pi = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("SafeGuard AI Active")
                .setContentText("Continuous background protection is running.")
                .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
                .setContentIntent(pi)
                .setPriority(NotificationCompat.PRIORITY_MIN)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .setOngoing(true)
                .build()
    }
}
