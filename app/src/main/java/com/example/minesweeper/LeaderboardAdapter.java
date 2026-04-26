package com.example.minesweeper;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.List;

public class LeaderboardAdapter extends RecyclerView.Adapter<LeaderboardAdapter.ScoreViewHolder> {

    private final List<Score> scoresList;

    public LeaderboardAdapter(List<Score> scoresList) {
        this.scoresList = scoresList;
    }

    @NonNull
    @Override
    public ScoreViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_leaderboard, parent, false);
        return new ScoreViewHolder(view);
    }



    @Override
    public int getItemCount() {
        return scoresList.size();
    }

    public static class ScoreViewHolder extends RecyclerView.ViewHolder {
        TextView tvRank, tvName, tvDetails;

        public ScoreViewHolder(@NonNull View itemView) {
            super(itemView);
            tvRank = itemView.findViewById(R.id.tvRank);
            tvName = itemView.findViewById(R.id.tvName);
            tvDetails = itemView.findViewById(R.id.tvDetails);
        }
    }
    // בתוך המתודה onBindViewHolder בקובץ LeaderboardAdapter.java:

    @Override
    public void onBindViewHolder(@NonNull ScoreViewHolder holder, int position) {
        Score currentScore = scoresList.get(position);

        holder.tvRank.setText(String.valueOf(position + 1));
        holder.tvName.setText(currentScore.getPlayer());

        // מציג גם זמן, גם ניצחונות וגם רמת קושי
        String details = "Time: " + currentScore.getTime() + "s"
                + " | Wins: " + currentScore.getWins()
                + " | Diff: " + currentScore.getDifficulty();

        holder.tvDetails.setText(details);
    }
}