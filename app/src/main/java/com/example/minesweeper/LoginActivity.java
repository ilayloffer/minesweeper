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

import com.google.android.gms.tasks.Task;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

public class LoginActivity extends AppCompatActivity {

    private FirebaseAuth auth;
    private DatabaseReference usersRef;
    private TextInputEditText etEmail, etPassword;
    private ProgressBar progressBar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        auth = FirebaseAuth.getInstance();
        usersRef = FirebaseDatabase.getInstance().getReference("users");

        etEmail = findViewById(R.id.etEmailLogin);
        etPassword = findViewById(R.id.etPasswordLogin);
        Button btnLogin = findViewById(R.id.btnLogin);
        progressBar = findViewById(R.id.progressBarLogin);

        ImageButton backBtn = findViewById(R.id.backBtn);
        backBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                LoginActivity.this.finish();
            }
        });

        btnLogin.setOnClickListener(new View.OnClickListener() {
            private void onFailure(Exception e) {
                progressBar.setVisibility(View.GONE);
                Toast.makeText(LoginActivity.this,
                        "שגיאה בטעינת שם משתמש",
                        Toast.LENGTH_SHORT).show();
            }

            private void onSuccess(DataSnapshot snapshot) {

                progressBar.setVisibility(View.GONE);

                String username = snapshot.child("username").getValue(String.class);

                if (username == null || username.isEmpty()) {
                    username = "Player";
                }

                SharedPreferences prefs =
                        LoginActivity.this.getSharedPreferences("MinePrefs", MODE_PRIVATE);
                prefs.edit().putString("username", username).apply();

                Intent intent = new Intent(LoginActivity.this, MainActivity.class);
                intent.putExtra("USERNAME", username);
                LoginActivity.this.startActivity(intent);
                LoginActivity.this.finish();

            }

            private void onComplete(Task<AuthResult> task) {
                if (!task.isSuccessful()) {
                    progressBar.setVisibility(View.GONE);
                    Toast.makeText(LoginActivity.this,
                            "שגיאה: " + task.getException().getMessage(),
                            Toast.LENGTH_LONG).show();
                    return;
                }

                FirebaseUser user = auth.getCurrentUser();
                if (user == null) return;

                // 🔥 FETCH USERNAME FROM DB
                Task<DataSnapshot> dataSnapshotTask = usersRef.child(user.getUid())
                        .get()
                        .addOnSuccessListener(this::onSuccess)
                        .addOnFailureListener(this::onFailure);
            }

            @Override
            public void onClick(View v) {
                String email = etEmail.getText().toString().trim();
                String pass = etPassword.getText().toString().trim();

                if (email.isEmpty() || pass.isEmpty()) {
                    Toast.makeText(LoginActivity.this, "מלא אימייל וסיסמה", Toast.LENGTH_SHORT).show();
                    return;
                }

                progressBar.setVisibility(View.VISIBLE);

                Task<AuthResult> authResultTask = auth.signInWithEmailAndPassword(email, pass)
                        .addOnCompleteListener(this::onComplete);
            }
        });
    }
}
