package com.example.android.activities;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.example.android.R;
import com.example.android.utils.SharedPrefsHelper;
import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

public class ProfileActivity extends AppCompatActivity {

    private SharedPrefsHelper prefsHelper;
    private TextView tvName, tvEmail, tvAddress, tvUid;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile);

        prefsHelper = new SharedPrefsHelper(this);
        setupToolbar();
        initializeViews();
        loadProfileData();
    }

    private void setupToolbar() {
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        toolbar.setNavigationOnClickListener(v -> {
            if (!isFinishing()) finish();
        });
    }

    private void initializeViews() {
        tvName = findViewById(R.id.profile_name);
        tvEmail = findViewById(R.id.profile_email);
        tvAddress = findViewById(R.id.profile_address);
        tvUid = findViewById(R.id.profile_uid);

        findViewById(R.id.btn_logout).setOnClickListener(v -> logout());
    }

    private void loadProfileData() {
        FirebaseUser user = null;
        boolean isFirebaseReady = false;

        try {
            if (!FirebaseApp.getApps(this).isEmpty()) {
                user = FirebaseAuth.getInstance().getCurrentUser();
                isFirebaseReady = true;
            }
        } catch (Exception e) {
            Log.w("Profile", "Firebase Auth initialization failed or not configured.");
        }

        if (isFirebaseReady && user != null) {
            tvName.setText(user.getDisplayName());
            tvEmail.setText(user.getEmail());
            tvUid.setText("UID: " + user.getUid());
            
            try {
                FirebaseDatabase.getInstance().getReference("users").child(user.getUid()).child("address")
                        .addListenerForSingleValueEvent(new ValueEventListener() {
                            @Override
                            public void onDataChange(DataSnapshot snapshot) {
                                if (snapshot.exists()) {
                                    tvAddress.setText(snapshot.getValue(String.class));
                                } else {
                                    tvAddress.setText("Not registered");
                                }
                            }
                            @Override public void onCancelled(DatabaseError error) {
                                Log.w("Profile", "Firebase address fetch cancelled: " + error.getMessage());
                                tvAddress.setText("Unavailable (DB error)");
                            }
                        });
            } catch (Exception e) {
                tvAddress.setText("Offline (Local Mode)");
            }
        } else {
            tvName.setText(prefsHelper.getUserName().isEmpty() ? "Demo User" : prefsHelper.getUserName());
            tvEmail.setText("Simulation Mode (No Google Config)");
            tvUid.setText("UID: " + (prefsHelper.getUserId().isEmpty() ? "DEV_LOCAL_UID" : prefsHelper.getUserId()));
            tvAddress.setText("Stored Locally on Device");
        }
    }

    private void logout() {
        try {
            if (!FirebaseApp.getApps(this).isEmpty()) {
                FirebaseAuth.getInstance().signOut();
            }
        } catch (Exception ignored) {}
        
        prefsHelper.clearAll();
        Toast.makeText(this, "Logged out successfully", Toast.LENGTH_SHORT).show();
        Intent intent = new Intent(this, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        if (!isFinishing()) {
            startActivity(intent);
            finish();
        }
    }
}
