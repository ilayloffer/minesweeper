package com.example.minesweeper;

import android.os.Handler;
import android.os.Looper;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import androidx.annotation.NonNull;
import java.util.Random;

public class OfflineGameController implements GameController {
    private final GameView view;
    private final int size;
    private final String currentUser;
    private Cell[][] board;
    private boolean isGameOver = false;
    private int revealedCount = 0;
    private int totalMines = 0;
    private int secondsElapsed = 0;
    private Handler timerHandler = new Handler(Looper.getMainLooper());
    private Runnable timerRunnable;

    public OfflineGameController(GameView view, int size, String currentUser) {
        this.view = view;
        this.size = size;
        this.currentUser = currentUser;
        this.board = new Cell[size][size];
        initBoard();
        startTimer();
    }

    private void initBoard() {
        Random r = new Random();
        boolean[][] mines = new boolean[size][size];
        totalMines = 0;
        for (int i = 0; i < size; i++) {
            for (int j = 0; j < size; j++) {
                mines[i][j] = r.nextInt(100) < 15;
                if (mines[i][j]) totalMines++;
            }
        }

        // סינכרון כמות המוקשים שהוגרלו מול ה-Activity
        if (view instanceof GameActivity) {
            ((GameActivity) view).setDynamicBombsCount(totalMines);
        }

        for (int i = 0; i < size; i++) {
            for (int j = 0; j < size; j++) {
                int count = 0;
                for (int ni = -1; ni <= 1; ni++) {
                    for (int nj = -1; nj <= 1; nj++) {
                        int r2 = i + ni, c2 = j + nj;
                        if (r2 >= 0 && r2 < size && c2 >= 0 && c2 < size && mines[r2][c2]) count++;
                    }
                }
                board[i][j] = new Cell(mines[i][j], count);
            }
        }
    }

    private void startTimer() {
        timerRunnable = new Runnable() {
            @Override public void run() {
                if (!isGameOver) {
                    secondsElapsed++;
                    view.updateStatus("Time: " + secondsElapsed + "s");
                    timerHandler.postDelayed(this, 1000);
                }
            }
        };
        timerHandler.postDelayed(timerRunnable, 1000);
    }

    public String getBoardData() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < size; i++) {
            for (int j = 0; j < size; j++) {
                Cell c = board[i][j];
                sb.append(c.getHasMine() ? "1" : "0").append(",")
                        .append(c.isRevealed() ? "1" : "0").append(",")
                        .append(c.isFlagged() ? "1" : "0").append(",")
                        .append(c.getNeighborMines()).append(";");
            }
        }
        return sb.toString();
    }

    public void loadExistingGame(String data, int savedSeconds) {
        this.secondsElapsed = savedSeconds;
        String[] cells = data.split(";");
        int idx = 0; revealedCount = 0;
        for (int i = 0; i < size; i++) {
            for (int j = 0; j < size; j++) {
                String[] p = cells[idx++].split(",");
                board[i][j] = new Cell(p[0].equals("1"), Integer.parseInt(p[3]));
                board[i][j].setRevealed(p[1].equals("1"));
                board[i][j].setFlagged(p[2].equals("1"));
                if (board[i][j].isRevealed()) revealedCount++;
                view.updateCell(i, j, board[i][j]);
            }
        }
    }

    public int getSecondsElapsed() { return secondsElapsed; }

    @Override
    public void onCellClicked(int r, int c) {
        if (isGameOver || board[r][c].isRevealed() || board[r][c].isFlagged()) return;
        if (board[r][c].getHasMine()) { isGameOver = true; view.showGameOver(false); return; }
        reveal(r, c);

        if (revealedCount == (size * size) - totalMines) {
            isGameOver = true;
            view.showGameOver(true);

            // עדכון הלידרבורד של האופליין (wins)
            if (currentUser != null && !currentUser.isEmpty() && !currentUser.equals("Guest")) {
                DatabaseReference userRef = FirebaseDatabase.getInstance().getReference("leaderboard").child(currentUser);
                userRef.child("wins").addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        Long currentWins = snapshot.getValue(Long.class);
                        if (currentWins == null) currentWins = 0L;
                        userRef.child("wins").setValue(currentWins + 1);
                        userRef.child("username").setValue(currentUser);
                    }
                    @Override public void onCancelled(@NonNull DatabaseError error) {}
                });
            }
        }
    }

    private void reveal(int r, int c) {
        if (r < 0 || r >= size || c < 0 || c >= size || board[r][c].isRevealed()) return;
        board[r][c].setRevealed(true);
        revealedCount++;
        view.updateCell(r, c, board[r][c]);
        if (board[r][c].getNeighborMines() == 0) {
            for (int i = -1; i <= 1; i++) for (int j = -1; j <= 1; j++) reveal(r + i, c + j);
        }
    }

    @Override
    public void onCellLongClicked(int r, int c) {
        if (isGameOver || board[r][c].isRevealed()) return;
        board[r][c].setFlagged(!board[r][c].isFlagged());
        view.updateCell(r, c, board[r][c]);
    }

    @Override public void onDestroy() { isGameOver = true; timerHandler.removeCallbacks(timerRunnable); }
}