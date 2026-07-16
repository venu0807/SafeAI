package com.example.android.activities

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay
import com.example.android.services.EmergencyResponseService
import com.example.android.services.ThreatDetectionService
import com.example.android.ui.theme.SafeGuardTheme
import com.example.android.utils.PermissionHelper
import com.example.android.utils.SharedPrefsHelper

class MainActivity : ComponentActivity() {

    private lateinit var prefsHelper: SharedPrefsHelper
    private var threatReceiver: BroadcastReceiver? = null

    // State for Compose
    private var _isProtectionEnabled = mutableStateOf(false)
    private var _detectionCount = mutableStateOf(0)
    private var _threatCount = mutableStateOf(0)

    private val REQ_BG_LOCATION = 101

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.entries.all { it.value }
        if (allGranted) {
            checkBackgroundLocationRequirement()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefsHelper = SharedPrefsHelper(this)

        updateStateFromPrefs()
        registerThreatReceiver()

        setContent {
            SafeGuardTheme {
                // Check permissions asynchronously after first frame renders
                // so the user sees the UI immediately instead of waiting through dialogs
                LaunchedEffect(Unit) {
                    checkAndRequestFullPermissions()
                }

                DashboardScreen(
                    isProtectionEnabled = _isProtectionEnabled.value,
                    detectionCount = _detectionCount.value,
                    threatCount = _threatCount.value,
                    onToggleProtection = { enabled ->
                        if (enabled) startProtection() else stopProtection()
                    },
                    onPanicClick = { triggerManualEmergency() },
                    onNavigateToContacts = { startActivity(Intent(this, EmergencyContactsActivity::class.java)) },
                    onNavigateToHistory = { startActivity(Intent(this, IncidentHistoryActivity::class.java)) },
                    onNavigateToSettings = { startActivity(Intent(this, SettingsActivity::class.java)) }
                )
            }
        }
    }

