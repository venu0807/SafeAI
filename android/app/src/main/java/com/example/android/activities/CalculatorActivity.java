package com.example.android.activities;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.example.android.R;
import com.example.android.utils.SharedPrefsHelper;

public class CalculatorActivity extends AppCompatActivity {

    private TextView display;
    private StringBuilder input = new StringBuilder();
    private SharedPrefsHelper prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_calculator);

        prefs = new SharedPrefsHelper(this);
        display = findViewById(R.id.tv_calc_display);

        setupButtons();
    }

    private void setupButtons() {
        int[] numberIds = {
                R.id.btn_0, R.id.btn_1, R.id.btn_2, R.id.btn_3,
                R.id.btn_4, R.id.btn_5, R.id.btn_6, R.id.btn_7,
                R.id.btn_8, R.id.btn_9
        };

        for (int id : numberIds) {
            findViewById(id).setOnClickListener(v -> {
                Button b = (Button) v;
                input.append(b.getText().toString());
                display.setText(input.toString());
            });
        }

        findViewById(R.id.btn_clear).setOnClickListener(v -> {
            input.setLength(0);
            display.setText("0");
        });

        findViewById(R.id.btn_del).setOnClickListener(v -> {
            if (input.length() > 0) {
                input.deleteCharAt(input.length() - 1);
                display.setText(input.length() > 0 ? input.toString() : "0");
            }
        });

        findViewById(R.id.btn_equals).setOnClickListener(v -> onEqualsPressed());

        // Dummy operators
        int[] opIds = { R.id.btn_add, R.id.btn_sub, R.id.btn_mul, R.id.btn_div, R.id.btn_dot };
        for (int id : opIds) {
            findViewById(id).setOnClickListener(v -> {
                Button b = (Button) v;
                input.append(b.getText().toString());
                display.setText(input.toString());
            });
        }
    }

    private void onEqualsPressed() {
        String pin = prefs.getDuressPin();
        if (pin == null || pin.isEmpty())
            pin = "9911"; // Default backdoor

        if (input.toString().equals(pin)) {
            // UNLOCKED: Launch real app
            input.setLength(0);
            display.setText("0");

            Intent intent = new Intent(this, MainActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            if (!isFinishing()) {
                startActivity(intent);
                finish();
            }
        } else {
            // Fake calculation response or "Syntax Error" to play it off
            display.setText("Error");
            input.setLength(0);
        }
    }
}
