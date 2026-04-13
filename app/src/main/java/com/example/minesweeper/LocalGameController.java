package com.example.minesweeper;

import java.util.Random;

public class LocalGameController implements GameController {
    private final GameView view;
    private final int size;
    private final Cell[][] board;
    private int safeLeft;
    private boolean isGameOver = false;

    private final int[] dx = {-1, -1, -1, 0, 0, 1, 1, 1};
    private final int[] dy = {-1, 0, 1, -1, 1, -1, 0, 1};

    public LocalGameController(GameView view, int size) {
        this.view = view;
        this.size = size;
        this.board = new Cell[size][size];
        initBoard();
    }

    private void initBoard() {
        int mineCount = Math.max(1, (int) (size * size * 0.15));
        safeLeft = (size * size) - mineCount;

        boolean[][] tempMines = new boolean[size][size];
        Random r = new Random();
        int placed = 0;
        while (placed < mineCount) {
            int i = r.nextInt(size);
            int j = r.nextInt(size);
            if (!tempMines[i][j]) {
                tempMines[i][j] = true;
                placed++;
            }
        }

        for (int i = 0; i < size; i++) {
            for (int j = 0; j < size; j++) {
                int count = 0;
                if (!tempMines[i][j]) {
                    for (int k = 0; k < 8; k++) {
                        int ni = i + dx[k];
                        int nj = j + dy[k];
                        if (ni >= 0 && nj >= 0 && ni < size && nj < size && tempMines[ni][nj]) {
                            count++;
                        }
                    }
                }
                board[i][j] = new Cell(tempMines[i][j], count);
            }
        }
        view.updateStatus("Left: " + safeLeft);
        view.setBoardEnabled(true);
    }

    @Override
    public void onCellClicked(int r, int c) {
        if (isGameOver || board[r][c].isRevealed() || board[r][c].isFlagged()) return;
        revealCell(r, c);
    }

    private void revealCell(int r, int c) {
        if (board[r][c].isRevealed()) return;

        board[r][c].setRevealed(true);
        view.updateCell(r, c, board[r][c]);

        if (board[r][c].getHasMine()) {
            isGameOver = true;
            view.showGameOver(false);
            return;
        }

        safeLeft--;
        view.updateStatus("Left: " + safeLeft);

        if (safeLeft == 0) {
            isGameOver = true;
            view.showGameOver(true);
            return;
        }

        if (board[r][c].getNeighborMines() == 0) {
            for (int k = 0; k < 8; k++) {
                int ni = r + dx[k];
                int nj = c + dy[k];
                if (ni >= 0 && nj >= 0 && ni < size && nj < size && !board[ni][nj].isFlagged()) {
                    revealCell(ni, nj);
                }
            }
        }
    }

    @Override
    public void onCellLongClicked(int r, int c) {
        if (isGameOver || board[r][c].isRevealed()) return;
        board[r][c].setFlagged(!board[r][c].isFlagged());
        view.updateCell(r, c, board[r][c]);
    }

    @Override
    public void onDestroy() {}
}