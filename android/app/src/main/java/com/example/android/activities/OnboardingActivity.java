package com.example.android.activities;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.android.R;
import com.example.android.models.EmergencyContact;
import com.example.android.utils.EmergencyHelper;
import com.example.android.utils.SharedPrefsHelper;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.FirebaseApp;
import com.google.firebase.database.FirebaseDatabase;

import java.util.HashMap;
import java.util.Map;

public class OnboardingActivity extends AppCompatActivity {

    private TextInputEditText etName, etAddress, etContactName, etContactPhone;
    private Button btnFinish;
    private SharedPrefsHelper prefsHelper;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_onboarding);

        prefsHelper = new SharedPrefsHelper(this);
        
        etName = findViewById(R.id.et_onboard_name);
        etAddress = findViewById(R.id.et_onboard_address);
        etContactName = findViewById(R.id.et_contact_name);
        etContactPhone = findViewById(R.id.et_contact_phone);
        btnFinish = findViewById(R.id.btn_finish_onboarding);
        btnFinish.setOnClickListener(v -> saveAndProceed());
    }

    private void saveAndProceed() {
        String name = etName.getText().toString().trim();
        String address = etAddress.getText().toString().trim();
        String cName = etContactName.getText().toString().trim();
        String cPhone = etContactPhone.getText().toString().trim();

        if (name.isEmpty() || address.isEmpty() || cName.isEmpty() || cPhone.isEmpty()) {
            Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show();
            return;
        }

        btnFinish.setEnabled(false);
        btnFinish.setText("Syncing Data...");

        prefsHelper.setUserName(name);
        prefsHelper.setHomeAddress(address);
        EmergencyContact contact = new EmergencyContact(cName, cPhone, "Primary");
        EmergencyHelper.addEmergencyContact(this, contact);

        String uid = prefsHelper.getUserId();
        
        if (!uid.isEmpty() && !FirebaseApp.getApps(this).isEmpty()) {
            Map<String, Object> updates = new HashMap<>();
            updates.put("profile", name);
            updates.put("address", address);
            updates.put("onboarding_complete", true);

            FirebaseDatabase.getInstance().getReference("users").child(uid)
                    .updateChildren(updates)
                    .addOnSuccessListener(aVoid -> {
                        proceedToMain();
                    })
                    .addOnFailureListener(e -> {
                        Toast.makeText(this, "Backup failed, but proceeding locally.", Toast.LENGTH_SHORT).show();
                        proceedToMain();
                    });
        } else {
            proceedToMain();
        }
    }

    private void proceedToMain() {
        if (isFinishing()) return;
        Toast.makeText(this, "Setup Complete!", Toast.LENGTH_LONG).show();
        startActivity(new Intent(this, MainActivity.class));
        finish();
    }
}
