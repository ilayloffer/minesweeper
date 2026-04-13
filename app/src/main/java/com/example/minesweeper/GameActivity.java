package com.example.minesweeper;

import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

public class GameActivity extends AppCompatActivity implements GameView {

    private LinearLayout boardContainer;
    private TextView statusText;
    private FrameLayout overlay;
    private TextView overlayTitle;

    private GameController controller;
    private Button[][] buttons;
    private int size;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_game);

        boardContainer = findViewById(R.id.boardContainer);
        statusText = findViewById(R.id.statusText); // Use timerText's ID here if needed
        overlay = findViewById(R.id.overlay);
        overlayTitle = findViewById(R.id.overlayTitle);

        // קריאת נתונים שהועברו מהמסך הראשי
        boolean isOnline = getIntent().getBooleanExtra("isOnline", false);
        size = getIntent().getIntExtra("size", 8);

        buildUi();

        if (isOnline) {
            String gameId = getIntent().getStringExtra("gameId");
            String user = getIntent().getStringExtra("currentUser");
            String other = getIntent().getStringExtra("otherPlayer");
            // ברירת מחדל אם לא סופק
            if(gameId == null) gameId = "testGame";
            if(user == null) user = "player1";
            if(other == null) other = "player2";

            controller = new OnlineGameController(this, size, gameId, user, other);
        } else {
            controller = new LocalGameController(this, size);
        }

        // כפתורים במסך (אם יש לך)
        View btnHome = findViewById(R.id.btnHome);
        if (btnHome != null) btnHome.setOnClickListener(v -> finish());

        View resetBtn = findViewById(R.id.resetBtn);
        if (resetBtn != null) resetBtn.setOnClickListener(v -> recreate());

        View btnNewGame = findViewById(R.id.btnNewGame);
        if (btnNewGame != null) btnNewGame.setOnClickListener(v -> recreate());
    }

    private void buildUi() {
        boardContainer.removeAllViews();
        buttons = new Button[size][size];

        for (int i = 0; i < size; i++) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setLayoutParams(new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT));

            for (int j = 0; j < size; j++) {
                final int r = i, c = j;
                Button btn = new Button(this);
                btn.setText("");
                btn.setTextSize(14f);
                btn.setPadding(0,0,0,0);

                btn.setOnClickListener(v -> controller.onCellClicked(r, c));
                btn.setOnLongClickListener(v -> {
                    controller.onCellLongClicked(r, c);
                    return true;
                });

                buttons[i][j] = btn;

                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                        0, 120, 1f);
                params.setMargins(2, 2, 2, 2);
                row.addView(btn, params);
            }
            boardContainer.addView(row);
        }
    }

    // --- יישום GameView ---

    @Override
    public void updateCell(int r, int c, Cell cell) {
        Button btn = buttons[r][c];

        if (cell.isRevealed()) {
            btn.setEnabled(false);
            if (cell.getHasMine()) {
                btn.setText("💣");
                btn.setBackgroundColor(Color.RED);
            } else {
                btn.setText(cell.getNeighborMines() > 0 ? String.valueOf(cell.getNeighborMines()) : "");
                btn.setBackgroundColor(Color.LTGRAY);

                // צבעי טקסט לפי מספר
                int n = cell.getNeighborMines();
                if (n == 1) btn.setTextColor(Color.BLUE);
                else if (n == 2) btn.setTextColor(Color.parseColor("#006400"));
                else if (n == 3) btn.setTextColor(Color.RED);
                else btn.setTextColor(Color.BLACK);
            }
        } else {
            btn.setText(cell.isFlagged() ? "🚩" : "");
            btn.setEnabled(true);
            btn.setBackgroundColor(Color.parseColor("#DDDDDD"));
        }
    }

    @Override
    public void updateStatus(String text) {
        if(statusText != null) {
            statusText.setText(text);
        }
    }

    @Override
    public void setBoardEnabled(boolean enabled) {
        for (int i = 0; i < size; i++) {
            for (int j = 0; j < size; j++) {
                // אנחנו מאפשרים לחיצה רק אם הלוח פתוח, והתא עדיין לא נחשף
                buttons[i][j].setClickable(enabled);
                buttons[i][j].setLongClickable(enabled);
            }
        }
        boardContainer.setAlpha(enabled ? 1.0f : 0.6f);
    }

    @Override
    public void showGameOver(boolean win) {
        if (overlay != null && overlayTitle != null) {
            overlay.setVisibility(View.VISIBLE);
            overlayTitle.setText(win ? "YOU WIN! 🎉" : "GAME OVER 💀");
            overlayTitle.setTextColor(win ? Color.GREEN : Color.RED);
        } else {
            Toast.makeText(this, win ? "YOU WIN!" : "GAME OVER", Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public void showMessage(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (controller != null) controller.onDestroy();
    }
}