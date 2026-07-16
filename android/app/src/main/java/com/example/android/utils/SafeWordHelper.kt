package com.example.android.utils

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Listens for spoken trigger words and safe words during the unverified SOS buffer.
 *
 * - **Trigger words** (e.g., "help", "save", "emergency") → immediately dispatch the SOS.
 * - **Safe word** (user-customizable, e.g., "pineapple" or "I'm safe") → cancel the SOS buffer.
 */
class SafeWordHelper(
    private val context: Context,
    private val prefsHelper: SharedPrefsHelper,
    private val onTriggerWord: () -> Unit,
    private val onSafeWord: () -> Unit
) {

    companion object {
        private const val TAG = "SafeWordHelper"
        // Hardcoded trigger keywords that dispatch the SOS immediately
        private val TRIGGER_WORDS = listOf("help", "save", "emergency")
    }

    private var speechRecognizer: SpeechRecognizer? = null
    private var recognizerIntent: Intent? = null
    private var isListening = false
    private var isDestroyed = false
    private var retryCount = 0

    private val scope = CoroutineScope(Dispatchers.Main + Job())

    init {
        initializeRecognizer()
    }

    private fun initializeRecognizer() {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            Log.e(TAG, "Speech recognition is not available on this device.")
            return
        }

        scope.launch {
            try {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                    if (SpeechRecognizer.isOnDeviceRecognitionAvailable(context)) {
                        speechRecognizer = SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
                        Log.i(TAG, "Using strictly ON-DEVICE Speech Recognizer (Maximum Privacy & Offline)")
                    } else {
                        Log.w(TAG, "On-Device Speech Recognition is NOT available. Falling back to Cloud (Warning: Requires Network!)")
                        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
                    }
                } else {
                    speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
                }

                recognizerIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-US")
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                    putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
                }

                speechRecognizer?.setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {
                        Log.d(TAG, "Ready for speech input...")
                    }

                    override fun onBeginningOfSpeech() {}
                    override fun onRmsChanged(rmsdB: Float) {}
                    override fun onBufferReceived(buffer: ByteArray?) {}
                    override fun onEndOfSpeech() {}

                    override fun onError(error: Int) {
                        if (error == SpeechRecognizer.ERROR_NETWORK || error == SpeechRecognizer.ERROR_NETWORK_TIMEOUT) {
                            Log.e(TAG, "CRITICAL: SpeechRecognizer network error! Requires offline language pack if no internet!")
                            retryCount++
                            if (retryCount >= 3) {
                                Log.e(TAG, "SpeechRecognizer failed 3 times. Failsafe: triggering SOS immediately.")
                                onTriggerWord()
                                stopListening()
                                return
                            }
                        } else {
                            retryCount = 0
                        }

                        if (!isDestroyed && isListening) {
                            restartListening()
                        }
                    }

                    override fun onResults(results: Bundle?) {
                        retryCount = 0
                        processResults(results)
                        if (!isDestroyed && isListening) {
                            restartListening()
                        }
                    }

                    override fun onPartialResults(partialResults: Bundle?) {
                        processResults(partialResults)
                    }

                    override fun onEvent(eventType: Int, params: Bundle?) {}
                })
            } catch (e: Exception) {
                Log.e(TAG, "Failed to initialize SpeechRecognizer", e)
            }
        }
    }

    /**
     * Checks the recognized text against:
     * 1. The user's custom safe word → cancels the SOS
     * 2. Hardcoded trigger words ("help", "save", "emergency") → dispatches the SOS
     */
    private fun processResults(results: Bundle?) {
        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
        if (!matches.isNullOrEmpty()) {
            val customSafeWord = prefsHelper.safeWord.lowercase().trim()

            for (match in matches) {
                val spokenText = match.lowercase().trim()

                // 1. Check if the user spoke their custom safe word → CANCEL
                if (customSafeWord.isNotEmpty() && spokenText.contains(customSafeWord)) {
                    Log.w(TAG, "SAFE WORD DETECTED: \"$spokenText\" → CANCELLING SOS")
                    onSafeWord()
                    return
                }

                // 2. Check if the user spoke a trigger word → DISPATCH SOS
                for (trigger in TRIGGER_WORDS) {
                    if (spokenText.contains(trigger)) {
                        Log.w(TAG, "TRIGGER WORD DETECTED: \"$spokenText\" → DISPATCHING SOS IMMEDIATELY")
                        onTriggerWord()
                        return
                    }
                }
            }
        }
    }

    fun startListening() {
        if (speechRecognizer == null || isListening || isDestroyed) return
        isListening = true

        scope.launch {
            try {
                speechRecognizer?.startListening(recognizerIntent)
                Log.d(TAG, "Started listening for trigger/safe words...")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start listening", e)
                isListening = false
            }
        }
    }

    fun stopListening() {
        if (speechRecognizer == null || !isListening) return
        isListening = false

        scope.launch {
            try {
                speechRecognizer?.stopListening()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to stop listening", e)
            }
        }
    }

    private fun restartListening() {
        scope.launch {
            delay(500)
            if (isListening && !isDestroyed && speechRecognizer != null) {
                try {
                    speechRecognizer?.cancel()
                    speechRecognizer?.startListening(recognizerIntent)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to restart listening", e)
                }
            }
        }
    }

    fun destroy() {
        isDestroyed = true
        isListening = false

        scope.launch {
            if (speechRecognizer != null) {
                try {
                    speechRecognizer?.cancel()
                    speechRecognizer?.destroy()
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to destroy SpeechRecognizer", e)
                }
                speechRecognizer = null
            }
            scope.cancel()
        }
    }
}
