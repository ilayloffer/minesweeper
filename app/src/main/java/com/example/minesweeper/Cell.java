package com.example.minesweeper;

public class Cell {
    private boolean hasMine;
    private int neighborMines;
    private boolean isRevealed;

    // Empty constructor required for Firestore
    public Cell() {}

    public Cell(boolean hasMine, int neighborMines) {
        this.hasMine = hasMine;
        this.neighborMines = neighborMines;
        this.isRevealed = false;
    }

    // Getters and Setters are required for Firestore
    public boolean isHasMine() { return hasMine; }
    public void setHasMine(boolean hasMine) { this.hasMine = hasMine; }

    public int getNeighborMines() { return neighborMines; }
    public void setNeighborMines(int neighborMines) { this.neighborMines = neighborMines; }

    public boolean isRevealed() { return isRevealed; }
    public void setRevealed(boolean revealed) { isRevealed = revealed; }
}