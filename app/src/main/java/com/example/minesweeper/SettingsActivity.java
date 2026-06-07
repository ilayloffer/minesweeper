package com.example.minesweeper;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.ImageButton;
import android.widget.Switch;
import android.widget.Toast;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

public class SettingsActivity extends AppCompatActivity {

    private static final int REQ_PICK_IMAGE = 1001; // מזהה לבחירת תמונה

    private Switch darkModeSwitch;
    private Button pickImageBtn, applyBtn;

    private String chosenTheme = "Light";
    private String chosenBgUri = null;

    private final ActivityResultLauncher<Intent> pickImageLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.StartActivityForResult(),
                    new ActivityResultCallback<ActivityResult>() {
                        @Override
                        public void onActivityResult(ActivityResult result) {
                            if (result.getResultCode() == RESULT_OK
                                    && result.getData() != null) {

                                Intent data = result.getData();
                                Uri uri = data.getData();

                                if (uri != null) {
                                    try {
                                        int takeFlags = data.getFlags()
                                                & (Intent.FLAG_GRANT_READ_URI_PERMISSION
                                                | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                                                | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);

                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                                            SettingsActivity.this.getContentResolver()
                                                    .takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                                        }
                                    } catch (Exception e) {
                                        Log.w("SettingsActivity",
                                                "takePersistableUriPermission failed: " + e);
                                    }

                                    chosenBgUri = uri.toString();
                                    Toast.makeText(
                                            SettingsActivity.this,
                                            "Background selected",
                                            Toast.LENGTH_SHORT
                                    ).show();
                                }
                            }
                        }
                    });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        ImageButton backBtn = findViewById(R.id.backBtn);
        backBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                SettingsActivity.this.finish();
            }
        });

        darkModeSwitch = findViewById(R.id.darkModeSwitch);
        pickImageBtn = findViewById(R.id.pickImageBtn);
        applyBtn = findViewById(R.id.applyBtn);

        // מצב כהה/בהיר
        darkModeSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                chosenTheme = isChecked ? "Dark" : "Light";
            }
        });

        // בחירת תמונה מהרשימה
        pickImageBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent pick = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                pick.addCategory(Intent.CATEGORY_OPENABLE);
                pick.setType("image/*");
                pick.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                pick.addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);

                pickImageLauncher.launch(pick);
            }
        });

        // כפתור החלה
        applyBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent result = new Intent();
                result.putExtra("theme", chosenTheme);
                if (chosenBgUri != null) {
                    result.putExtra("bgUri", chosenBgUri);
                }
                SettingsActivity.this.setResult(RESULT_OK, result);
                SettingsActivity.this.finish();
            }
        });
    }
}
