package com.example.minesweeper;

import android.content.Intent;
import android.os.Bundle;
import android.widget.ImageButton;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.List;

public class LeaderboardActivity extends AppCompatActivity {

    private RecyclerView recyclerLeaderboard;
    private LeaderboardAdapter adapter;
    private DatabaseReference leaderboardRef;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_leaderboard);

        recyclerLeaderboard = findViewById(R.id.recyclerLeaderboard);
        recyclerLeaderboard.setLayoutManager(new LinearLayoutManager(this));

        adapter = new LeaderboardAdapter();
        recyclerLeaderboard.setAdapter(adapter);

        leaderboardRef = FirebaseDatabase.getInstance().getReference("leaderboard");
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

        leaderboardRef
                .orderByChild("time")
                .limitToFirst(10)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(DataSnapshot snapshot) {

                        List<Score> scores = new ArrayList<>();

                        for (DataSnapshot doc : snapshot.getChildren()) {

                            String player = doc.child("player").getValue(String.class);
                            Long time = doc.child("time").getValue(Long.class);
                            Long difficulty = doc.child("difficulty").getValue(Long.class);

                            if (player != null && time != null && difficulty != null) {
                                scores.add(new Score(
                                        player,
                                        time.intValue(),
                                        difficulty.intValue()
                                ));
                            }
                        }

                        adapter.setScores(scores);
                    }

                    @Override
                    public void onCancelled(DatabaseError error) {
                        Toast.makeText(LeaderboardActivity.this,
                                "Failed to load leaderboard",
                                Toast.LENGTH_SHORT).show();
                    }
                });
    }
}
