package com.example.minesweeper;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.UserProfileChangeRequest;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QuerySnapshot;

import java.util.HashMap;
import java.util.Map;

public class RegisterActivity extends AppCompatActivity {

    private FirebaseAuth auth;
    private TextInputEditText etEmail, etPassword, etUsername;
    private ProgressBar progressBar;
    private FirebaseFirestore db;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        // Initialize Firebase instances
        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        // Bind Views
        etEmail = findViewById(R.id.etEmail);
        etPassword = findViewById(R.id.etPassword);
        etUsername = findViewById(R.id.etUsername);
        progressBar = findViewById(R.id.progressBar);
        Button btnRegister = findViewById(R.id.btnRegister);
        ImageButton backBtn = findViewById(R.id.backBtn);

        // Listeners
        backBtn.setOnClickListener(v -> finish());
        btnRegister.setOnClickListener(v -> checkUsernameAndRegister());
    }

    private void checkUsernameAndRegister() {
        String username = etUsername.getText().toString().trim();

        if (username.isEmpty()) {
            Toast.makeText(this, "מלא שם משתמש", Toast.LENGTH_SHORT).show();
            return;
        }

        progressBar.setVisibility(View.VISIBLE);

        // Check if username exists in Firestore
        // NOTE: This requires Firestore rules to allow unauthenticated reads for this collection!
        db.collection("users")
                .whereEqualTo("username", username)
                .get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        QuerySnapshot snapshot = task.getResult();
                        if (snapshot != null && !snapshot.isEmpty()) {
                            progressBar.setVisibility(View.GONE);
                            Toast.makeText(this, "שם המשתמש כבר קיים", Toast.LENGTH_SHORT).show();
                        } else {
                            // Username is free, proceed to register
                            registerUser();
                        }
                    } else {
                        progressBar.setVisibility(View.GONE);
                        // If the check fails (e.g., permission denied), we warn the user
                        Toast.makeText(this, "שגיאה בבדיקת שם משתמש - נסה שוב", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void registerUser() {
        String email = etEmail.getText().toString().trim();
        String pass = etPassword.getText().toString().trim();
        String username = etUsername.getText().toString().trim();

        if (email.isEmpty() || pass.isEmpty()) {
            progressBar.setVisibility(View.GONE);
            Toast.makeText(this, "מלא את כל השדות", Toast.LENGTH_SHORT).show();
            return;
        }

        if (pass.length() < 6) {
            progressBar.setVisibility(View.GONE);
            Toast.makeText(this, "סיסמה חייבת להיות לפחות 6 תווים", Toast.LENGTH_SHORT).show();
            return;
        }

        // 1. Create User in Auth
        auth.createUserWithEmailAndPassword(email, pass)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        FirebaseUser user = auth.getCurrentUser();
                        if (user != null) {
                            // 2. CRITICAL FIX: Update the Auth Profile with the Username immediately
                            UserProfileChangeRequest profileUpdates = new UserProfileChangeRequest.Builder()
                                    .setDisplayName(username)
                                    .build();

                            user.updateProfile(profileUpdates)
                                    .addOnCompleteListener(profileTask -> {
                                        if (profileTask.isSuccessful()) {
                                            // 3. Save to Firestore only after profile is updated
                                            saveUserToFirestore(user.getUid(), email, username);
                                        } else {
                                            progressBar.setVisibility(View.GONE);
                                            Toast.makeText(this, "Failed to set username", Toast.LENGTH_SHORT).show();
                                        }
                                    });
                        }
                    } else {
                        progressBar.setVisibility(View.GONE);
                        String error = task.getException() != null ? task.getException().getMessage() : "Error";
                        Toast.makeText(this, "שגיאה: " + error, Toast.LENGTH_LONG).show();
                    }
                });
    }

    private void saveUserToFirestore(String userId, String email, String username) {
        Map<String, Object> userMap = new HashMap<>();
        userMap.put("email", email);
        userMap.put("username", username);
        // You can add more fields here (e.g., score: 0)
        userMap.put("score", 0);

        db.collection("users").document(userId).set(userMap)
                .addOnSuccessListener(aVoid -> {
                    progressBar.setVisibility(View.GONE);
                    Toast.makeText(this, "נרשמת בהצלחה!", Toast.LENGTH_LONG).show();

                    // 4. Navigate to Home
                    Intent intent = new Intent(RegisterActivity.this, MainActivity.class);
                    // Pass the username manually just in case
                    intent.putExtra("USERNAME", username);
                    // Clear back stack so they can't go back to register
                    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(intent);
                    finish();
                })
                .addOnFailureListener(e -> {
                    progressBar.setVisibility(View.GONE);
                    // If this Toast appears, you have a Firestore Permissions issue
                    Toast.makeText(this, "Error Saving DB: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
    }
}