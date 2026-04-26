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

    // משתנים חדשים
    private String currentUser;
    private TextView tvMyRankDetails;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_leaderboard);

        // מושך את שם השחקן מהמסך הראשי
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

        // טעינה ראשונית - לפי ניצחונות אונליין
        loadData("onlineWins", false);
    }

    private void loadData(String orderByField, boolean isAscending) {
        if (currentListener != null) {
            leaderboardRef.removeEventListener(currentListener);
        }

        // עכשיו מושכים הכל (בלי limit) כדי שנוכל לחשב את המיקום האמיתי של השחקן שלנו
        currentListener = leaderboardRef.orderByChild(orderByField)
                .addValueEventListener(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        List<Score> fullList = new ArrayList<>();

                        for (DataSnapshot doc : snapshot.getChildren()) {
                            Score score = doc.getValue(Score.class);
                            if (score != null) {
                                score.setPlayerName(doc.getKey());

                                // מתעלם מזמנים של 0
                                if (orderByField.equals("bestOfflineTime") && score.getBestOfflineTime() == 0) {
                                    continue;
                                }
                                fullList.add(score);
                            }
                        }

                        // פיירבייס תמיד מביא סדר עולה. אם צריך הכי גבוה למעלה, נהפוך:
                        if (!isAscending) {
                            Collections.reverse(fullList);
                        }

                        // --- שלב 1: חיפוש השחקן שלנו ---
                        int myRank = -1;
                        Score myScore = null;
                        for (int i = 0; i < fullList.size(); i++) {
                            if (fullList.get(i).getPlayerName().equals(currentUser)) {
                                myRank = i + 1; // המיקום הוא האינדקס + 1
                                myScore = fullList.get(i);
                                break;
                            }
                        }

                        // מעדכן את הטקסט למטה
                        if (myScore != null) {
                            String timeDisplay = myScore.getBestOfflineTime() == 0 ? "N/A" : myScore.getBestOfflineTime() + "s";
                            String myStats = "Place: #" + myRank + " | " + currentUser + "\n" +
                                    "Online: " + myScore.getOnlineWins() + " | Offline: " + myScore.getOfflineWins() + " | Time: " + timeDisplay;
                            tvMyRankDetails.setText(myStats);
                        } else {
                            tvMyRankDetails.setText(currentUser + " - No records in this category yet.");
                        }

                        // --- שלב 2: משאירים רק את 10 הראשונים להצגה ---
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