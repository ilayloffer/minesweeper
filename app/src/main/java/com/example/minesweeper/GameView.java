package com.example.minesweeper;

public interface GameView {
    void updateCell(int r, int c, Cell cell);
    void updateStatus(String statusText);
    void setBoardEnabled(boolean enabled);
    void showGameOver(boolean win);
    void showMessage(String message);
}