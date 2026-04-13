package com.example.minesweeper;

public class Cell {
    private boolean hasMine;
    private int neighborMines;
    private boolean isRevealed;
    private boolean isFlagged;

    public Cell() {} // חובה עבור Firebase

    public Cell(boolean hasMine, int neighborMines) {
        this.hasMine = hasMine;
        this.neighborMines = neighborMines;
        this.isRevealed = false;
        this.isFlagged = false;
    }

    public boolean getHasMine() { return hasMine; }
    public void setHasMine(boolean hasMine) { this.hasMine = hasMine; }

    public int getNeighborMines() { return neighborMines; }
    public void setNeighborMines(int neighborMines) { this.neighborMines = neighborMines; }

    public boolean isRevealed() { return isRevealed; }
    public void setRevealed(boolean revealed) { isRevealed = revealed; }

    public boolean isFlagged() { return isFlagged; }
    public void setFlagged(boolean flagged) { isFlagged = flagged; }
}