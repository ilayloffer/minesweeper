package com.example.minesweeper;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.Switch;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

public class SettingsActivity extends AppCompatActivity {

    private static final int REQ_PICK_IMAGE = 1001; // מזהה לבחירת תמונה

    private Switch darkModeSwitch;
    private Button pickImageBtn, applyBtn;

    private String chosenTheme = "Light";
    private String chosenBgUri = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        ImageButton backBtn = findViewById(R.id.backBtn);
        backBtn.setOnClickListener(v -> finish());

        darkModeSwitch = findViewById(R.id.darkModeSwitch);
        pickImageBtn = findViewById(R.id.pickImageBtn);
        applyBtn = findViewById(R.id.applyBtn);

        // מצב כהה/בהיר
        darkModeSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            chosenTheme = isChecked ? "Dark" : "Light";
        });

        // בחירת תמונה מהרשימה
        pickImageBtn.setOnClickListener(v -> {
            Intent pick = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            pick.addCategory(Intent.CATEGORY_OPENABLE);
            pick.setType("image/*");

            // כאן מותר להוסיף רק READ/WRITE
            pick.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

            startActivityForResult(pick, REQ_PICK_IMAGE);
        });

        // כפתור החלה
        applyBtn.setOnClickListener(v -> {
            Intent result = new Intent();
            result.putExtra("theme", chosenTheme);
            if (chosenBgUri != null) {
                result.putExtra("bgUri", chosenBgUri);
            }
            setResult(RESULT_OK, result);
            finish();
        });
    }

    @SuppressLint("WrongConstant")
    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_PICK_IMAGE && resultCode == RESULT_OK && data != null) {
            Uri uri = data.getData();
            if (uri != null) {
                try {
                    // שמירת הרשאות קריאה מתמשכות
                    final int takeFlags = data.getFlags()
                            & (Intent.FLAG_GRANT_READ_URI_PERMISSION
                            | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                            | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                        getContentResolver().takePersistableUriPermission(uri, takeFlags);
                    }
                } catch (Exception e) {
                    Log.w("SettingsActivity", "takePersistableUriPermission failed: " + e);
                }
                chosenBgUri = uri.toString();
                Toast.makeText(this, "Background selected", Toast.LENGTH_SHORT).show();
            }
        }
    }
}
