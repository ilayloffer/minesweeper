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
import com.google.firebase.auth.FirebaseUser;

public class MainActivity extends AppCompatActivity {

    private SeekBar difficultySeek;
    private TextView difficultyLabel;
    private TextView tvWelcome;
    private Button startBtn;
    private Button startOnlineBtn;
    private SharedPreferences prefs;
    private FirebaseAuth mAuth;

    // ActivityResultLauncher for Settings screen
    private final ActivityResultLauncher<Intent> settingsLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    String theme = result.getData().getStringExtra("theme");
                    String bgUri = result.getData().getStringExtra("bgUri");

                    if (theme != null) {
                        prefs.edit().putString("theme", theme).apply();
                    }
                    if (bgUri != null) {
                        prefs.edit().putString("bgUri", bgUri).apply();
                    }
                }
            });

    // BroadcastReceiver for network changes
    // Ensure you have a class named NetworkChangeReceiver in your project!
    private final BroadcastReceiver networkReceiver = new NetworkChangeReceiver();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mAuth = FirebaseAuth.getInstance();
        prefs = getSharedPreferences("MinePrefs", MODE_PRIVATE);

        // View bindings
        difficultySeek = findViewById(R.id.difficultySeek);
        difficultyLabel = findViewById(R.id.difficultyLabel);
        tvWelcome = findViewById(R.id.tvWelcome);
        startBtn = findViewById(R.id.startBtn);
        startOnlineBtn = findViewById(R.id.StartOnl);

        // --- Difficulty Logic ---
        // Load saved difficulty (default to 5)
        int savedDifficulty = prefs.getInt("difficulty", 5);
        difficultySeek.setMax(15); // Max size 20 (5 min + 15)
        difficultySeek.setProgress(savedDifficulty);
        updateDifficultyLabel(savedDifficulty);

        difficultySeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                updateDifficultyLabel(progress);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                prefs.edit().putInt("difficulty", seekBar.getProgress()).apply();
            }
        });

        // --- Buttons ---
        startBtn.setOnClickListener(v -> {
            // Calculation: Base size 5 + progress.
            // If progress is 0, size is 5x5.
            int size = difficultySeek.getProgress() + 5;
            Intent intent = new Intent(MainActivity.this, GameActivity.class);
            intent.putExtra("size", size);
            startActivity(intent);
        });

        startOnlineBtn.setOnClickListener(v -> {
            if (mAuth.getCurrentUser() == null) {
                Toast.makeText(this, "Please Login to play Online", Toast.LENGTH_SHORT).show();
                startActivity(new Intent(MainActivity.this, LoginActivity.class));
            } else {
                startActivity(new Intent(MainActivity.this, OnlineGameActivity.class));
            }
        });

        // --- Music Check on Launch ---
        if (prefs.getBoolean("music_on", false)) {
            handleMusicService(true);
        }

        // Register network receiver
        try {
            registerReceiver(networkReceiver, new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION));
        } catch (Exception e) {
            Log.e("MainActivity", "Receiver error: " + e.getMessage());
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // This ensures the name updates immediately after returning from RegisterActivity
        updateWelcomeMessage();
        invalidateOptionsMenu(); // Force menu to redraw (Update Login/Logout buttons)
    }

    private void updateWelcomeMessage() {
        FirebaseUser currentUser = mAuth.getCurrentUser();
        String displayInternal;

        if (currentUser != null) {
            // Priority 1: Get from Firebase Auth (Synced with RegisterActivity)
            String fbName = currentUser.getDisplayName();
            if (fbName != null && !fbName.isEmpty()) {
                displayInternal = fbName;
            } else {
                // Priority 2: Fallback to Intent or Prefs if Firebase name is empty
                displayInternal = getIntent().getStringExtra("USERNAME");
            }
        } else {
            displayInternal = "Guest";
        }

        // Final fallback
        if (displayInternal == null) displayInternal = "Player";

        tvWelcome.setText("Welcome, " + displayInternal + "!");
    }

    private void updateDifficultyLabel(int progress) {
        // Minimum size 5, so 0 progress = 5x5
        int size = progress + 5;
        difficultyLabel.setText("Board Size: " + size + " x " + size);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        try {
            unregisterReceiver(networkReceiver);
        } catch (IllegalArgumentException e) {
            // Receiver not registered or already unregistered
        }
    }

    // -----------------------------
    // 🔊 MENU CONTROL
    // -----------------------------
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main_menu, menu);

        // Handle Music Checkbox visual state
        MenuItem musicItem = menu.findItem(R.id.action_music);
        musicItem.setChecked(prefs.getBoolean("music_on", false));

        // Handle Login/Logout visibility
        boolean isLoggedIn = mAuth.getCurrentUser() != null;

        MenuItem loginItem = menu.findItem(R.id.menu_login);
        MenuItem registerItem = menu.findItem(R.id.menu_register);
        MenuItem logoutItem = menu.findItem(R.id.menu_logout);

        if (loginItem != null) loginItem.setVisible(!isLoggedIn);
        if (registerItem != null) registerItem.setVisible(!isLoggedIn);
        if (logoutItem != null) logoutItem.setVisible(isLoggedIn);

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
        }
        else if (id == R.id.menu_settings) {
            settingsLauncher.launch(new Intent(this, SettingsActivity.class));
            return true;
        }
        else if (id == R.id.menu_login) {
            startActivity(new Intent(this, LoginActivity.class));
            return true;
        }
        else if (id == R.id.menu_register) {
            startActivity(new Intent(this, RegisterActivity.class));
            return true;
        }
        else if (id == R.id.leaderboard) {
            startActivity(new Intent(this, LeaderboardActivity.class));
            return true;
        }
        else if (id == R.id.menu_logout) {
            mAuth.signOut();
            Toast.makeText(this, "Logged out", Toast.LENGTH_SHORT).show();

            // Refresh the screen to show "Guest" and update buttons
            updateWelcomeMessage();
            invalidateOptionsMenu();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

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