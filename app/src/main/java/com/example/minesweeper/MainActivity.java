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
    private Button startOnlineBtn;
    private SharedPreferences prefs;

    // ActivityResultLauncher for Settings screen
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

    // BroadcastReceiver for network changes
    private final BroadcastReceiver networkReceiver = new NetworkChangeReceiver();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Log.i("MainActivity", "onCreate called");

        prefs = getSharedPreferences("MinePrefs", MODE_PRIVATE);

        difficultySeek = findViewById(R.id.difficultySeek);
        difficultyLabel = findViewById(R.id.difficultyLabel);
        startBtn = findViewById(R.id.startBtn);
        startOnlineBtn = findViewById(R.id.StartOnl);

        // Load saved difficulty
        int saved = prefs.getInt("difficulty", 5);
        difficultySeek.setProgress(saved);
        updateDifficultyLabel(saved);

        // SeekBar listener
        difficultySeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                updateDifficultyLabel(progress);
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {
                prefs.edit().putInt("difficulty", seekBar.getProgress()).apply();
            }
        });

        // Start local game
        startBtn.setOnClickListener(v -> {
            int size = difficultySeek.getProgress() + 1;
            Intent i = new Intent(MainActivity.this, GameActivity.class);
            i.putExtra("size", size);
            startActivity(i);
        });

        // Start online game
        startOnlineBtn.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, OnlineGameActivity.class);
            startActivity(intent);
        });

        // Register network receiver
        registerReceiver(networkReceiver, new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION));

        // Display welcome message
        TextView tvWelcome = findViewById(R.id.tvWelcome); // make sure you have this TextView
        String username = getIntent().getStringExtra("USERNAME");
        if (username != null && !username.isEmpty()) {
            tvWelcome.setText("שלום, " + username + "!");
        }
    }

    private void updateDifficultyLabel(int progress) {
        int size = progress + 1;
        difficultyLabel.setText("Board Size: " + size + " x " + size);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        try { unregisterReceiver(networkReceiver); } catch (Exception ignored) {}
    }

    // -----------------------------
    // 🔊 MENU MUSIC CONTROL SECTION
    // -----------------------------
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main_menu, menu);

        // Restore last music state
        boolean wasMusicOn = prefs.getBoolean("music_on", false);
        MenuItem musicItem = menu.findItem(R.id.action_music);
        musicItem.setChecked(wasMusicOn);

        if (wasMusicOn) {
            handleMusicService(true);
        }

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        if (id == R.id.action_music) {
            boolean newState = !item.isChecked();
            item.setChecked(newState);
            prefs.edit().putBoolean("music_on", newState).apply();
            handleMusicService(newState);
            return true;

        } else if (id == R.id.menu_settings) {
            Intent intent = new Intent(this, SettingsActivity.class);
            settingsLauncher.launch(intent);
            return true;

        } else if (id == R.id.menu_login) {
            startActivity(new Intent(this, LoginActivity.class));
            return true;

        } else if (id == R.id.menu_register) {
            startActivity(new Intent(this, RegisterActivity.class));
            return true;

        } else if (id == R.id.leaderboard) {
            startActivity(new Intent(this, LeaderboardActivity.class));
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    // 🔧 Helper for starting/stopping MusicService
    private void handleMusicService(boolean start) {
        Intent svc = new Intent(this, MusicService.class);
        if (start) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(svc);
            } else {
                startService(svc);
            }
        } else {
            stopService(svc);
        }
    }
}
