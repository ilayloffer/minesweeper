package com.example.minesweeper;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

public class LoginActivity extends AppCompatActivity {

    private FirebaseAuth auth;
    private FirebaseFirestore db;
    private TextInputEditText etEmail, etPassword;
    private ProgressBar progressBar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        etEmail = findViewById(R.id.etEmailLogin);
        etPassword = findViewById(R.id.etPasswordLogin);
        Button btnLogin = findViewById(R.id.btnLogin);
        progressBar = findViewById(R.id.progressBarLogin);

        ImageButton backBtn = findViewById(R.id.backBtn);
        backBtn.setOnClickListener(v -> finish());

        btnLogin.setOnClickListener(v -> {
            String email = etEmail.getText().toString().trim();
            String pass = etPassword.getText().toString().trim();

            if (email.isEmpty() || pass.isEmpty()) {
                Toast.makeText(this, "מלא אימייל וסיסמה", Toast.LENGTH_SHORT).show();
                return;
            }

            progressBar.setVisibility(View.VISIBLE);

            auth.signInWithEmailAndPassword(email, pass)
                    .addOnCompleteListener(task -> {
                        if (!task.isSuccessful()) {
                            progressBar.setVisibility(View.GONE);
                            Toast.makeText(this,
                                    "שגיאה: " + task.getException().getMessage(),
                                    Toast.LENGTH_LONG).show();
                            return;
                        }

                        FirebaseUser user = auth.getCurrentUser();
                        if (user == null) return;

                        // 🔥 FETCH USERNAME FROM FIRESTORE
                        db.collection("users")
                                .document(user.getUid())
                                .get()
                                .addOnSuccessListener(doc -> {
                                    progressBar.setVisibility(View.GONE);

                                    String username = doc.getString("username");
                                    if (username == null || username.isEmpty()) {
                                        username = "Player";
                                    }

                                    // 💾 Save locally
                                    SharedPreferences prefs =
                                            getSharedPreferences("MinePrefs", MODE_PRIVATE);
                                    prefs.edit().putString("username", username).apply();

                                    // 🚀 Open MainActivity
                                    Intent intent = new Intent(LoginActivity.this, MainActivity.class);
                                    intent.putExtra("USERNAME", username);
                                    startActivity(intent);
                                    finish();
                                })
                                .addOnFailureListener(e -> {
                                    progressBar.setVisibility(View.GONE);
                                    Toast.makeText(this,
                                            "שגיאה בטעינת שם משתמש",
                                            Toast.LENGTH_SHORT).show();
                                });
                    });
        });
    }
}
