package com.example.minesweeper;

import android.content.SharedPreferences;
import android.graphics.PorterDuff;
import android.net.Uri;
import android.os.Bundle;
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

    private LinearLayout boardContainer;   // LinearLayout
    private FrameLayout overlay;           // FrameLayout
    private TextView overlayTitle;
    private Button resetBtn;

    private int size;
    private boolean[][] mines;
    private int[][] neigh;
    private boolean[][] revealed;
    private int cellsToReveal;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_game);
        Log.d("GameActivity", "onCreate");

        boardContainer = findViewById(R.id.boardContainer);
        overlay = findViewById(R.id.overlay);
        overlayTitle = findViewById(R.id.overlayTitle);
        resetBtn = findViewById(R.id.resetBtn);

        size = Math.max(3, getIntent().getIntExtra("size", 6)); // מינימום 3x3

        // רקע מותאם מה-Settings (אם בחרו תמונה)
        SharedPreferences prefs = getSharedPreferences("MinePrefs", MODE_PRIVATE);
        String bgUri = prefs.getString("bgUri", null);
        if (bgUri != null) {
            try {
                ImageView bg = new ImageView(this);
                bg.setScaleType(ImageView.ScaleType.CENTER_CROP);
                bg.setImageURI(Uri.parse(bgUri));
                addContentView(bg, new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
                bg.setAlpha(0.15f);
            } catch (Exception e) {
                Log.w("GameActivity", "Failed setting bg image: " + e);
            }
        }

        initBoard();
        buildUi(); // דינמי
        resetBtn.setOnClickListener(v -> {
            finish(); // finish הנוכחי וחזרה למסך הראשי
            // לחלופין ניתן להתחיל Activity חדש של המשחק
            // startActivity(new Intent(this, GameActivity.class).putExtra("size", size));
        });
    }

    private void initBoard() {
        mines = new boolean[size][size];
        neigh = new int[size][size];
        revealed = new boolean[size][size];

        int mineCount = Math.max(1, (int) (size * size * 0.18)); // ~18%
        Random r = new Random();
        int placed = 0;
        while (placed < mineCount) {
            int i = r.nextInt(size), j = r.nextInt(size);
            if (!mines[i][j]) {
                mines[i][j] = true;
                placed++;
            }
        }
        // שכנים
        int[] dx = {-1,-1,-1,0,0,1,1,1};
        int[] dy = {-1,0,1,-1,1,-1,0,1};
        for (int i=0;i<size;i++){
            for (int j=0;j<size;j++){
                if (mines[i][j]) continue;
                int c=0;
                for (int k=0;k<8;k++){
                    int ni=i+dx[k], nj=j+dy[k];
                    if (ni>=0 && nj>=0 && ni<size && nj<size && mines[ni][nj]) c++;
                }
                neigh[i][j]=c;
            }
        }
        cellsToReveal = size*size - mineCount;
    }

    private void buildUi() {
        boardContainer.removeAllViews();

        for (int i = 0; i < size; i++) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setLayoutParams(new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            for (int j = 0; j < size; j++) {
                final int x=i, y=j;
                Button cell = new Button(this);
                cell.setText("");
                cell.getBackground().setColorFilter(0xFFE0E0E0, PorterDuff.Mode.MULTIPLY);

                cell.setOnClickListener(v -> { // setOnClickListener + Log
                    Log.d("GameActivity", "Clicked: " + x + "," + y);
                    reveal(x, y, (Button) v);
                });

                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                        0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
                lp.setMargins(4,4,4,4);
                row.addView(cell, lp);
            }
            boardContainer.addView(row);
        }
    }

    private void reveal(int i, int j, Button btn) {
        if (revealed[i][j]) return;
        revealed[i][j] = true;

        if (mines[i][j]) {
            btn.setText("💣");
            showOverlay(false);
            return;
        }
        int n = neigh[i][j];
        btn.setText(n == 0 ? "" : String.valueOf(n));
        btn.setEnabled(false);
        btn.getBackground().setColorFilter(0xFFC8E6C9, PorterDuff.Mode.MULTIPLY);

        cellsToReveal--;
        if (cellsToReveal == 0) {
            showOverlay(true);
        }
        // פתח שכנים ריקים (פשוט)
        if (n == 0) {
            int[] dx = {-1,-1,-1,0,0,1,1,1};
            int[] dy = {-1,0,1,-1,1,-1,0,1};
            for (int k=0;k<8;k++){
                int ni=i+dx[k], nj=j+dy[k];
                if (ni>=0 && nj>=0 && ni<size && nj<size && !revealed[ni][nj]) {
                    // מצא את כפתור השכן
                    LinearLayout row = (LinearLayout) boardContainer.getChildAt(ni);
                    Button neighborBtn = (Button) row.getChildAt(nj);
                    reveal(ni, nj, neighborBtn);
                }
            }
        }
    }

    private void showOverlay(boolean win) {
        overlay.setVisibility(FrameLayout.VISIBLE);
        overlayTitle.setText(win ? "You Win!" : "Game Over");
    }
}
