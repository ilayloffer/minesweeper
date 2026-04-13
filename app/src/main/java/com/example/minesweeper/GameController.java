package com.example.minesweeper;

public interface GameController {
    void onCellClicked(int r, int c);
    void onCellLongClicked(int r, int c);
    void onDestroy();
}