    private fun checkAndRequestFullPermissions() {
        if (!PermissionHelper.hasAllPermissions(this)) {
            val permissions = arrayOf(
                Manifest.permission.RECORD_AUDIO,
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.CALL_PHONE,
                Manifest.permission.SEND_SMS,
                Manifest.permission.READ_CONTACTS,
                Manifest.permission.CAMERA
            )
            val needed = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                permissions + Manifest.permission.POST_NOTIFICATIONS
            } else {
                permissions
            }
            permissionLauncher.launch(needed)
        } else {
            checkBackgroundLocationRequirement()
        }
    }

    private fun checkBackgroundLocationRequirement() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.ACCESS_BACKGROUND_LOCATION
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                AlertDialog.Builder(this)
                    .setTitle("Crucial Permission Required")
                    .setMessage("To ensure the Panic Button works when your screen is locked, please select 'Allow all the time' in the next location settings screen.")
                    .setPositiveButton("Set to Always Allow") { _, _ ->
                        ActivityCompat.requestPermissions(
                            this,
                            arrayOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION),
                            REQ_BG_LOCATION
                        )
                    }
                    .setNegativeButton("Later") { _, _ -> checkBatteryOptimizationRequirement() }
                    .show()
            } else {
                checkBatteryOptimizationRequirement()
            }
        } else {
            checkBatteryOptimizationRequirement()
        }
    }

    private fun checkBatteryOptimizationRequirement() {
        if (!PermissionHelper.isIgnoringBatteryOptimizations(this)) {
            AlertDialog.Builder(this)
                .setTitle("Disable Battery Optimization")
                .setMessage("To ensure SafeGuard AI works perfectly in real life with the screen off, you MUST disable battery optimizations. Please select 'Unrestricted' or 'No Restrictions' on the next screen.")
                .setPositiveButton("Settings") { _, _ ->
                    PermissionHelper.showBatteryOptimizationDialog(this)
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }

    private fun startProtection() {
        if (!PermissionHelper.hasAllPermissions(this)) {
            _isProtectionEnabled.value = false
            return
        }
        try {
            val intent = Intent(this, ThreatDetectionService::class.java)
            ContextCompat.startForegroundService(this, intent)
            prefsHelper.setProtectionEnabled(true)
            updateStateFromPrefs()
        } catch (e: Exception) {
            Log.e("MainActivity", "Start fail", e)
        }
    }

    private fun stopProtection() {
        stopService(Intent(this, ThreatDetectionService::class.java))
        prefsHelper.setProtectionEnabled(false)
        updateStateFromPrefs()
    }

    private fun triggerManualEmergency() {
        AlertDialog.Builder(this)
            .setTitle("🚨 Confirm Emergency")
            .setMessage(
                "This will immediately:\n" +
                "• Send SOS alerts to your emergency contacts via SMS\n" +
                "• Activate the camera flash as a visual deterrent\n" +
                "• Send your location to Firebase\n" +
                "• Trigger an automatic emergency call\n\n" +
                "Are you sure you want to proceed?"
            )
            .setPositiveButton("Send SOS") { _, _ ->
                Toast.makeText(this, "🚨 EMERGENCY PROTOCOL INITIATED", Toast.LENGTH_SHORT).show()
                try {
                    val intent = Intent(this, EmergencyResponseService::class.java).apply {
                        putExtra("confidence", 1.0f)
                        putExtra("call_police", false)
                    }
                    ContextCompat.startForegroundService(this, intent)
                } catch (e: Exception) {
                    Log.e("MainActivity", "Panic trigger failed", e)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun registerThreatReceiver() {
        threatReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if ("THREAT_DETECTED" == intent.action) {
                    updateStateFromPrefs()
                }
            }
        }
        val filter = IntentFilter("THREAT_DETECTED")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(threatReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(threatReceiver, filter)
        }
    }

    private fun updateStateFromPrefs() {
        _isProtectionEnabled.value = prefsHelper.isProtectionEnabled
        _detectionCount.value = prefsHelper.detectionCount
        _threatCount.value = prefsHelper.threatCount
    }

    override fun onResume() {
        super.onResume()
        updateStateFromPrefs()
    }

    override fun onDestroy() {
        super.onDestroy()
        threatReceiver?.let { unregisterReceiver(it) }
    }
}

// --- Compose UI ---

@Composable
fun DashboardScreen(
    isProtectionEnabled: Boolean,
    detectionCount: Int,
    threatCount: Int,
    onToggleProtection: (Boolean) -> Unit,
    onPanicClick: () -> Unit,
    onNavigateToContacts: () -> Unit,
    onNavigateToHistory: () -> Unit,
    onNavigateToSettings: () -> Unit
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onPanicClick,
                containerColor = MaterialTheme.colorScheme.error,
                contentColor = MaterialTheme.colorScheme.onError,
                modifier = Modifier.padding(16.dp)
            ) {                    Text(text = "PANIC BUTTON", fontWeight = FontWeight.Bold)
            }
        }
    ) { paddingValues ->
        // Phase 1: Always-rendered core — title, indicator, status, protection toggle.
        // Phase 2: Deferred by one frame — metrics, nav buttons.
        // This splits the ~950ms PerformTraversals work across two frames,
        // keeping the first frame under the 16ms budget and avoiding jank.
        var showBottomSection by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) {
            delay(16) // Wait ~1 frame for the core UI to settle
            showBottomSection = true
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(paddingValues)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "SafeGuard AI",
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(32.dp))

            // Status Indicator (Pulse)
            PulseIndicator(isActive = isProtectionEnabled)
            
            Spacer(modifier = Modifier.height(32.dp))

            Text(
                text = if (isProtectionEnabled) "Shield Active" else "Shield Dormant",
                style = MaterialTheme.typography.titleLarge,
                color = if (isProtectionEnabled) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onBackground
            )
            
            Spacer(modifier = Modifier.height(16.dp))

            Switch(
                checked = isProtectionEnabled,
                onCheckedChange = onToggleProtection,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = MaterialTheme.colorScheme.primary,
                    checkedTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                )
            )

            // --- Deferred bottom section (composed after first frame) ---
            if (showBottomSection) {
                Spacer(modifier = Modifier.height(48.dp))

                // Metrics Grid
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    MetricCard("Events Monitored", detectionCount.toString())
                    MetricCard("Threats Blocked", threatCount.toString(), isCritical = threatCount > 0)
                }

                Spacer(modifier = Modifier.height(48.dp))

                // Navigation Actions
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    MenuButton("Emergency Contacts", onNavigateToContacts)
                    MenuButton("Incident Vault", onNavigateToHistory)
                    MenuButton("Settings", onNavigateToSettings)
                }
            }
        }
    }
}

@Composable
fun PulseIndicator(isActive: Boolean) {
    val primaryColor = MaterialTheme.colorScheme.primary

    if (isActive) {
        val infiniteTransition = rememberInfiniteTransition()
        val animProgress by infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(1500, easing = LinearOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            )
        )

        // Canvas drawing avoids composable nesting and clip() overhead.
        // Animation values are read during the draw phase, not composition phase,
        // so the composable tree doesn't need to recompose on every frame.
        Canvas(modifier = Modifier.size(120.dp)) {
            val radius = size.minDimension / 2f
            // Outer pulse ring — expanding circle that fades out
            val pulseRadius = radius * (1f + animProgress * 0.5f)
            val pulseAlpha = 1f - animProgress
            drawCircle(
                color = primaryColor.copy(alpha = pulseAlpha),
                radius = pulseRadius
            )
            // Inner solid core
            drawCircle(
                color = primaryColor,
                radius = radius
            )
        }
    } else {
        Canvas(modifier = Modifier.size(120.dp)) {
            drawCircle(
                color = Color.DarkGray,
                radius = size.minDimension / 2f
            )
        }
    }
}

@Composable
fun MetricCard(title: String, value: String, isCritical: Boolean = false) {
    Card(
        modifier = Modifier.width(140.dp).height(100.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = value,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = if (isCritical) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
        }
    }
}

@Composable
fun MenuButton(text: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(56.dp),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface
        )
    ) {
        Text(text = text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
    }
}
