package com.example.minesweeper;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.UserProfileChangeRequest;

import java.util.HashMap;
import java.util.Map;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

public class RegisterActivity extends AppCompatActivity {

    private FirebaseAuth auth;
    private TextInputEditText etEmail, etPassword, etUsername;
    private ProgressBar progressBar;
    private DatabaseReference usersRef;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        // Initialize Firebase instances
        auth = FirebaseAuth.getInstance();
        usersRef = FirebaseDatabase.getInstance().getReference("users");

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

        usersRef.orderByChild("username")
                .equalTo(username)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(DataSnapshot snapshot) {

                        progressBar.setVisibility(View.GONE);

                        if (snapshot.exists()) {
                            Toast.makeText(RegisterActivity.this,
                                    "שם המשתמש כבר קיים",
                                    Toast.LENGTH_SHORT).show();
                        } else {
                            registerUser();
                        }
                    }

                    @Override
                    public void onCancelled(DatabaseError error) {
                        progressBar.setVisibility(View.GONE);
                        Toast.makeText(RegisterActivity.this,
                                "שגיאה בבדיקת שם משתמש",
                                Toast.LENGTH_SHORT).show();
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
                                            // 3. Save to DB only after profile is updated
                                            saveUserToRealtimeDB(user.getUid(), email, username);
                                            Toast.makeText(this, "BBB", Toast.LENGTH_SHORT).show();
                                        } else {
                                            progressBar.setVisibility(View.GONE);
                                            Toast.makeText(this, "Failed to set username", Toast.LENGTH_SHORT).show();
                                        }
                                    });
                            Toast.makeText(this, "AAAA", Toast.LENGTH_SHORT).show();
                        }
                        else {
                                progressBar.setVisibility(View.GONE);
                                Toast.makeText(this, "User is null!", Toast.LENGTH_LONG).show();
                        }
                    } else {
                        progressBar.setVisibility(View.GONE);
                        String error = task.getException() != null ? task.getException().getMessage() : "Error";
                        Toast.makeText(this, "שגיאה: " + error, Toast.LENGTH_LONG).show();
                    }
                });
    }

    private void saveUserToRealtimeDB(String userId, String email, String username) {
        Map<String, Object> userMap = new HashMap<>();
        userMap.put("email", email);
        userMap.put("username", username);
        // You can add more fields here (e.g., score: 0)
        userMap.put("score", 0);

        usersRef.child(userId).setValue(userMap)
                .addOnSuccessListener(new OnSuccessListener<Void>() {
                    @Override
                    public void onSuccess(Void aVoid) {
                        progressBar.setVisibility(View.GONE);
                        Toast.makeText(RegisterActivity.this, "נרשמת בהצלחה!", Toast.LENGTH_LONG).show();

                        Intent intent = new Intent(RegisterActivity.this, MainActivity.class);
                        intent.putExtra("USERNAME", username);
                        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP |
                                Intent.FLAG_ACTIVITY_NEW_TASK |
                                Intent.FLAG_ACTIVITY_CLEAR_TASK);
                        RegisterActivity.this.startActivity(intent);
                        RegisterActivity.this.finish();
                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        progressBar.setVisibility(View.GONE);
                        Toast.makeText(RegisterActivity.this, "Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    }
                });
        Toast.makeText(this, "CCC", Toast.LENGTH_SHORT).show();
    }
}