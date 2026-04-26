package com.example.minesweeper;

import android.os.Bundle;
import android.widget.RadioGroup;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class LeaderboardActivity extends AppCompatActivity {

    private RecyclerView recyclerView;
    private LeaderboardAdapter adapter;
    private List<Score> scoresList;
    private DatabaseReference leaderboardRef;
    private ValueEventListener currentListener; // שומר את המאזין הנוכחי כדי לבטל אותו כשמחליפים מיון

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_leaderboard);

        recyclerView = findViewById(R.id.recyclerViewLeaderboard);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        scoresList = new ArrayList<>();
        adapter = new LeaderboardAdapter(scoresList);
        recyclerView.setAdapter(adapter);

        leaderboardRef = FirebaseDatabase.getInstance().getReference("leaderboard");

        // מאזין לכפתורי הבחירה
        RadioGroup rgSortOptions = findViewById(R.id.rgSortOptions);
        rgSortOptions.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == R.id.rbSortTime) {
                loadDataByTime();
            } else if (checkedId == R.id.rbSortWins) {
                loadDataByWins();
            }
        });

        // טעינה ראשונית - לפי זמן
        loadDataByTime();
    }

    private void loadDataByTime() {
        if (currentListener != null) {
            leaderboardRef.removeEventListener(currentListener);
        }

        // מיון לפי זמן - limitToFirst מביא את הזמנים הכי נמוכים קודם
        currentListener = leaderboardRef.orderByChild("time").limitToFirst(50)
                .addValueEventListener(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        scoresList.clear();
                        for (DataSnapshot doc : snapshot.getChildren()) {
                            Score score = doc.getValue(Score.class);
                            if (score != null) scoresList.add(score);
                        }
                        adapter.notifyDataSetChanged();
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        showError();
                    }
                });
    }

    private void loadDataByWins() {
        if (currentListener != null) {
            leaderboardRef.removeEventListener(currentListener);
        }

        // מיון לפי ניצחונות - limitToLast מביא את הכי גבוהים, אבל בסדר עולה
        currentListener = leaderboardRef.orderByChild("wins").limitToLast(50)
                .addValueEventListener(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        scoresList.clear();
                        for (DataSnapshot doc : snapshot.getChildren()) {
                            Score score = doc.getValue(Score.class);
                            if (score != null) scoresList.add(score);
                        }
                        // הופכים את הרשימה כדי שהכי הרבה ניצחונות יהיה למעלה (מקום ראשון)
                        Collections.reverse(scoresList);
                        adapter.notifyDataSetChanged();
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        showError();
                    }
                });
    }

    private void showError() {
        Toast.makeText(LeaderboardActivity.this, "Failed to load scores", Toast.LENGTH_SHORT).show();
    }
}