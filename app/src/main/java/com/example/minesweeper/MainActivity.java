package com.example.minesweeper;

import android.content.BroadcastReceiver;
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
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;

public class MainActivity extends AppCompatActivity {

    private SeekBar difficultySeek;
    private TextView difficultyLabel;
    private TextView tvWelcome;
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

        // View bindings
        difficultySeek = findViewById(R.id.difficultySeek);
        difficultyLabel = findViewById(R.id.difficultyLabel);
        tvWelcome = findViewById(R.id.tvWelcome);
        startBtn = findViewById(R.id.startBtn);
        startOnlineBtn = findViewById(R.id.StartOnl);

        // Load saved difficulty
        int savedDifficulty = prefs.getInt("difficulty", 5);
        difficultySeek.setProgress(savedDifficulty);
        updateDifficultyLabel(savedDifficulty);

        // Difficulty SeekBar listener
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
            Intent intent = new Intent(MainActivity.this, GameActivity.class);
            intent.putExtra("size", size);
            startActivity(intent);
        });

        // Start online game
        startOnlineBtn.setOnClickListener(v -> startActivity(new Intent(MainActivity.this, OnlineGameActivity.class)));

        // Register network receiver
        registerReceiver(networkReceiver, new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION));

        // Display welcome message
        displayWelcomeMessage();
    }

    private void displayWelcomeMessage() {
        String username = getIntent().getStringExtra("USERNAME");

        if (username == null || username.isEmpty()) {
            username = prefs.getString("username", null);
        } else {
            // Save newly received username for future use
            prefs.edit().putString("username", username).apply();
        }

        if (username == null || username.isEmpty()) {
            username = "Player";
        }

        tvWelcome.setText("Welcome, " + username + "!");
    }

    private void updateDifficultyLabel(int progress) {
        int size = progress + 1;
        difficultyLabel.setText("Board Size: " + size + " x " + size);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Refresh welcome message in case username changed
        displayWelcomeMessage();
        invalidateOptionsMenu(); // Refresh menu to show/hide login/logout
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        try {
            unregisterReceiver(networkReceiver);
        } catch (Exception ignored) {}
    }

    // -----------------------------
    // 🔊 MENU MUSIC & LOGIN/LOGOUT CONTROL
    // -----------------------------
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main_menu, menu);

        boolean wasMusicOn = prefs.getBoolean("music_on", false);
        MenuItem musicItem = menu.findItem(R.id.action_music);
        musicItem.setChecked(wasMusicOn);
        if (wasMusicOn) handleMusicService(true);

        // Show/hide menu items based on login state
        boolean isLoggedIn = FirebaseAuth.getInstance().getCurrentUser() != null;
        menu.findItem(R.id.menu_login).setVisible(!isLoggedIn);
        menu.findItem(R.id.menu_register).setVisible(!isLoggedIn);
        menu.findItem(R.id.menu_logout).setVisible(isLoggedIn);

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
            settingsLauncher.launch(new Intent(this, SettingsActivity.class));
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

        } else if (id == R.id.menu_logout) {
            // 🔒 Logout
            FirebaseAuth.getInstance().signOut();
            prefs.edit().remove("username").apply();

            Intent intent = new Intent(this, LoginActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP |
                    Intent.FLAG_ACTIVITY_NEW_TASK |
                    Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    // 🔧 Helper for starting/stopping MusicService
    private void handleMusicService(boolean start) {
        Intent svc = new Intent(this, MusicService.class);
        if (start) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(svc);
            else startService(svc);
        } else {
            stopService(svc);
        }
    }
}
