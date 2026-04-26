package com.example.minesweeper;

import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

public class Score {
    private String playerName;
    private int onlineWins = 0;
    private int offlineWins = 0;
    private int bestOfflineTime = 0;

    // קונסטרקטור ריק - חובה בשביל פיירבייס
    public Score() {}

    public Score(String playerName, int onlineWins, int offlineWins, int bestOfflineTime) {
        this.playerName = playerName;
        this.onlineWins = onlineWins;
        this.offlineWins = offlineWins;
        this.bestOfflineTime = bestOfflineTime;
    }

    public String getPlayerName() { return playerName; }
    public void setPlayerName(String playerName) { this.playerName = playerName; }

    public int getOnlineWins() { return onlineWins; }
    public void setOnlineWins(int onlineWins) { this.onlineWins = onlineWins; }

    public int getOfflineWins() { return offlineWins; }
    public void setOfflineWins(int offlineWins) { this.offlineWins = offlineWins; }

    public int getBestOfflineTime() { return bestOfflineTime; }
    public void setBestOfflineTime(int bestOfflineTime) { this.bestOfflineTime = bestOfflineTime; }

    // פונקציית שמירה מעודכנת ששומרת תחת שם השחקן (ולא תחת מפתח אקראי)
    public void saveToLeaderboard() {
        if (playerName != null && !playerName.isEmpty()) {
            DatabaseReference database = FirebaseDatabase.getInstance().getReference("leaderboard");
            database.child(playerName).setValue(this);
        }
    }
}