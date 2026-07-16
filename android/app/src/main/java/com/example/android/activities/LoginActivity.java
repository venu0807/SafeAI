package com.example.android.activities;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.example.android.R;
import com.example.android.utils.SharedPrefsHelper;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.tasks.Task;
import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GoogleAuthProvider;

public class LoginActivity extends AppCompatActivity {

    private FirebaseAuth mAuth;
    private GoogleSignInClient mGoogleSignInClient;
    private SharedPrefsHelper prefsHelper;
    private com.google.android.material.button.MaterialButton btnGoogleLogin;
    private android.widget.Button btnSkipLogin;

    private final ActivityResultLauncher<Intent> googleSignInLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                setLoadingState(false);
                Intent data = result.getData();
                Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(data);
                try {
                    GoogleSignInAccount account = task.getResult(ApiException.class);
                    if (account != null) {
                        if (account.getIdToken() != null && !FirebaseApp.getApps(this).isEmpty()) {
                            firebaseAuthWithGoogle(account.getIdToken());
                        } else {
                            // Fallback if Firebase isn't correctly configured
                            prefsHelper.setUserId(
                                    account.getId() != null ? account.getId() : "google_" + System.currentTimeMillis());
                            prefsHelper.setUserName(
                                    account.getDisplayName() != null ? account.getDisplayName() : "Google User");
                            Toast.makeText(this, "Signed in as " + prefsHelper.getUserName(), Toast.LENGTH_SHORT)
                                    .show();
                            startActivity(new Intent(LoginActivity.this, MainActivity.class));
                            finish();
                        }
                    }
                } catch (ApiException e) {
                    Log.w("Login", "Google sign in failed code: " + e.getStatusCode());
                    if (e.getStatusCode() == 10) {
                        Toast.makeText(this,
                                "Google Sign-In failed (Error 10). Register this app's SHA-1 fingerprint in Firebase Console under Project Settings > General > Your apps > Android.",
                                Toast.LENGTH_LONG).show();
                    } else if (e.getStatusCode() != 12501) { // 12501 is user canceled
                        Toast.makeText(this, "Sign-in failed. Error Code: " + e.getStatusCode(), Toast.LENGTH_LONG)
                                .show();
                    }
                }
            });

    private void setLoadingState(boolean isLoading) {
        if (btnGoogleLogin == null) return;
        btnGoogleLogin.setEnabled(!isLoading);
        btnGoogleLogin.setText(isLoading ? "Signing in..." : "Sign in with Google");
        if (btnSkipLogin != null) {
            btnSkipLogin.setEnabled(!isLoading);
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        prefsHelper = new SharedPrefsHelper(this);

        try {
            if (!FirebaseApp.getApps(this).isEmpty()) {
                mAuth = FirebaseAuth.getInstance();
            }
        } catch (Exception e) {
            Log.w("Login", "Firebase Auth initialization skipped.");
        }

        GoogleSignInOptions.Builder gsoBuilder = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestEmail()
                .requestProfile();

        try {
            int stringId = getResources().getIdentifier("default_web_client_id", "string", getPackageName());
            if (stringId != 0) {
                gsoBuilder.requestIdToken(getString(stringId));
            }
        } catch (Exception ignored) {
        }

        mGoogleSignInClient = GoogleSignIn.getClient(this, gsoBuilder.build());

        btnGoogleLogin = findViewById(R.id.btn_google_login);
        btnGoogleLogin.setOnClickListener(v -> signIn());

        // GUEST MODE LOGIN
        btnSkipLogin = findViewById(R.id.btn_skip_login);
        btnSkipLogin.setOnClickListener(v -> {
            if (!btnSkipLogin.isEnabled() || isFinishing()) return;
            prefsHelper.setUserId("guest_" + System.currentTimeMillis());
            prefsHelper.setUserName("Guest User");
            Toast.makeText(this, "Continuing as Guest", Toast.LENGTH_SHORT).show();
            startActivity(new Intent(LoginActivity.this, MainActivity.class));
            finish();
        });
    }

    private void signIn() {
        setLoadingState(true);
        Intent signInIntent = mGoogleSignInClient.getSignInIntent();
        googleSignInLauncher.launch(signInIntent);
    }

    private void firebaseAuthWithGoogle(String idToken) {
        if (mAuth == null) {
            proceedToNextStep();
            return;
        }

        AuthCredential credential = GoogleAuthProvider.getCredential(idToken, null);
        mAuth.signInWithCredential(credential)
                .addOnCompleteListener(this, task -> proceedToNextStep());
    }

    private void proceedToNextStep() {
        if (isFinishing()) return;

        if (mAuth != null) {
            FirebaseUser user = mAuth.getCurrentUser();
            if (user != null) {
                prefsHelper.setUserId(user.getUid());
                if (user.getDisplayName() != null && !user.getDisplayName().isEmpty()) {
                    prefsHelper.setUserName(user.getDisplayName());
                }
            }
        }

        if (prefsHelper.getUserId().isEmpty()) {
            prefsHelper.setUserId("user_" + System.currentTimeMillis());
        }

        Toast.makeText(this, "Signed in successfully!", Toast.LENGTH_SHORT).show();
        startActivity(new Intent(this, MainActivity.class));
        finish();
    }
}
