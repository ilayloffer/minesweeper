package com.example.minesweeper;

import java.util.Random;

public class OfflineGameController implements GameController {
    private final GameView view;
    private final int size;
    private final Cell[][] board;
    private boolean isGameOver = false;
    private int totalSafeCells;
    private int revealedCells = 0;

    private final int[] dx = {-1, -1, -1, 0, 0, 1, 1, 1};
    private final int[] dy = {-1, 0, 1, -1, 1, -1, 0, 1};

    public OfflineGameController(GameView view, int size) {
        this.view = view;
        this.size = size;
        this.board = new Cell[size][size];

        initBoard();

        // מעדכן את הטקסט למעלה שהמשחק התחיל
        view.updateStatus("Offline Game - Good Luck!");
        view.setBoardEnabled(true);
    }

    private void initBoard() {
        boolean[][] tempMines = new boolean[size][size];
        Random r = new Random();
        int minesCount = 0;

        // פיזור מוקשים אקראי (15% סיכוי למוקש)
        for (int i = 0; i < size; i++) {
            for (int j = 0; j < size; j++) {
                boolean hasMine = r.nextInt(100) < 15;
                tempMines[i][j] = hasMine;
                if (hasMine) minesCount++;
            }
        }

        totalSafeCells = (size * size) - minesCount;

        // ספירת השכנים לכל משבצת
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
                view.updateCell(i, j, board[i][j]);
            }
        }
    }

    @Override
    public void onCellClicked(int r, int c) {
        if (isGameOver || board[r][c].isRevealed() || board[r][c].isFlagged()) return;

        revealCell(r, c);

        // בדיקת הפסד / ניצחון
        if (board[r][c].getHasMine()) {
            isGameOver = true;
            view.showGameOver(false);
            view.updateStatus("Game Over! You hit a mine.");
        } else if (revealedCells == totalSafeCells) {
            isGameOver = true;
            view.showGameOver(true);
            view.updateStatus("You Won!");
        }
    }

    private void revealCell(int r, int c) {
        // תנאי עצירה לרקורסיה (גבולות הלוח או משבצת שכבר נפתחה/דוגלה)
        if (r < 0 || c < 0 || r >= size || c >= size) return;
        Cell cell = board[r][c];
        if (cell.isRevealed() || cell.isFlagged()) return;

        // חשיפת המשבצת
        cell.setRevealed(true);
        revealedCells++;
        view.updateCell(r, c, cell);

        // אם המשבצת ריקה (0 שכנים), נפתח אוטומטית גם את כל השכנים שלה!
        if (cell.getNeighborMines() == 0 && !cell.getHasMine()) {
            for (int k = 0; k < 8; k++) {
                revealCell(r + dx[k], c + dy[k]);
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

    @Override
    public void onDestroy() {
        // אין צורך לנקות חיבורי רשת באופליין
    }
}