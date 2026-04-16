package com.example.minesweeper;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

public class GameActivity extends AppCompatActivity implements GameView {

    private TextView statusText;
    private LinearLayout boardContainer;
    private View overlay;
    private TextView overlayTitle;
    private Button btnHome;

    private GameController controller;
    private Button[][] buttons;
    private int size;
    private boolean isOnline;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_game);

        // חיבור רכיבי ה-UI מה-XML
        statusText = findViewById(R.id.statusText);
        boardContainer = findViewById(R.id.boardContainer);
        overlay = findViewById(R.id.overlay);
        overlayTitle = findViewById(R.id.overlayTitle);
        btnHome = findViewById(R.id.btnHome);

        // קבלת נתונים מה-MainActivity
        Intent intent = getIntent();
        size = intent.getIntExtra("size", 10);
        isOnline = intent.getBooleanExtra("isOnline", false);

        createBoardUI();

        // אתחול הקונטרולר המתאים
// אתחול הקונטרולר המתאים
        if (isOnline) {
            String gameId = intent.getStringExtra("gameId");
            String currentUser = intent.getStringExtra("currentUser");
            String otherPlayer = intent.getStringExtra("otherPlayer");
            controller = new OnlineGameController(this, size, gameId, currentUser, otherPlayer);
        } else {
            controller = new OfflineGameController(this, size);
        }

        // כפתור חזרה לתפריט הראשי בסיום המשחק
        btnHome.setOnClickListener(v -> finish());
    }

    private void createBoardUI() {
        buttons = new Button[size][size];
        boardContainer.removeAllViews();

        for (int i = 0; i < size; i++) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            for (int j = 0; j < size; j++) {
                Button btn = new Button(this);
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(100, 100);
                params.setMargins(2, 2, 2, 2);
                btn.setLayoutParams(params);
                btn.setBackgroundColor(Color.LTGRAY);

                final int r = i;
                final int c = j;

                btn.setOnClickListener(v -> {
                    if (controller != null) controller.onCellClicked(r, c);
                });

                btn.setOnLongClickListener(v -> {
                    if (controller != null) controller.onCellLongClicked(r, c);
                    return true;
                });

                buttons[i][j] = btn;
                row.addView(btn);
            }
            boardContainer.addView(row);
        }
    }

    // --- מימוש פונקציות ה-GameView ---

    @Override
    public void updateStatus(String status) {
        // פונקציה זו קריטית! היא מעדכנת את הטיימר שמופיע במסך
        runOnUiThread(() -> statusText.setText(status));
    }

    @Override
    public void updateCell(int r, int c, Cell cell) {
        runOnUiThread(() -> {
            Button btn = buttons[r][c];
            if (cell.isRevealed()) {
                btn.setEnabled(false);
                if (cell.getHasMine()) {
                    btn.setText("💣");
                    btn.setBackgroundColor(Color.RED);
                } else {
                    btn.setBackgroundColor(Color.WHITE);
                    int neighbors = cell.getNeighborMines();
                    if (neighbors > 0) {
                        btn.setText(String.valueOf(neighbors));
                    } else {
                        btn.setText("");
                    }
                }
            } else if (cell.isFlagged()) {
                btn.setText("🚩");
            } else {
                btn.setText("");
                btn.setEnabled(true);
            }
        });
    }

    @Override
    public void setBoardEnabled(boolean enabled) {
        // מונע או מאפשר לחיצה על הלוח בהתאם לתור
        runOnUiThread(() -> boardContainer.setAlpha(enabled ? 1.0f : 0.5f));
    }

    @Override
    public void showGameOver(boolean didIWin) {
        runOnUiThread(() -> {
            overlayTitle.setText(didIWin ? "YOU WIN! 🎉" : "YOU LOSE! 💥");
            overlayTitle.setTextColor(didIWin ? Color.GREEN : Color.RED);
            overlay.setVisibility(View.VISIBLE);
        });
    }

    @Override
    public void showMessage(String msg) {
        runOnUiThread(() -> Toast.makeText(GameActivity.this, msg, Toast.LENGTH_SHORT).show());
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (controller != null) {
            controller.onDestroy();
        }
    }
}