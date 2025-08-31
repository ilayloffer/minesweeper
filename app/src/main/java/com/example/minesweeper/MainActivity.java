package com.example.minesweeper;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Button;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {

    private SeekBar difficultySeek;
    private TextView difficultyLabel;
    private Switch musicSwitch;
    private Button startBtn;

    private SharedPreferences prefs;

    // ActivityResultLauncher (חדש) למסך ההגדרות
    private final ActivityResultLauncher<Intent> settingsLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    String theme = result.getData().getStringExtra("theme");
                    String bgUri = result.getData().getStringExtra("bgUri");
                    Log.d("MainActivity", "Settings result -> theme=" + theme + ", bgUri=" + bgUri);
                    if (theme != null) {
                        prefs.edit().putString("theme", theme).apply();
                        Toast.makeText(this, "Theme: " + theme, Toast.LENGTH_SHORT).show();
                    }
                    if (bgUri != null) {
                        prefs.edit().putString("bgUri", bgUri).apply();
                    }
                }
            });

    // BroadcastReceiver (רשת) - נרשם דינמית
    private final BroadcastReceiver networkReceiver = new NetworkChangeReceiver();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Log.i("MainActivity", "onCreate called");

        prefs = getSharedPreferences("MinePrefs", MODE_PRIVATE);

        difficultySeek = findViewById(R.id.difficultySeek);        // findViewById
        difficultyLabel = findViewById(R.id.difficultyLabel);
        musicSwitch = findViewById(R.id.musicSwitch);
        startBtn = findViewById(R.id.startBtn);

        int saved = prefs.getInt("difficulty", 5);
        difficultySeek.setProgress(saved);
        updateDifficultyLabel(saved);

        difficultySeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() { // מחלקה אנונימית
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                updateDifficultyLabel(progress);
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {
                prefs.edit().putInt("difficulty", seekBar.getProgress()).apply(); // SharedPreferences
            }
        });

        musicSwitch.setChecked(prefs.getBoolean("music_on", false));
        musicSwitch.setOnCheckedChangeListener((btn, isChecked) -> { // setOnClickListener/Listener
            prefs.edit().putBoolean("music_on", isChecked).apply();
            Intent svc = new Intent(this, MusicService.class);
            if (isChecked) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(svc);
                } else {
                    startService(svc);
                }
            } else {
                stopService(svc);
            }
        });

        startBtn.setOnClickListener(v -> {
            int size = difficultySeek.getProgress() + 1; // 1..10
            Intent i = new Intent(MainActivity.this, GameActivity.class); // startActivity
            i.putExtra("size", size);
            startActivity(i);
        });

        // רישום BroadcastReceiver דינמי
        registerReceiver(networkReceiver, new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION));
    }

    private void updateDifficultyLabel(int progress) {
        int size = progress + 1; // 1..10
        difficultyLabel.setText("Board Size: " + size + " x " + size);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        try { unregisterReceiver(networkReceiver); } catch (Exception ignored) {}
    }

    // MENU
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main_menu, menu);
        return true;
    }
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.menu_settings) {
            Intent intent = new Intent(this, SettingsActivity.class);
            settingsLauncher.launch(intent); // ActivityResultLauncher
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
