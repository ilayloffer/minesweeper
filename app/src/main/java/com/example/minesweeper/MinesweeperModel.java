package com.example.minesweeper;

import java.util.Random;

public class MinesweeperModel {
    private final int size;
    private final boolean[][] mines;
    private final int[][] neigh;
    private final boolean[][] revealed;
    private final boolean[][] flagged;

    private boolean isGameOver;
    private int safeLeft;

    private final int[] dx = {-1, -1, -1, 0, 0, 1, 1, 1};
    private final int[] dy = {-1, 0, 1, -1, 1, -1, 0, 1};

    public MinesweeperModel(int size) {
        this.size = size;
        mines = new boolean[size][size];
        neigh = new int[size][size];
        revealed = new boolean[size][size];
        flagged = new boolean[size][size];
        isGameOver = false;

        initBoard();
    }

    private void initBoard() {
        int mineCount = Math.max(1, (int) (size * size * 0.15));
        safeLeft = (size * size) - mineCount;

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
        for (int i = 0; i < size; i++) {
            for (int j = 0; j < size; j++) {
                if (mines[i][j]) continue;
                int count = 0;
                for (int k = 0; k < 8; k++) {
                    int ni = i + dx[k];
                    int nj = j + dy[k];
                    if (isValidPosition(ni, nj) && mines[ni][nj]) {
                        count++;
                    }
                }
                neigh[i][j] = count;
            }
        }
    }

    private boolean isValidPosition(int x, int y) {
        return x >= 0 && y >= 0 && x < size && y < size;
    }

    // Getters for Controller
    public boolean isGameOver() { return isGameOver; }
    public boolean isRevealed(int x, int y) { return revealed[x][y]; }
    public boolean isFlagged(int x, int y) { return flagged[x][y]; }
    public boolean isMine(int x, int y) { return mines[x][y]; }
    public int getNeighbors(int x, int y) { return neigh[x][y]; }
    public int getSafeLeft() { return safeLeft; }

    // Actions
    public void toggleFlag(int x, int y) {
        if (revealed[x][y] || isGameOver) return;
        flagged[x][y] = !flagged[x][y];
    }

    public void setRevealed(int x, int y) {
        revealed[x][y] = true;
    }

    public void decrementSafeLeft() {
        safeLeft--;
    }

    public void setGameOver(boolean state) {
        isGameOver = state;
    }
}