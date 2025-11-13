package com.example.minesweeper;

public class Score {
    private String player;
    private int time;
    private int difficulty;

    public Score() {} // Needed for Firebase

    public Score(String player, int time, int difficulty) {
        this.player = player;
        this.time = time;
        this.difficulty = difficulty;
    }

    public String getPlayer() { return player; }
    public void setPlayer(String player) { this.player = player; }

    public int getTime() { return time; }
    public void setTime(int time) { this.time = time; }

    public int getDifficulty() { return difficulty; }
    public void setDifficulty(int difficulty) { this.difficulty = difficulty; }
}
