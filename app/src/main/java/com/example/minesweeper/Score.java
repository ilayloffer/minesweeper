package com.example.minesweeper;

// הוסף את הייבוא (Imports) הבאים למעלה:
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

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

    /**
     * פעולה לשמירת התוצאה הנוכחית בטבלת ה-Leaderboard ב-Firebase
     */
    public void saveToLeaderboard() {
        // קבלת רפרנס לענף "leaderboard" במסד הנתונים
        DatabaseReference database = FirebaseDatabase.getInstance().getReference("leaderboard");

        // יצירת מפתח (ID) ייחודי עבור התוצאה החדשה
        String scoreId = database.push().getKey();

        // שמירת אובייקט ה-Score הנוכחי תחת המפתח שנוצר
        if (scoreId != null) {
            database.child(scoreId).setValue(this);
        }
    }
}