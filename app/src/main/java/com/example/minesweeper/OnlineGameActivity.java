package com.example.minesweeper;

import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.LinearLayout;
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
    private int size = 8;
    private Button[][] buttons = new Button[size][size];
    private FirebaseFirestore firestore;
    private String gameId = "testGame";
    private String currentUser = "player1"; // replace with auth UID
    private String otherPlayer = "player2";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_game); // reuse your layout
        boardContainer = findViewById(R.id.boardContainer);

        firestore = FirebaseFirestore.getInstance();

        initBoardUi();
        loadOrCreateGame();
    }

    private void initBoardUi() {
        boardContainer.removeAllViews();

        for (int i = 0; i < size; i++) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            for (int j = 0; j < size; j++) {
                final int x = i, y = j;
                Button btn = new Button(this);
                btn.setText("");
                btn.setOnClickListener(v -> onCellClick(x, y, btn));
                buttons[i][j] = btn;
                row.addView(btn, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
            }
            boardContainer.addView(row);
        }
    }

    private void loadOrCreateGame() {
        DocumentReference ref = firestore.collection("games").document(gameId);
        ref.get().addOnSuccessListener(doc -> {
            if (!doc.exists()) createNewGame(ref);
            else loadBoardFromFirestore(doc);
        });
    }

    private void createNewGame(DocumentReference ref) {
        Map<String, Object> board = new HashMap<>();
        Random r = new Random();
        for (int i = 0; i < size; i++) {
            for (int j = 0; j < size; j++) {
                boolean hasMine = r.nextInt(100) < 15;
                int adj = 0; // optional: calculate adjacent mines
                board.put(i + "_" + j, new Cell(hasMine, adj));
            }
        }
        Map<String, Object> game = new HashMap<>();
        game.put("playerTurn", currentUser);
        game.put("board", board);

        ref.set(game)
                .addOnSuccessListener(aVoid -> Toast.makeText(this, "Online game created", Toast.LENGTH_SHORT).show());
    }

    private void loadBoardFromFirestore(DocumentSnapshot doc) {
        // TODO: parse Firestore board and update buttons
        Toast.makeText(this, "Online game loaded", Toast.LENGTH_SHORT).show();
    }

    private void onCellClick(int i, int j, Button btn) {
        DocumentReference ref = firestore.collection("games").document(gameId);
        ref.get().addOnSuccessListener(doc -> {
            String playerTurn = doc.getString("playerTurn");
            if (!currentUser.equals(playerTurn)) {
                Toast.makeText(this, "Not your turn!", Toast.LENGTH_SHORT).show();
                return;
            }

            // TODO: Update Firestore with revealed cell
            btn.setText("X"); // temporary
            ref.update("playerTurn", otherPlayer);
        });
    }
}
