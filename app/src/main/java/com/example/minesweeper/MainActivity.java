package com.example.minesweeper;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

public class MainActivity extends AppCompatActivity {

    private SeekBar difficultySeek;
    private TextView difficultyLabel, tvWelcome;
    private Button startBtn, btnOnlineMatch, btnLeaderboard, btnContinue;
    private FirebaseAuth mAuth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mAuth = FirebaseAuth.getInstance();

        // אתחול רכיבים
        difficultySeek = findViewById(R.id.difficultySeek);
        difficultyLabel = findViewById(R.id.difficultyLabel);
        tvWelcome = findViewById(R.id.tvWelcome);
        startBtn = findViewById(R.id.startBtn);
        btnOnlineMatch = findViewById(R.id.btnOnlineMatch);
        btnLeaderboard = findViewById(R.id.btnLeaderboard);
        btnContinue = findViewById(R.id.btnContinue);

        setupListeners();
    }

    private void setupListeners() {
        difficultySeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int size = progress + 5;
                difficultyLabel.setText("Board Size: " + size + " x " + size);
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        startBtn.setOnClickListener(v -> {
            int size = difficultySeek.getProgress() + 5;
            startGame(size, false);
        });

        btnContinue.setOnClickListener(v -> {
            SharedPreferences sp = getSharedPreferences("SavedGame", MODE_PRIVATE);
            int savedSize = sp.getInt("size", 10);
            Intent intent = new Intent(this, GameActivity.class);
            intent.putExtra("size", savedSize);
            intent.putExtra("isOnline", false);
            intent.putExtra("loadSaved", true);
            intent.putExtra("currentUser", getPlayerName());
            startActivity(intent);
        });

        btnOnlineMatch.setOnClickListener(v -> {
            if (mAuth.getCurrentUser() == null) {
                startActivity(new Intent(this, LoginActivity.class));
            } else {
                // לוגיקה של אונליין (חיפוש חדר/שידוך)
                Toast.makeText(this, "Searching for online match...", Toast.LENGTH_SHORT).show();
            }
        });

        btnLeaderboard.setOnClickListener(v -> {
            Intent i = new Intent(this, LeaderboardActivity.class);
            i.putExtra("currentUser", getPlayerName());
            startActivity(i);
        });
    }

    private void startGame(int size, boolean loadSaved) {
        Intent i = new Intent(this, GameActivity.class);
        i.putExtra("size", size);
        i.putExtra("isOnline", false);
        i.putExtra("loadSaved", loadSaved);
        i.putExtra("currentUser", getPlayerName());
        startActivity(i);
    }

    private String getPlayerName() {
        FirebaseUser user = mAuth.getCurrentUser();
        if (user != null) {
            return user.getDisplayName() != null ? user.getDisplayName() : user.getEmail().split("@")[0];
        }
        return "Guest";
    }

    @Override
    protected void onResume() {
        super.onResume();
        tvWelcome.setText("Welcome, " + getPlayerName() + "!");

        SharedPreferences sp = getSharedPreferences("SavedGame", MODE_PRIVATE);
        boolean hasSaved = sp.getBoolean("hasSaved", false);
        btnContinue.setVisibility(hasSaved ? View.VISIBLE : View.GONE);

        // רענון התפריט העליון כדי לעדכן נראות כפתורי Login/Logout
        invalidateOptionsMenu();
    }

    // --- חיבור ה-MENU ששלחת לקוד ---

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main_menu, menu);

        boolean isLoggedIn = mAuth.getCurrentUser() != null;

        // שליטה בנראות לפי מצב התחברות
        menu.findItem(R.id.menu_login).setVisible(!isLoggedIn);
        menu.findItem(R.id.menu_register).setVisible(!isLoggedIn);
        menu.findItem(R.id.menu_logout).setVisible(isLoggedIn);

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        if (id == R.id.menu_login) {
            startActivity(new Intent(this, LoginActivity.class));
            return true;
        }
        else if (id == R.id.menu_register) {
            startActivity(new Intent(this, RegisterActivity.class));
            return true;
        }
        else if (id == R.id.menu_logout) {
            mAuth.signOut();
            recreate(); // רענון המסך לעדכון הממשק
            return true;
        }
        else if (id == R.id.leaderboard) {
            Intent intent = new Intent(this, LeaderboardActivity.class);
            intent.putExtra("currentUser", getPlayerName());
            startActivity(intent);
            return true;
        }
        else if (id == R.id.menu_settings) {
            Toast.makeText(this, "Settings clicked", Toast.LENGTH_SHORT).show();
            return true;
        }
        else if (id == R.id.action_music) {
            item.setChecked(!item.isChecked());
            if (item.isChecked()) {
                Toast.makeText(this, "Music: ON", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Music: OFF", Toast.LENGTH_SHORT).show();
            }
            return true;
        }

        return super.onOptionsItemSelected(item);
    }
}