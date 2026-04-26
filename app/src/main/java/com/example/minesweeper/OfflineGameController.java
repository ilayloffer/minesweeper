package com.example.minesweeper;

import android.os.Handler;
import android.os.Looper;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.Random;

public class OfflineGameController implements GameController {
    private final GameView view;
    private final int size;
    private final String currentUser; // נשמור את שם השחקן ללידרבורד
    private Cell[][] board;
    private boolean isGameOver = false;
    private int revealedCount = 0;
    private int totalMines = 0;

    // משתני טיימר לאופליין
    private int secondsElapsed = 0;
    private Handler timerHandler = new Handler(Looper.getMainLooper());
    private Runnable timerRunnable;

    public OfflineGameController(GameView view, int size, String currentUser) {
        this.view = view;
        this.size = size;
        this.currentUser = currentUser;
        this.board = new Cell[size][size];
        initBoard();
        startTimer(); // הפעלת השעון
        view.setBoardEnabled(true);
    }

    private void initBoard() {
        Random r = new Random();
        boolean[][] tempMines = new boolean[size][size];
        totalMines = 0;

        for (int i = 0; i < size; i++) {
            for (int j = 0; j < size; j++) {
                boolean isMine = r.nextInt(100) < 15;
                tempMines[i][j] = isMine;
                if (isMine) totalMines++;
            }
        }

        int[] dx = {-1, -1, -1, 0, 0, 1, 1, 1};
        int[] dy = {-1, 0, 1, -1, 1, -1, 0, 1};

        for (int i = 0; i < size; i++) {
            for (int j = 0; j < size; j++) {
                int count = 0;
                for (int k = 0; k < 8; k++) {
                    int ni = i + dx[k];
                    int nj = j + dy[k];
                    if (ni >= 0 && nj >= 0 && ni < size && nj < size && tempMines[ni][nj]) {
                        count++;
                    }
                }
                board[i][j] = new Cell(tempMines[i][j], count);
            }
        }
    }

    private void startTimer() {
        timerRunnable = new Runnable() {
            @Override
            public void run() {
                if (!isGameOver) {
                    secondsElapsed++;
                    view.updateStatus("Time: " + secondsElapsed + "s");
                    timerHandler.postDelayed(this, 1000); // קורא לעצמו כל שנייה
                }
            }
        };
        timerHandler.postDelayed(timerRunnable, 1000);
    }

    @Override
    public void onCellClicked(int r, int c) {
        if (isGameOver || board[r][c].isRevealed() || board[r][c].isFlagged()) return;

        if (board[r][c].getHasMine()) {
            endGame(false);
            return;
        }

        floodFill(r, c);
        checkWin();
    }

    private void floodFill(int r, int c) {
        if (r < 0 || c < 0 || r >= size || c >= size) return;
        Cell cell = board[r][c];
        if (cell.isRevealed() || cell.isFlagged()) return;

        cell.setRevealed(true);
        revealedCount++;
        view.updateCell(r, c, cell);

        if (cell.getNeighborMines() == 0 && !cell.getHasMine()) {
            int[] dx = {-1, -1, -1, 0, 0, 1, 1, 1};
            int[] dy = {-1, 0, 1, -1, 1, -1, 0, 1};
            for (int k = 0; k < 8; k++) {
                floodFill(r + dx[k], c + dy[k]);
            }
        }
    }

    @Override
    public void onCellLongClicked(int r, int c) {
        if (isGameOver || board[r][c].isRevealed()) return;

        Cell cell = board[r][c];
        cell.setFlagged(!cell.isFlagged());
        view.updateCell(r, c, cell);
    }

    private void checkWin() {
        if (revealedCount == (size * size) - totalMines) {
            endGame(true);
        }
    }

    private void endGame(boolean didIWin) {
        isGameOver = true;
        timerHandler.removeCallbacks(timerRunnable); // עוצר את הטיימר

        if (didIWin) {
            view.updateStatus("You Won! Time: " + secondsElapsed + "s");
            saveOfflineStatsToLeaderboard(secondsElapsed);
        } else {
            // אם הפסדנו, נחשוף את כל המוקשים כדי שהשחקן יראה איפה הם היו
            for (int i = 0; i < size; i++) {
                for (int j = 0; j < size; j++) {
                    if (board[i][j].getHasMine()) {
                        board[i][j].setRevealed(true);
                        view.updateCell(i, j, board[i][j]);
                    }
                }
            }
        }
        view.showGameOver(didIWin);
    }

    private void saveOfflineStatsToLeaderboard(int time) {
        if (currentUser == null || currentUser.isEmpty()) return;

        DatabaseReference userRef = FirebaseDatabase.getInstance().getReference("leaderboard").child(currentUser);

        userRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                // 1. עדכון מספר ניצחונות אופליין
                Long offlineWins = snapshot.child("offlineWins").getValue(Long.class);
                if (offlineWins == null) offlineWins = 0L;
                userRef.child("offlineWins").setValue(offlineWins + 1);

                // 2. עדכון זמן שיא (אם זה הזמן הראשון או זמן מהיר יותר)
                Long bestTime = snapshot.child("bestOfflineTime").getValue(Long.class);
                if (bestTime == null || time < bestTime) {
                    userRef.child("bestOfflineTime").setValue(time);
                }
            }

            @Override
            public void onCancelled(DatabaseError error) {}
        });
    }

    @Override
    public void onDestroy() {
        // פונקציה סופר חשובה! היא מונעת את המסך השחור והקריסות בסיום המשחק
        isGameOver = true;
        if (timerHandler != null) {
            timerHandler.removeCallbacks(timerRunnable);
        }
    }
}