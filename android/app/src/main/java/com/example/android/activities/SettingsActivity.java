package com.example.android.activities;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.slider.Slider;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.example.android.R;
import com.example.android.utils.SharedPrefsHelper;

import java.util.Locale;
import java.util.Stack;

/**
 * Settings Activity
 */
public class SettingsActivity extends AppCompatActivity {

    private SwitchMaterial autoCallSwitch;
    private SwitchMaterial incognitoSwitch;
    private Slider sensitivitySlider;
    private TextView userNameText;
    private EditText duressPinText;
    private TextView versionText;
    private SharedPrefsHelper prefsHelper;
    private final Stack<AlertDialog> activeDialogs = new Stack<>();

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        prefsHelper = new SharedPrefsHelper(this);

        setupToolbar();
        initializeViews();
        loadSettings();
        setupListeners();
    }

    private void setupToolbar() {
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        toolbar.setNavigationOnClickListener(v -> {
            if (!isFinishing()) finish();
        });
    }

    private void initializeViews() {
        autoCallSwitch = findViewById(R.id.switch_auto_call);
        incognitoSwitch = findViewById(R.id.switch_incognito);
        sensitivitySlider = findViewById(R.id.slider_sensitivity);
        userNameText = findViewById(R.id.et_user_name);
        duressPinText = findViewById(R.id.et_duress_pin);
        versionText = findViewById(R.id.text_version);

        try {
            String version = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
            if (versionText != null)
                versionText.setText("Version " + version);
        } catch (Exception e) {
            if (versionText != null)
                versionText.setText("Version 1.0");
        }
    }

    private void loadSettings() {
        if (autoCallSwitch != null)
            autoCallSwitch.setChecked(prefsHelper.isAutoCallEnabled());
        if (incognitoSwitch != null)
            incognitoSwitch.setChecked(prefsHelper.isIncognitoEnabled());
        if (duressPinText != null)
            duressPinText.setText(prefsHelper.getDuressPin());

        if (sensitivitySlider != null) {
                    // Store as float in prefs, display as integer 3-9 (maps to 0.3-0.9)
                    float savedValue = prefsHelper.getSensitivity();
                    int intValue = Math.round(savedValue * 10.0f);
                    if (intValue < 3) intValue = 3;
                    if (intValue > 9) intValue = 9;
                    sensitivitySlider.setValue(intValue);

                    // Label formatter to display float values (0.3-0.9)
                    sensitivitySlider.setLabelFormatter(value ->
                            String.format(Locale.getDefault(), "%.1f", value / 10.0f));
        }

        String userName = prefsHelper.getUserName();
        if (userNameText instanceof EditText) {
            ((EditText) userNameText).setText(userName != null ? userName : "");
        } else if (userNameText != null) {
            userNameText.setText(userName != null && !userName.isEmpty() ? userName : "Not set");
        }
    }

    private void setupListeners() {
        if (autoCallSwitch != null) {
            autoCallSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
                prefsHelper.setAutoCallEnabled(isChecked);
                if (isChecked) {
                    showAutoCallWarning();
                }
                Toast.makeText(this, isChecked ? "Auto-call enabled" : "Auto-call disabled", Toast.LENGTH_SHORT).show();
            });
        }

        if (sensitivitySlider != null) {
            sensitivitySlider.addOnChangeListener((slider, value, fromUser) -> {
                // Convert integer slider value (5-9) back to float (0.5-0.9)
                prefsHelper.setSensitivity(value / 10.0f);
            });
        }

        View btnSave = findViewById(R.id.btn_save_settings);
        if (btnSave != null) {
            btnSave.setOnClickListener(v -> {
                if (userNameText instanceof EditText) {
                    prefsHelper.setUserName(((EditText) userNameText).getText().toString().trim());
                }
                if (duressPinText != null) {
                    prefsHelper.setDuressPin(duressPinText.getText().toString().trim());
                }
                if (incognitoSwitch != null && incognitoSwitch.isChecked() != prefsHelper.isIncognitoEnabled()) {
                    toggleIncognitoMode(incognitoSwitch.isChecked());
                }
                Toast.makeText(this, "Settings saved", Toast.LENGTH_SHORT).show();
                if (!isFinishing()) finish();
            });
        }

        View btnAbout = findViewById(R.id.btn_about);
        if (btnAbout != null) {
            btnAbout.setOnClickListener(v -> showAboutDialog());
        }
    }

    private void showAutoCallWarning() {
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Auto-Call Enabled")
                .setMessage(
                        "Emergency services (112) will be automatically called when a threat is detected. Use this feature responsibly.")
                .setPositiveButton("OK", null)
                .create();
        activeDialogs.push(dialog);
        dialog.show();
    }

    private void showAboutDialog() {
        String aboutText = "SafeGuard AI\n\nMachine learning-powered safety application using CNN and MFCC for real-time distress detection.";
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("About SafeGuard AI")
                .setMessage(aboutText)
                .setPositiveButton("OK", null)
                .create();
        activeDialogs.push(dialog);
        dialog.show();
    }

    @Override
    protected void onDestroy() {
        while (!activeDialogs.isEmpty()) {
            AlertDialog dialog = activeDialogs.pop();
            if (dialog.isShowing()) {
                dialog.dismiss();
            }
        }
        super.onDestroy();
    }

    private void toggleIncognitoMode(boolean enable) {
        prefsHelper.setIncognitoEnabled(enable);
        android.content.pm.PackageManager pm = getPackageManager();

        android.content.ComponentName defaultAlias = new android.content.ComponentName(this,
                "com.example.android.activities.SplashAlias");
        android.content.ComponentName incognitoAlias = new android.content.ComponentName(this,
                "com.example.android.activities.CalculatorAlias");

        if (enable) {
            pm.setComponentEnabledSetting(incognitoAlias,
                    android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                    android.content.pm.PackageManager.DONT_KILL_APP);
            pm.setComponentEnabledSetting(defaultAlias,
                    android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    android.content.pm.PackageManager.DONT_KILL_APP);
            Toast.makeText(this, "Incognito Mode activated! Icon will change shortly.", Toast.LENGTH_LONG).show();
        } else {
            pm.setComponentEnabledSetting(defaultAlias,
                    android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                    android.content.pm.PackageManager.DONT_KILL_APP);
            pm.setComponentEnabledSetting(incognitoAlias,
                    android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    android.content.pm.PackageManager.DONT_KILL_APP);
            Toast.makeText(this, "Incognito Mode disabled.", Toast.LENGTH_SHORT).show();
        }
    }
}
