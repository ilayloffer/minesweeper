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
import androidx.appcompat.app.AlertDialog;
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
        int savedDifficulty = prefs.getInt("difficulty", 5);
        difficultySeek.setMax(15);
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

        // 1. כפתור משחק רגיל (Offline)
        startBtn.setOnClickListener(v -> {
            // באופליין לוקחים את הגודל מהסליידר
            int size = difficultySeek.getProgress() + 5;
            Intent intent = new Intent(MainActivity.this, GameActivity.class);
            intent.putExtra("size", size);
            intent.putExtra("isOnline", false);
            startActivity(intent);
        });

        // 2. כפתור משחק רשת (Online)
        startOnlineBtn.setOnClickListener(v -> {
            FirebaseUser currentUser = mAuth.getCurrentUser();
            if (currentUser == null) {
                Toast.makeText(this, "Please Login to play Online", Toast.LENGTH_SHORT).show();
                startActivity(new Intent(MainActivity.this, LoginActivity.class));
            } else {
                String[] roles = {"Player 1 (Host Game)", "Player 2 (Join Game)"};

                new AlertDialog.Builder(this)
                        .setTitle("Select your role")
                        .setItems(roles, (dialog, which) -> {

                            // התיקון: באונליין הגודל תמיד מקובע ל-10!
                            int size = 10;

                            Intent intent = new Intent(MainActivity.this, GameActivity.class);
                            intent.putExtra("isOnline", true);
                            intent.putExtra("size", size);
                            intent.putExtra("gameId", "global_room");

                            if (which == 0) { // בחר שחקן 1
                                intent.putExtra("currentUser", "player1");
                                intent.putExtra("otherPlayer", "player2");
                            } else { // בחר שחקן 2
                                intent.putExtra("currentUser", "player2");
                                intent.putExtra("otherPlayer", "player1");
                            }

                            startActivity(intent);
                        }).show();
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
        updateWelcomeMessage();
        invalidateOptionsMenu();
    }

    private void updateWelcomeMessage() {
        FirebaseUser currentUser = mAuth.getCurrentUser();
        String displayInternal;

        if (currentUser != null) {
            String fbName = currentUser.getDisplayName();
            if (fbName != null && !fbName.isEmpty()) {
                displayInternal = fbName;
            } else {
                displayInternal = getIntent().getStringExtra("USERNAME");
            }
        } else {
            displayInternal = "Guest";
        }

        if (displayInternal == null) displayInternal = "Player";
        tvWelcome.setText("Welcome, " + displayInternal + "!");
    }

    private void updateDifficultyLabel(int progress) {
        int size = progress + 5;
        difficultyLabel.setText("Board Size: " + size + " x " + size);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        try {
            unregisterReceiver(networkReceiver);
        } catch (IllegalArgumentException e) {
            // תתעלם אם לא נרשם
        }
    }

    // -----------------------------
    // 🔊 MENU CONTROL
    // -----------------------------
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main_menu, menu);

        MenuItem musicItem = menu.findItem(R.id.action_music);
        musicItem.setChecked(prefs.getBoolean("music_on", false));

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