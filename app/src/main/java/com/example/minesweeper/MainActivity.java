package com.example.minesweeper;

import android.content.BroadcastReceiver;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.net.ConnectivityManager;
import android.os.Build;
import android.os.Bundle;
import android.text.InputType;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

// ספריות הברקוד
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.journeyapps.barcodescanner.BarcodeEncoder;
import com.journeyapps.barcodescanner.ScanContract;
import com.journeyapps.barcodescanner.ScanOptions;
import com.google.zxing.BarcodeFormat;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

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

    // ActivityResultLauncher for QR Scanner
    private final ActivityResultLauncher<ScanOptions> qrScannerLauncher = registerForActivityResult(
            new ScanContract(),
            result -> {
                if (result.getContents() != null) {
                    String scannedRoomId = result.getContents();
                    Toast.makeText(this, getPlayerName() + " Joined Room: " + scannedRoomId, Toast.LENGTH_SHORT).show();
                    Log.d("Rinat","Joined Room: " + scannedRoomId);
                    Log.d("Rinat",getPlayerName());
                    startGameAsJoiner(scannedRoomId);
                } else {
                    Toast.makeText(this, "Scan cancelled", Toast.LENGTH_SHORT).show();
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

// כפתור Leaderboard - הוספנו לו את השם של השחקן!
        Button btnLeaderboard = findViewById(R.id.btnLeaderboard);
        btnLeaderboard.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, LeaderboardActivity.class);
            intent.putExtra("currentUser", getPlayerName());
            startActivity(intent);
        });

        // View bindings
        difficultySeek = findViewById(R.id.difficultySeek);
        difficultyLabel = findViewById(R.id.difficultyLabel);
        tvWelcome = findViewById(R.id.tvWelcome);
        startBtn = findViewById(R.id.startBtn);
        startOnlineBtn = findViewById(R.id.btnOnlineMatch);

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
            int size = difficultySeek.getProgress() + 5;
            Intent intent = new Intent(MainActivity.this, GameActivity.class);
            intent.putExtra("size", size);
            intent.putExtra("isOnline", false);
            // **השורה החדשה והחשובה שמעבירה את השם!**
            intent.putExtra("currentUser", getPlayerName());
            startActivity(intent);
        });

        // 2. כפתור משחק רשת (Online) עם חדרים וברקוד
        startOnlineBtn.setOnClickListener(v -> {
            FirebaseUser currentUser = mAuth.getCurrentUser();
            if (currentUser == null) {
                Toast.makeText(this, "Please Login to play Online", Toast.LENGTH_SHORT).show();
                startActivity(new Intent(MainActivity.this, LoginActivity.class));
            } else {
                showMultiplayerDialog();
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

    // --- חילוץ שם השחקן ---

    // פונקציה ששולפת את שם השחקן בצורה הכי מדויקת שיש
    private String getPlayerName() {
        FirebaseUser currentUser = mAuth.getCurrentUser();
        String name = "Guest";

        if (currentUser != null) {
            String fbName = currentUser.getDisplayName();
            if (fbName != null && !fbName.isEmpty()) {
                name = fbName;
            } else if (getIntent().getStringExtra("USERNAME") != null) {
                name = getIntent().getStringExtra("USERNAME");
            } else if (currentUser.getEmail() != null) {
                // אם אין לו שם מוגדר בפיירבייס, ניקח את החלק שלפני ה-@ באימייל שלו
                name = currentUser.getEmail().split("@")[0];
            }
        } else if (getIntent().getStringExtra("USERNAME") != null) {
            name = getIntent().getStringExtra("USERNAME");
        }
        return name;
    }

    // --- Multiplayer Matchmaking Methods ---

    private void showMultiplayerDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Online Multiplayer");
        builder.setMessage("Do you want to create a new room or join an existing one?");

        builder.setPositiveButton("Create Room", (dialog, which) -> generateRoomAndShowQR());
        builder.setNegativeButton("Join Room", (dialog, which) -> showJoinDialog());

        builder.show();
    }

    private void generateRoomAndShowQR() {
        String roomId = String.format("%04d", new Random().nextInt(10000));

        DatabaseReference roomRef = FirebaseDatabase.getInstance()
                .getReference("rooms")
                .child(roomId);

        // יצירת מבנה חדר
        Map<String, Object> roomData = new HashMap<>();
        roomData.put("status", "waiting");
        roomData.put("host", getPlayerName());

        roomRef.setValue(roomData);

        // הוספת שחקן ראשון
        roomRef.child("players").child("player1").setValue(getPlayerName());

        ImageView qrImageView = new ImageView(this);
        try {
            BarcodeEncoder barcodeEncoder = new BarcodeEncoder();
            Bitmap bitmap = barcodeEncoder.encodeBitmap(roomId, BarcodeFormat.QR_CODE, 600, 600);
            qrImageView.setImageBitmap(bitmap);
        } catch (Exception e) {
            e.printStackTrace();
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Room Code: " + roomId);
        builder.setMessage("Ask your friend to scan this QR code or enter the number " + roomId + ".");
        builder.setView(qrImageView);

        builder.setPositiveButton("Start Game", (dialog, which) -> {
            Intent intent = new Intent(MainActivity.this, GameActivity.class);
            intent.putExtra("isOnline", true);
            intent.putExtra("size", 10);
            intent.putExtra("gameId", roomId);
            // התיקון: שולחים את השם האמיתי של השחקן שיצר את החדר
            intent.putExtra("currentUser", getPlayerName());
            Log.d("Rinat", "Opened by: " + getPlayerName());
            startActivity(intent);
        });

        builder.show();
    }

    private void startGameAsJoiner(String roomId) {

        DatabaseReference roomRef = FirebaseDatabase.getInstance()
                .getReference("rooms")
                .child(roomId);

        // הוספת שחקן שני
        roomRef.child("players").child("player2").setValue(getPlayerName());

        // שינוי סטטוס
        roomRef.child("status").setValue("playing");

        Intent intent = new Intent(MainActivity.this, GameActivity.class);
        intent.putExtra("isOnline", true);
        intent.putExtra("size", 10);
        intent.putExtra("gameId", roomId);
        // התיקון: שולחים את השם האמיתי של השחקן שהצטרף לחדר
        intent.putExtra("currentUser", getPlayerName());
        Log.d("Rinat", "Joined: " + getPlayerName());
        startActivity(intent);
    }

    private void showJoinDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Join Room");
        builder.setMessage("Enter the 4-digit room code or scan the QR.");

        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        input.setHint("e.g., 4829");

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(60, 20, 60, 0);
        layout.addView(input);
        builder.setView(layout);

        builder.setPositiveButton("Join", (dialog, which) -> {
            String code = input.getText().toString().trim();
            if (!code.isEmpty()) {
                startGameAsJoiner(code);
            } else {
                Toast.makeText(MainActivity.this, "Please enter a valid code", Toast.LENGTH_SHORT).show();
            }
        });

        builder.setNeutralButton("Scan QR", (dialog, which) -> startQRScanner());
        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.cancel());

        builder.show();
    }

    private void startQRScanner() {
        ScanOptions options = new ScanOptions();
        options.setPrompt("Scan your friend's Room QR Code");
        options.setBeepEnabled(true);
        options.setOrientationLocked(true);
        options.setCaptureActivity(com.journeyapps.barcodescanner.CaptureActivity.class);

        qrScannerLauncher.launch(options);
    }


    // --- Utility Methods ---

    @Override
    protected void onResume() {
        super.onResume();
        updateWelcomeMessage();
        invalidateOptionsMenu();
    }

    private void updateWelcomeMessage() {
        // עכשיו גם פה אנחנו משתמשים בפונקציה המסודרת שיצרנו
        String displayInternal = getPlayerName();
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

    // --- Menu Methods ---

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