package com.example.minesweeper;

import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

public class Score {
    private String player;
    private int time;
    private int difficulty;
    private int wins; // <-- הוספנו ניצחונות

    public Score() {} // Needed for Firebase

    public Score(String player, int time, int difficulty, int wins) {
        this.player = player;
        this.time = time;
        this.difficulty = difficulty;
        this.wins = wins;
    }

    public String getPlayer() { return player; }
    public void setPlayer(String player) { this.player = player; }

    public int getTime() { return time; }
    public void setTime(int time) { this.time = time; }

    public int getDifficulty() { return difficulty; }
    public void setDifficulty(int difficulty) { this.difficulty = difficulty; }

    public int getWins() { return wins; }
    public void setWins(int wins) { this.wins = wins; }

    public void saveToLeaderboard() {
        DatabaseReference database = FirebaseDatabase.getInstance().getReference("leaderboard");
        String scoreId = database.push().getKey();
        if (scoreId != null) {
            database.child(scoreId).setValue(this);
        }
    }
}