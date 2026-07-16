package com.example.android.activities;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.appcompat.app.AppCompatActivity;

import com.example.android.R;
import com.example.android.utils.SharedPrefsHelper;
import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

public class SplashActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            if (isFinishing()) return;

            SharedPrefsHelper prefsHelper = new SharedPrefsHelper(this);
            FirebaseUser user = null;

            try {
                if (!FirebaseApp.getApps(this).isEmpty()) {
                    user = FirebaseAuth.getInstance().getCurrentUser();
                }
            } catch (Exception e) {
                Log.w("Splash", "Firebase Auth not available. Checking local session.");
            }

            if (user == null && prefsHelper.getUserId().isEmpty()) {
                startActivity(new Intent(SplashActivity.this, LoginActivity.class));
            } else if (prefsHelper.getUserName().isEmpty()) {
                startActivity(new Intent(SplashActivity.this, OnboardingActivity.class));
            } else {
                startActivity(new Intent(SplashActivity.this, MainActivity.class));
            }
            if (!isFinishing()) finish();
        }, 2000);
    }
}
