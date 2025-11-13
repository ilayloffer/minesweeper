package com.example.minesweeper;

public class Cell {
    public boolean hasMine;
    public boolean revealed;
    public int adjacentMines;

    public Cell() {} // Needed for Firebase

    public Cell(boolean hasMine, int adjacentMines) {
        this.hasMine = hasMine;
        this.revealed = false;
        this.adjacentMines = adjacentMines;
    }
}
