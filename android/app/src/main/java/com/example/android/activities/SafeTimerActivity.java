package com.example.android.activities;

import android.content.Intent;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.android.R;
import com.example.android.services.EmergencyResponseService;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.IntentFilter;
import android.os.Build;
import java.util.Locale;

public class SafeTimerActivity extends AppCompatActivity {

    private TextView tvCountdownDisplay;
    private MaterialButton btnStart;
    private MaterialButton btnCancel;
    private MaterialButton btnMinus;
    private MaterialButton btnPlus;
    private View llTimerControls;

    private BroadcastReceiver timerUpdateReceiver;
    private boolean timerRunning = false;
    private long timeLeftInMillis = 1800000; // 30 mins default

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_safe_timer);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            toolbar.setNavigationOnClickListener(v -> {
                if (!isFinishing()) finish();
            });
        }

        tvCountdownDisplay = findViewById(R.id.tv_countdown_display);
        btnStart = findViewById(R.id.btn_start_timer);
        btnCancel = findViewById(R.id.btn_cancel_timer);
        btnMinus = findViewById(R.id.btn_minus_10);
        btnPlus = findViewById(R.id.btn_plus_10);
        llTimerControls = findViewById(R.id.ll_timer_controls);

        updateCountDownText();

        btnPlus.setOnClickListener(v -> {
            timeLeftInMillis += 600000; // +10 mins
            updateCountDownText();
        });

        btnMinus.setOnClickListener(v -> {
            if (timeLeftInMillis > 600000) {
                timeLeftInMillis -= 600000; // -10 mins
            } else {
                timeLeftInMillis = 60000; // min 1 minute
            }
            updateCountDownText();
        });

        btnStart.setOnClickListener(v -> startTimerService());
        btnCancel.setOnClickListener(v -> cancelTimerService());

        setupBroadcastReceiver();
    }

    private void setupBroadcastReceiver() {
        timerUpdateReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (intent.getBooleanExtra("IS_CANCELLED", false)) {
                    resetUI();
                } else {
                    timeLeftInMillis = intent
                            .getLongExtra(com.example.android.services.SafeTimerService.EXTRA_TIME_IN_MILLIS, 0);
                    if (timeLeftInMillis > 0) {
                        timerRunning = true;
                        updateUIForRunningTimer();
                        updateCountDownText();
                    } else {
                        tvCountdownDisplay.setText("SOS");
                        resetUI();
                    }
                }
            }
        };
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(timerUpdateReceiver,
                    new IntentFilter(com.example.android.services.SafeTimerService.ACTION_TIMER_UPDATE),
                    Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(timerUpdateReceiver,
                    new IntentFilter(com.example.android.services.SafeTimerService.ACTION_TIMER_UPDATE));
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(timerUpdateReceiver);
    }

    private void startTimerService() {
        Intent serviceIntent = new Intent(this, com.example.android.services.SafeTimerService.class);
        serviceIntent.setAction(com.example.android.services.SafeTimerService.ACTION_START_TIMER);
        serviceIntent.putExtra(com.example.android.services.SafeTimerService.EXTRA_TIME_IN_MILLIS, timeLeftInMillis);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent);
        } else {
            startService(serviceIntent);
        }

        timerRunning = true;
        updateUIForRunningTimer();
        Toast.makeText(this, "Safe Timer Active in Background", Toast.LENGTH_SHORT).show();
    }

    private void cancelTimerService() {
        Intent serviceIntent = new Intent(this, com.example.android.services.SafeTimerService.class);
        serviceIntent.setAction(com.example.android.services.SafeTimerService.ACTION_CANCEL_TIMER);
        startService(serviceIntent);

        resetUI();
        Toast.makeText(this, "Timer Cancelled.", Toast.LENGTH_SHORT).show();
    }

    private void updateUIForRunningTimer() {
        btnStart.setVisibility(View.GONE);
        llTimerControls.setVisibility(View.GONE);
        btnCancel.setVisibility(View.VISIBLE);
    }

    private void resetUI() {
        timerRunning = false;
        btnStart.setVisibility(View.VISIBLE);
        llTimerControls.setVisibility(View.VISIBLE);
        btnCancel.setVisibility(View.GONE);
        timeLeftInMillis = 1800000; // reset to 30 mins
        updateCountDownText();
    }

    private void updateCountDownText() {
        int minutes = (int) (timeLeftInMillis / 1000) / 60;
        int seconds = (int) (timeLeftInMillis / 1000) % 60;
        String timeFormatted = String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds);
        tvCountdownDisplay.setText(timeFormatted);
    }
}
