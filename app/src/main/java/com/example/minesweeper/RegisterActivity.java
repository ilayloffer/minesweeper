package com.example.minesweeper;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import java.util.HashMap;
import java.util.Map;

public class RegisterActivity extends AppCompatActivity {

    private static final int PICK_IMAGE_REQUEST = 1;

    private FirebaseAuth auth;
    private TextInputEditText etEmail, etPassword;
    private ProgressBar progressBar;
    private ImageView profileImage;
    private Uri imageUri = null;

    private StorageReference storageRef;
    private FirebaseFirestore db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
        storageRef = FirebaseStorage.getInstance().getReference("profile_pictures");

        etEmail = findViewById(R.id.etEmail);
        etPassword = findViewById(R.id.etPassword);
        Button btnRegister = findViewById(R.id.btnRegister);
        Button btnSelectImage = findViewById(R.id.btnSelectImage);
        profileImage = findViewById(R.id.profileImage);
        progressBar = findViewById(R.id.progressBar);

        // BACK BUTTON
        ImageButton backBtn = findViewById(R.id.backBtn);
        backBtn.setOnClickListener(v -> finish());

        // Select profile image from gallery
        btnSelectImage.setOnClickListener(v -> openFileChooser());

        // Register user
        btnRegister.setOnClickListener(v -> registerUser());
    }


    private void openFileChooser() {
        Intent intent = new Intent();
        intent.setType("image/*");
        intent.setAction(Intent.ACTION_GET_CONTENT);
        startActivityForResult(intent, PICK_IMAGE_REQUEST);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PICK_IMAGE_REQUEST && resultCode == RESULT_OK && data != null && data.getData() != null) {
            imageUri = data.getData();
            profileImage.setImageURI(imageUri);
        }
    }

    private void registerUser() {
        String email = etEmail.getText().toString().trim();
        String pass = etPassword.getText().toString().trim();

        if (email.isEmpty() || pass.isEmpty()) {
            Toast.makeText(this, "מלא אימייל וסיסמה", Toast.LENGTH_SHORT).show();
            return;
        }

        if (pass.length() < 6) {
            Toast.makeText(this, "סיסמה חייבת להיות לפחות 6 תווים", Toast.LENGTH_SHORT).show();
            return;
        }

        progressBar.setVisibility(View.VISIBLE);

        auth.createUserWithEmailAndPassword(email, pass)
                .addOnCompleteListener(task -> {
                    progressBar.setVisibility(View.GONE);
                    if (task.isSuccessful()) {
                        FirebaseUser user = auth.getCurrentUser();
                        if (user != null) {
                            uploadProfileImage(user.getUid(), email);
                        }
                    } else {
                        Toast.makeText(this, "שגיאה: " + task.getException().getMessage(), Toast.LENGTH_LONG).show();
                    }
                });
    }

    private void uploadProfileImage(String userId, String email) {
        Uri uploadUri;

        // If user selected an image, use it — otherwise use a default one from drawable
        if (imageUri != null) {
            uploadUri = imageUri;
        } else {
            uploadUri = Uri.parse("android.resource://" + getPackageName() + "/" + R.drawable.default_profile);
        }

        StorageReference fileRef = storageRef.child(userId + ".jpg");

        fileRef.putFile(uploadUri)
                .addOnSuccessListener(taskSnapshot -> fileRef.getDownloadUrl().addOnSuccessListener(uri -> {
                    saveUserToFirestore(userId, email, uri.toString());
                }))
                .addOnFailureListener(e -> Toast.makeText(this, "שגיאה בהעלאת תמונה: " + e.getMessage(), Toast.LENGTH_SHORT).show());
    }

    private void saveUserToFirestore(String userId, String email, String profileUrl) {
        Map<String, Object> userMap = new HashMap<>();
        userMap.put("email", email);
        userMap.put("profileImageUrl", profileUrl);

        db.collection("users").document(userId).set(userMap)
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(this, "נרשמת בהצלחה!", Toast.LENGTH_LONG).show();
                    finish();
                })
                .addOnFailureListener(e -> Toast.makeText(this, "שגיאה בשמירה: " + e.getMessage(), Toast.LENGTH_SHORT).show());
    }
}
