package com.example.minesweeper;

import android.annotation.SuppressLint;
import android.graphics.Color;
import android.os.Bundle;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

public class OnlineGameActivity extends AppCompatActivity {

    private LinearLayout boardContainer;
    private TextView statusText; // Added to show whose turn it is
    private int size = 8;
    private Button[][] buttons = new Button[size][size];
    private FirebaseFirestore firestore;

    // In a real app, pass these via Intent
    private String gameId = "testGame";
    private String currentUser = "player1";
    private String otherPlayer = "player2";

    @SuppressLint("MissingInflatedId")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_game);

        boardContainer = findViewById(R.id.boardContainer);
        // Ensure you add a TextView with id statusText in your XML
        statusText = findViewById(R.id.statusText);

        firestore = FirebaseFirestore.getInstance();

        initBoardUi();
        subscribeToGameUpdates();
    }

    private void initBoardUi() {
        boardContainer.removeAllViews();
        for (int i = 0; i < size; i++) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setLayoutParams(new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT));

            for (int j = 0; j < size; j++) {
                final int x = i, y = j;
                Button btn = new Button(this);
                btn.setText("");
                btn.setOnClickListener(v -> onCellClick(x, y));
                buttons[i][j] = btn;

                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                        0, 150, 1f); // Fixed height for squares
                params.setMargins(2, 2, 2, 2);
                row.addView(btn, params);
            }
            boardContainer.addView(row);
        }
    }

    /**
     * Use SnapshotListener instead of get().
     * This triggers every time the database changes.
     */
    private void subscribeToGameUpdates() {
        DocumentReference ref = firestore.collection("games").document(gameId);

        ref.addSnapshotListener((doc, e) -> {
            if (e != null) {
                Toast.makeText(this, "Listen failed.", Toast.LENGTH_SHORT).show();
                return;
            }

            if (doc != null && doc.exists()) {
                updateBoardUi(doc);
            } else {
                // If game doesn't exist, create it (Only Player 1 should ideally do this)
                if(currentUser.equals("player1")) {
                    createNewGame(ref);
                }
            }
        });
    }

    private void createNewGame(DocumentReference ref) {
        Map<String, Object> boardMap = new HashMap<>();
        boolean[][] mines = new boolean[size][size];
        Random r = new Random();

        // 1. Place Mines
        for (int i = 0; i < size; i++) {
            for (int j = 0; j < size; j++) {
                mines[i][j] = r.nextInt(100) < 15; // 15% chance
            }
        }

        // 2. Calculate Neighbors and Create Cells
        for (int i = 0; i < size; i++) {
            for (int j = 0; j < size; j++) {
                int neighbors = countAdjacentMines(mines, i, j);
                Cell cell = new Cell(mines[i][j], neighbors);
                // We flatten the 2D array into a Map using "row_col" keys
                boardMap.put(i + "_" + j, cell);
            }
        }

        Map<String, Object> game = new HashMap<>();
        game.put("playerTurn", currentUser); // Player 1 starts
        game.put("board", boardMap);
        game.put("status", "ACTIVE");

        ref.set(game);
    }

    private int countAdjacentMines(boolean[][] mines, int r, int c) {
        int count = 0;
        for (int i = -1; i <= 1; i++) {
            for (int j = -1; j <= 1; j++) {
                int nr = r + i;
                int nc = c + j;
                if (nr >= 0 && nr < size && nc >= 0 && nc < size) {
                    if (mines[nr][nc]) count++;
                }
            }
        }
        return count;
    }

    @SuppressWarnings("unchecked")
    private void updateBoardUi(DocumentSnapshot doc) {
        String turn = doc.getString("playerTurn");
        statusText.setText("Turn: " + turn);

        // Disable board if not your turn
        boolean isMyTurn = currentUser.equals(turn);
        boardContainer.setEnabled(isMyTurn);

        // Parse the Flattened Map
        Map<String, Object> boardMap = (Map<String, Object>) doc.get("board");
        if (boardMap == null) return;

        for (int i = 0; i < size; i++) {
            for (int j = 0; j < size; j++) {
                String key = i + "_" + j;
                // Convert Map back to POJO (requires JSON mapping usually, doing manual cast here safely)
                Map<String, Object> cellData = (Map<String, Object>) boardMap.get(key);

                if (cellData != null) {
                    boolean revealed = (boolean) cellData.get("revealed");
                    boolean hasMine = (boolean) cellData.get("hasMine");
                    long neighbors = (long) cellData.get("neighborMines"); // Firestore numbers are Long

                    Button btn = buttons[i][j];

                    if (revealed) {
                        btn.setEnabled(false); // Cannot click again
                        if (hasMine) {
                            btn.setText("💣");
                            btn.setBackgroundColor(Color.RED);
                        } else {
                            btn.setText(neighbors > 0 ? String.valueOf(neighbors) : "");
                            btn.setBackgroundColor(Color.LTGRAY);
                        }
                    } else {
                        btn.setText("");
                        btn.setEnabled(true);
                        btn.setBackgroundColor(Color.parseColor("#DDDDDD")); // default color
                    }
                }
            }
        }
    }

    private void onCellClick(int r, int c) {
        DocumentReference ref = firestore.collection("games").document(gameId);

        // Transaction not strictly necessary for simple turn logic,
        // but good for preventing race conditions. Using simple update for now.
        ref.get().addOnSuccessListener(doc -> {
            String playerTurn = doc.getString("playerTurn");

            if (!currentUser.equals(playerTurn)) {
                Toast.makeText(this, "Wait for your turn!", Toast.LENGTH_SHORT).show();
                return;
            }

            // Key logic: Update specific nested fields using Dot Notation
            // "board.0_0.revealed"
            String cellPath = "board." + r + "_" + c;

            Map<String, Object> updates = new HashMap<>();
            updates.put(cellPath + ".revealed", true);
            updates.put("playerTurn", otherPlayer); // Switch turn

            ref.update(updates).addOnFailureListener(e ->
                    Toast.makeText(this, "Error moving", Toast.LENGTH_SHORT).show()
            );
        });
    }
}