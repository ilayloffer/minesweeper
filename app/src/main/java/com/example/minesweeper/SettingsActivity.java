package com.example.minesweeper;

import android.content.Intent;
import android.content.SharedPreferences;
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
import androidx.appcompat.app.AppCompatActivity;

public class SettingsActivity extends AppCompatActivity {

    private Switch darkModeSwitch;
    private Button pickImageBtn, applyBtn;

    private String chosenTheme = "Light";
    private String chosenBgUri = null;

    private SharedPreferences sp;

    private final ActivityResultLauncher<Intent> pickImageLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.StartActivityForResult(),
                    new ActivityResultCallback<ActivityResult>() {
                        @Override
                        public void onActivityResult(ActivityResult result) {
                            if (result.getResultCode() == RESULT_OK && result.getData() != null) {

                                Intent data = result.getData();
                                Uri uri = data.getData();

                                if (uri != null) {
                                    // שמירת ה-URI של התמונה שנבחרה
                                    chosenBgUri = uri.toString();
                                    Toast.makeText(SettingsActivity.this, "Background selected", Toast.LENGTH_SHORT).show();

                                    try {
                                        //  בניית הדגלים בצורה מפורשת
                                        int intentFlags = data.getFlags();
                                        int takeFlags = 0;

                                        if ((intentFlags & Intent.FLAG_GRANT_READ_URI_PERMISSION) != 0) {
                                            takeFlags |= Intent.FLAG_GRANT_READ_URI_PERMISSION;
                                        }
                                        if ((intentFlags & Intent.FLAG_GRANT_WRITE_URI_PERMISSION) != 0) {
                                            takeFlags |= Intent.FLAG_GRANT_WRITE_URI_PERMISSION;
                                        }

                                        // ביצוע הרישום הקבוע רק אם יש הרשאות רלוונטיות
                                        if (takeFlags != 0 && Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                                            SettingsActivity.this.getContentResolver()
                                                    .takePersistableUriPermission(uri, takeFlags);
                                        }
                                    } catch (Exception e) {
                                        Log.w("SettingsActivity", "takePersistableUriPermission failed: " + e);
                                    }
                                }
                            }
                        }
                    });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        sp = getSharedPreferences("AppSettings", MODE_PRIVATE);

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

        // --- טעינת מצב קיים מה-SharedPreferences ---
        chosenTheme = sp.getString("theme", "Light");
        chosenBgUri = sp.getString("bgUri", null);

        // מעדכנים את הסוויץ' לפי מה ששמור בזיכרון
        darkModeSwitch.setChecked("Dark".equals(chosenTheme));

        // האזנה לשינוי במצב כהה/בהיר
        darkModeSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                chosenTheme = isChecked ? "Dark" : "Light";
            }
        });

        // בחירת תמונה מהגלריה
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

        // כפתור שמירה והחלה
        applyBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // שמירת הנתונים בתוך ה-SharedPreferences בצורה קבועה
                SharedPreferences.Editor editor = sp.edit();
                editor.putString("theme", chosenTheme);
                if (chosenBgUri != null) {
                    editor.putString("bgUri", chosenBgUri);
                }
                editor.apply();

                Toast.makeText(SettingsActivity.this, "Settings saved! 🎉", Toast.LENGTH_SHORT).show();

                // סגירת המסך וחזרה למסך הקודם
                SettingsActivity.this.finish();
            }
        });
    }
}