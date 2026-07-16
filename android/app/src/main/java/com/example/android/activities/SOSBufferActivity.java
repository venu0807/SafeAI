package com.example.android.activities;

import android.content.Intent;
import android.media.MediaPlayer;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.util.Log;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.example.android.R;
import com.example.android.services.EmergencyResponseService;

public class SOSBufferActivity extends AppCompatActivity {

    private static final String TAG = "SOSBufferActivity";
    private TextView tvCountdown;
    private CountDownTimer timer;
    private MediaPlayer mediaPlayer;
    private boolean isCancelled = false;

    // Carry over extras from original intent
    private float threatConfidence = 1.0f;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Required to wake up screen and show over lockscreen for emergencies
        Window window = getWindow();
        window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
                | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        setContentView(R.layout.activity_sos_buffer);

        if (getIntent() != null) {
            threatConfidence = getIntent().getFloatExtra("confidence", 1.0f);
        }

        tvCountdown = findViewById(R.id.tv_buffer_countdown);
        Button btnCancel = findViewById(R.id.btn_buffer_cancel);

        btnCancel.setOnClickListener(v -> cancelEmergency());

        playAlarmSound();
        startCountdown();
    }

    private void playAlarmSound() {
        try {
            Uri alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
            if (alarmUri == null)
                alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
            mediaPlayer = MediaPlayer.create(this, alarmUri);
            if (mediaPlayer != null) {
                mediaPlayer.setLooping(true);
                mediaPlayer.start();
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to play alarm", e);
        }
    }

    private void startCountdown() {
        timer = new CountDownTimer(10000, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                int secondsLeft = (int) (millisUntilFinished / 1000) + 1;
                tvCountdown.setText(String.valueOf(secondsLeft));
            }

            @Override
            public void onFinish() {
                if (!isCancelled)
                    dispatchSOS();
            }
        }.start();
    }

    private void cancelEmergency() {
        isCancelled = true;
        if (timer != null)
            timer.cancel();
        if (mediaPlayer != null && mediaPlayer.isPlaying()) {
            mediaPlayer.stop();
            mediaPlayer.release();
            mediaPlayer = null;
        }
        if (!isFinishing()) finish();
    }

    private void dispatchSOS() {
        if (mediaPlayer != null && mediaPlayer.isPlaying()) {
            mediaPlayer.stop();
            mediaPlayer.release();
            mediaPlayer = null;
        }

        Intent serviceIntent = new Intent(this, EmergencyResponseService.class);
        serviceIntent.putExtra("confidence", threatConfidence);

        try {
            startForegroundService(serviceIntent);
        } catch (Exception e) {
            Log.e(TAG, "Failed to launch service", e);
        }

        if (!isFinishing()) finish();
    }

    @Override
    public void onBackPressed() {
        // Prevent backing out of the alarm. Must explicitly press cancel.
        // super.onBackPressed();
    }
}
