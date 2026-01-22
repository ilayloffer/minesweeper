package com.example.minesweeper;

import android.annotation.SuppressLint;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.net.Uri;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.util.Log;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import java.util.Random;

public class GameActivity extends AppCompatActivity {

    // UI Elements
    private LinearLayout boardContainer;
    private FrameLayout overlay;
    private TextView overlayTitle;
    private TextView timerText;

    // Buttons
    private Button resetBtn;    // Top bar reset
    private Button btnNewGame;  // Overlay New Game
    private Button btnHome;     // Overlay Back Home

    // Timer Variables
    private CountDownTimer timer;
    private boolean timerStarted = false;
    private int elapsed = 0;

    // Game Data
    private int size;
    private boolean[][] mines;
    private int[][] neigh;
    private boolean[][] revealed;
    private boolean isGameOver = false;

    @SuppressLint("MissingInflatedId")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_game);

        // 1. Link UI Components
        boardContainer = findViewById(R.id.boardContainer);
        overlay = findViewById(R.id.overlay);
        overlayTitle = findViewById(R.id.overlayTitle);
        timerText = findViewById(R.id.timerText);

        resetBtn = findViewById(R.id.resetBtn);
        btnNewGame = findViewById(R.id.btnNewGame);
        btnHome = findViewById(R.id.btnHome);

        timerText.setText("00:00");

        // 2. Get Game Size
        size = Math.max(3, getIntent().getIntExtra("size", 8));

        // 3. Optional: Background Image
        SharedPreferences prefs = getSharedPreferences("MinePrefs", MODE_PRIVATE);
        String bgUri = prefs.getString("bgUri", null);
        if (bgUri != null) {
            try {
                ImageView bg = new ImageView(this);
                bg.setScaleType(ImageView.ScaleType.CENTER_CROP);
                bg.setImageURI(Uri.parse(bgUri));
                bg.setAlpha(0.15f);
                addContentView(bg, new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            } catch (Exception e) {
                Log.e("GameActivity", "Error loading bg", e);
            }
        }

        // 4. Start Game Logic
        initBoard();
        buildUi();

        // 5. Button Listeners

        // Top Reset Button -> Restart immediately
        resetBtn.setOnClickListener(v -> recreate());

        // Overlay: New Game -> Restart immediately
        btnNewGame.setOnClickListener(v -> recreate());

        // Overlay: Back Home -> Close Activity
        btnHome.setOnClickListener(v -> finish());
    }

    // --- INITIALIZATION ---

    private void initBoard() {
        mines = new boolean[size][size];
        neigh = new int[size][size];
        revealed = new boolean[size][size];
        isGameOver = false;

        // Place Mines (approx 15% density)
        int mineCount = Math.max(1, (int) (size * size * 0.15));
        Random r = new Random();
        int placed = 0;
        while (placed < mineCount) {
            int i = r.nextInt(size);
            int j = r.nextInt(size);
            if (!mines[i][j]) {
                mines[i][j] = true;
                placed++;
            }
        }

        // Calculate Neighbors
        int[] dx = {-1, -1, -1, 0, 0, 1, 1, 1};
        int[] dy = {-1, 0, 1, -1, 1, -1, 0, 1};

        for (int i = 0; i < size; i++) {
            for (int j = 0; j < size; j++) {
                if (mines[i][j]) continue;
                int count = 0;
                for (int k = 0; k < 8; k++) {
                    int ni = i + dx[k];
                    int nj = j + dy[k];
                    if (ni >= 0 && nj >= 0 && ni < size && nj < size && mines[ni][nj]) {
                        count++;
                    }
                }
                neigh[i][j] = count;
            }
        }
    }

    private void buildUi() {
        boardContainer.removeAllViews();

        for (int i = 0; i < size; i++) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setLayoutParams(new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

            for (int j = 0; j < size; j++) {
                final int x = i, y = j;
                Button cell = new Button(this);
                cell.setText("");
                cell.setTextSize(18f);
                cell.getBackground().setColorFilter(0xFFE0E0E0, PorterDuff.Mode.MULTIPLY);

                // --- LONG CLICK: FLAG 🚩 ---
                cell.setOnLongClickListener(v -> {
                    if (revealed[x][y] || isGameOver) return true;

                    String txt = cell.getText().toString();
                    if (txt.equals("🚩")) {
                        cell.setText("");
                    } else {
                        cell.setText("🚩");
                    }
                    return true;
                });

                // --- NORMAL CLICK: REVEAL ---
                cell.setOnClickListener(v -> {
                    if (isGameOver) return;

                    // Safety: Do not click flagged cells
                    if (cell.getText().toString().equals("🚩")) return;

                    if (!timerStarted) startTimer();

                    reveal(x, y, cell);
                });

                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, 140, 1f);
                lp.setMargins(2, 2, 2, 2);
                row.addView(cell, lp);
            }
            boardContainer.addView(row);
        }
    }

    // --- CORE GAME LOGIC ---

    private void reveal(int i, int j, Button btn) {
        if (revealed[i][j]) return;

        revealed[i][j] = true;

        // 1. HIT MINE -> LOSE
        if (mines[i][j]) {
            btn.setText("💣");
            btn.setBackgroundColor(Color.RED);
            gameOver(false);
            return;
        }

        // 2. REVEAL CELL
        int n = neigh[i][j];
        btn.setText(n == 0 ? "" : String.valueOf(n));
        btn.setEnabled(false);
        btn.setBackgroundColor(Color.parseColor("#DDDDDD"));

        if (n == 1) btn.setTextColor(Color.BLUE);
        else if (n == 2) btn.setTextColor(Color.parseColor("#006400")); // Green
        else if (n == 3) btn.setTextColor(Color.RED);
        else btn.setTextColor(Color.BLACK);

        // 3. CHECK WIN
        checkWin();

        // 4. FLOOD FILL
        if (n == 0) {
            int[] dx = {-1, -1, -1, 0, 0, 1, 1, 1};
            int[] dy = {-1, 0, 1, -1, 1, -1, 0, 1};
            for (int k = 0; k < 8; k++) {
                int ni = i + dx[k];
                int nj = j + dy[k];
                if (ni >= 0 && nj >= 0 && ni < size && nj < size && !revealed[ni][nj]) {
                    LinearLayout row = (LinearLayout) boardContainer.getChildAt(ni);
                    Button nextBtn = (Button) row.getChildAt(nj);
                    if (!nextBtn.getText().toString().equals("🚩")) {
                        reveal(ni, nj, nextBtn);
                    }
                }
            }
        }
    }

    /**
     * ROBUST WIN CHECK
     */
    private void checkWin() {
        if (isGameOver) return;

        int safeLeft = 0;
        for (int i = 0; i < size; i++) {
            for (int j = 0; j < size; j++) {
                // Count unrevealed non-mine cells
                if (!mines[i][j] && !revealed[i][j]) {
                    safeLeft++;
                }
            }
        }

        if (timerText != null) {
            String timePart = timerText.getText().toString().split(" \\| ")[0];
            timerText.setText(timePart + " | Left: " + safeLeft);
        }

        if (safeLeft == 0) {
            gameOver(true);
        }
    }

    private void gameOver(boolean win) {
        isGameOver = true;
        stopTimer();

        // Show Overlay
        overlay.setVisibility(android.view.View.VISIBLE);
        overlayTitle.setText(win ? "YOU WIN! 🎉" : "GAME OVER 💀");
        overlayTitle.setTextColor(win ? Color.GREEN : Color.RED);
    }

    // --- TIMER ---

    private void startTimer() {
        timerStarted = true;
        timer = new CountDownTimer(Long.MAX_VALUE, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                elapsed++;
                int m = elapsed / 60;
                int s = elapsed % 60;
                String currentText = timerText.getText().toString();
                String suffix = currentText.contains("|") ? " | " + currentText.split(" \\| ")[1] : "";

                timerText.setText(String.format("%02d:%02d%s", m, s, suffix));
            }
            @Override
            public void onFinish() {}
        }.start();
    }

    private void stopTimer() {
        if (timer != null) timer.cancel();
    }
}