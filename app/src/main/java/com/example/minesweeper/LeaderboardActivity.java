package com.example.minesweeper;

import android.os.Bundle;
import android.widget.RadioGroup;
import android.widget.TextView;
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
    private ValueEventListener currentListener;

    private String currentUser;
    private TextView tvMyRankDetails;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_leaderboard);

        currentUser = getIntent().getStringExtra("currentUser");
        if (currentUser == null) currentUser = "Guest";

        tvMyRankDetails = findViewById(R.id.tvMyRankDetails);

        recyclerView = findViewById(R.id.recyclerViewLeaderboard);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        scoresList = new ArrayList<>();
        adapter = new LeaderboardAdapter(scoresList);
        recyclerView.setAdapter(adapter);

        leaderboardRef = FirebaseDatabase.getInstance().getReference("leaderboard");

        RadioGroup rgSortOptions = findViewById(R.id.rgSortOptions);
        rgSortOptions.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == R.id.rbSortOnlineWins) {
                loadData("onlineWins", false);
            } else if (checkedId == R.id.rbSortOfflineWins) {
                loadData("offlineWins", false);
            } else if (checkedId == R.id.rbSortTime) {
                loadData("bestOfflineTime", true);
            }
        });

        // טעינה ראשונית
        loadData("onlineWins", false);
    }

    private void loadData(String orderByField, boolean isAscending) {
        if (currentListener != null) {
            leaderboardRef.removeEventListener(currentListener);
        }

        // מעדכנים את האדפטר באיזה סנן אנחנו משתמשים עכשיו כדי שיציג את המידע הנכון
        adapter.setCurrentFilter(orderByField);

        currentListener = leaderboardRef.orderByChild(orderByField)
                .addValueEventListener(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        List<Score> fullList = new ArrayList<>();

                        for (DataSnapshot doc : snapshot.getChildren()) {
                            Score score = doc.getValue(Score.class);
                            if (score != null) {
                                score.setPlayerName(doc.getKey());

                                // --- לוגיקת הסינון: מדלגים על שחקנים עם 0 בקטגוריה המבוקשת ---
                                if (orderByField.equals("onlineWins") && score.getOnlineWins() == 0) continue;
                                if (orderByField.equals("offlineWins") && score.getOfflineWins() == 0) continue;
                                if (orderByField.equals("bestOfflineTime") && score.getBestOfflineTime() == 0) continue;

                                fullList.add(score);
                            }
                        }

                        // היפוך הרשימה אם צריך מהגבוה לנמוך
                        if (!isAscending) {
                            Collections.reverse(fullList);
                        }

                        int myRank = -1;
                        Score myScore = null;
                        for (int i = 0; i < fullList.size(); i++) {
                            if (fullList.get(i).getPlayerName().equals(currentUser)) {
                                myRank = i + 1;
                                myScore = fullList.get(i);
                                break;
                            }
                        }

                        if (myScore != null) {
                            String timeDisplay = myScore.getBestOfflineTime() == 0 ? "N/A" : myScore.getBestOfflineTime() + "s";
                            String myStats = "Place: #" + myRank + " | " + currentUser + "\n" +
                                    "Online: " + myScore.getOnlineWins() + " | Offline: " + myScore.getOfflineWins() + " | Time: " + timeDisplay;
                            tvMyRankDetails.setText(myStats);
                        } else {
                            tvMyRankDetails.setText(currentUser + " - Unranked in this category.");
                        }

                        scoresList.clear();
                        int limit = Math.min(fullList.size(), 10);
                        for (int i = 0; i < limit; i++) {
                            scoresList.add(fullList.get(i));
                        }

                        adapter.notifyDataSetChanged();
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        Toast.makeText(LeaderboardActivity.this, "Failed to load scores", Toast.LENGTH_SHORT).show();
                    }
                });
    }
}