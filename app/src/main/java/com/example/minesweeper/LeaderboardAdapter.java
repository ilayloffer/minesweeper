package com.example.minesweeper;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

public class LeaderboardAdapter extends RecyclerView.Adapter<LeaderboardAdapter.ScoreViewHolder> {

    private List<Score> scores = new ArrayList<>();

    public void setScores(List<Score> newScores) {
        scores.clear();
        scores.addAll(newScores);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ScoreViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.leaderboard_item, parent, false);
        return new ScoreViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ScoreViewHolder holder, int position) {
        Score score = scores.get(position);
        holder.playerName.setText(score.getPlayer());
        holder.time.setText("Time: " + score.getTime() + "s");
        holder.difficulty.setText("Difficulty: " + score.getDifficulty());
    }

    @Override
    public int getItemCount() {
        return scores.size();
    }

    static class ScoreViewHolder extends RecyclerView.ViewHolder {
        TextView playerName, time, difficulty;

        public ScoreViewHolder(@NonNull View itemView) {
            super(itemView);
            playerName = itemView.findViewById(R.id.tvPlayerName);
            time = itemView.findViewById(R.id.tvTime);
            difficulty = itemView.findViewById(R.id.tvDifficulty);
        }
    }
}
