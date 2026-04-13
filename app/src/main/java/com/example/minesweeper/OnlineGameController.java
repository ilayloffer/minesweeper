package com.example.minesweeper;

import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

public class OnlineGameController implements GameController {
    private final GameView view;
    private final int size;
    private final FirebaseFirestore firestore;
    private ListenerRegistration listener;

    private final String gameId;
    private final String currentUser;
    private final String otherPlayer;
    private String currentTurn;

    private final int[] dx = {-1, -1, -1, 0, 0, 1, 1, 1};
    private final int[] dy = {-1, 0, 1, -1, 1, -1, 0, 1};

    public OnlineGameController(GameView view, int size, String gameId, String currentUser, String otherPlayer) {
        this.view = view;
        this.size = size;
        this.gameId = gameId;
        this.currentUser = currentUser;
        this.otherPlayer = otherPlayer;
        this.firestore = FirebaseFirestore.getInstance();

        listenToFirebase();
    }

    private void listenToFirebase() {
        DocumentReference ref = firestore.collection("games").document(gameId);
        listener = ref.addSnapshotListener((doc, e) -> {
            if (e != null) {
                view.showMessage("Connection error");
                return;
            }
            if (doc != null && doc.exists()) {
                parseAndUpdateBoard(doc);
            } else if (currentUser.equals("player1")) {
                createNewGame(ref);
            }
        });
    }

    private void createNewGame(DocumentReference ref) {
        Map<String, Object> boardMap = new HashMap<>();
        boolean[][] tempMines = new boolean[size][size];
        Random r = new Random();

        for (int i = 0; i < size; i++) {
            for (int j = 0; j < size; j++) {
                tempMines[i][j] = r.nextInt(100) < 15;
            }
        }

        for (int i = 0; i < size; i++) {
            for (int j = 0; j < size; j++) {
                int count = 0;
                for (int k = 0; k < 8; k++) {
                    int ni = i + dx[k];
                    int nj = j + dy[k];
                    if (ni >= 0 && nj >= 0 && ni < size && nj < size && tempMines[ni][nj]) count++;
                }
                Cell cell = new Cell(tempMines[i][j], count);
                boardMap.put(i + "_" + j, cell);
            }
        }

        Map<String, Object> game = new HashMap<>();
        game.put("playerTurn", "player1");
        game.put("board", boardMap);
        game.put("status", "ACTIVE");
        ref.set(game);
    }

    @SuppressWarnings("unchecked")
    private void parseAndUpdateBoard(DocumentSnapshot doc) {
        currentTurn = doc.getString("playerTurn");
        boolean isMyTurn = currentUser.equals(currentTurn);

        view.updateStatus("Turn: " + currentTurn);
        view.setBoardEnabled(isMyTurn);

        Map<String, Object> boardMap = (Map<String, Object>) doc.get("board");
        if (boardMap == null) return;

        for (int i = 0; i < size; i++) {
            for (int j = 0; j < size; j++) {
                String key = i + "_" + j;
                Map<String, Object> cellData = (Map<String, Object>) boardMap.get(key);

                if (cellData != null) {
                    Cell cell = new Cell();
                    cell.setRevealed((boolean) cellData.getOrDefault("revealed", false));
                    cell.setHasMine((boolean) cellData.getOrDefault("hasMine", false));
                    cell.setNeighborMines(((Long) cellData.getOrDefault("neighborMines", 0L)).intValue());
                    cell.setFlagged((boolean) cellData.getOrDefault("flagged", false));

                    view.updateCell(i, j, cell);
                }
            }
        }
    }

    @Override
    public void onCellClicked(int r, int c) {
        if (!currentUser.equals(currentTurn)) {
            view.showMessage("Wait for your turn!");
            return;
        }

        DocumentReference ref = firestore.collection("games").document(gameId);
        ref.update(
                "board." + r + "_" + c + ".revealed", true,
                "playerTurn", otherPlayer
        ).addOnFailureListener(e -> view.showMessage("Error moving"));
    }

    @Override
    public void onCellLongClicked(int r, int c) {
        if (!currentUser.equals(currentTurn)) return;

        DocumentReference ref = firestore.collection("games").document(gameId);
        ref.get().addOnSuccessListener(doc -> {
            boolean currentFlag = doc.getBoolean("board." + r + "_" + c + ".flagged") != null &&
                    Boolean.TRUE.equals(doc.getBoolean("board." + r + "_" + c + ".flagged"));
            ref.update("board." + r + "_" + c + ".flagged", !currentFlag);
        });
    }

    @Override
    public void onDestroy() {
        if (listener != null) listener.remove();
    }
}