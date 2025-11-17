package com.example.minesweeper;

import android.content.Intent;
import android.os.Bundle;
import android.widget.ImageButton;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.firestore.*;

import java.util.ArrayList;
import java.util.List;

public class LeaderboardActivity extends AppCompatActivity {

    private RecyclerView recyclerLeaderboard;
    private LeaderboardAdapter adapter;
    private FirebaseFirestore firestore;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_leaderboard);

        recyclerLeaderboard = findViewById(R.id.recyclerLeaderboard);
        recyclerLeaderboard.setLayoutManager(new LinearLayoutManager(this));

        adapter = new LeaderboardAdapter();
        recyclerLeaderboard.setAdapter(adapter);

        firestore = FirebaseFirestore.getInstance();
        loadLeaderboard();

        // Add this inside onCreate()
        ImageButton backBtn = findViewById(R.id.backBtn);
        backBtn.setOnClickListener(v -> {
            // Return to MainActivity
            Intent i = new Intent(this, MainActivity.class);
            startActivity(i);
            finish();
        });
    }

    private void loadLeaderboard() {
        firestore.collection("leaderboard")
                .orderBy("time", Query.Direction.ASCENDING)
                .limit(10)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    List<Score> scores = new ArrayList<>();
                    for (DocumentSnapshot doc : queryDocumentSnapshots) {
                        String player = doc.getString("player");
                        Long time = doc.getLong("time");
                        Long difficulty = doc.getLong("difficulty");
                        if (player != null && time != null && difficulty != null) {
                            scores.add(new Score(player, time.intValue(), difficulty.intValue()));
                        }
                    }
                    adapter.setScores(scores);
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Failed to load leaderboard", Toast.LENGTH_SHORT).show());
    }
}
