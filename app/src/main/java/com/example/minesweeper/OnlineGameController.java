package com.example.minesweeper;

import android.os.CountDownTimer;

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

    private CountDownTimer turnTimer;
    private boolean isGameOver = false;

    // שמירת הלוח מקומית כדי לחשב פתיחת שטחים
    private Cell[][] localBoard;

    private final int[] dx = {-1, -1, -1, 0, 0, 1, 1, 1};
    private final int[] dy = {-1, 0, 1, -1, 1, -1, 0, 1};

    public OnlineGameController(GameView view, int size, String gameId, String currentUser, String otherPlayer) {
        this.view = view;
        this.size = size;
        this.gameId = gameId;
        this.currentUser = currentUser;
        this.otherPlayer = otherPlayer;
        this.firestore = FirebaseFirestore.getInstance();
        this.localBoard = new Cell[size][size];

        listenToFirebase();
    }

    private void listenToFirebase() {
        DocumentReference ref = firestore.collection("games").document(gameId);

        if (currentUser.equals("player1")) {
            createNewGame(ref);
        }

        listener = ref.addSnapshotListener((doc, e) -> {
            if (e != null) {
                view.showMessage("Connection error");
                return;
            }
            if (doc != null && doc.exists()) {
                parseAndUpdateBoard(doc);
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
        game.put(currentUser + "_misses", 0);
        game.put(otherPlayer + "_misses", 0);

        ref.set(game);
    }

    @SuppressWarnings("unchecked")
    private void parseAndUpdateBoard(DocumentSnapshot doc) {
        if (isGameOver) return;

        Long myMisses = doc.getLong(currentUser + "_misses");
        Long otherMisses = doc.getLong(otherPlayer + "_misses");

        if (myMisses != null && myMisses >= 3) {
            endGame(false);
            return;
        }
        if (otherMisses != null && otherMisses >= 3) {
            endGame(true);
            return;
        }

        currentTurn = doc.getString("playerTurn");
        boolean isMyTurn = currentUser.equals(currentTurn);
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

                    localBoard[i][j] = cell; // שמירת המצב המעודכן בזיכרון המקומי
                    view.updateCell(i, j, cell);

                    if (cell.isRevealed() && cell.getHasMine()) {
                        endGame(!isMyTurn);
                        return;
                    }
                }
            }
        }

        startTurnTimer(isMyTurn);
    }

    private void startTurnTimer(boolean isMyTurn) {
        if (turnTimer != null) turnTimer.cancel();

        long timeLimit = isMyTurn ? 5000 : 6000;

        turnTimer = new CountDownTimer(timeLimit, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                int secondsLeft = (int) (millisUntilFinished / 1000);
                if (isMyTurn) {
                    view.updateStatus("Your Turn! (" + secondsLeft + "s)");
                } else {
                    view.updateStatus("Opponent's Turn... (" + Math.min(secondsLeft, 5) + "s)");
                }
            }

            @Override
            public void onFinish() {
                handleTimeout(isMyTurn);
            }
        }.start();
    }

    private void handleTimeout(boolean isMyTurn) {
        // התיקון: רק השחקן שזה התור שלו יכול להכריז שנגמר לו הזמן!
        // זה מונע משני המכשירים לתת פסילות אחד לשני בבת אחת.
        if (isGameOver || !isMyTurn) return;

        DocumentReference ref = firestore.collection("games").document(gameId);

        ref.get().addOnSuccessListener(doc -> {
            if (!doc.exists()) return;

            long currentMisses = doc.getLong(currentUser + "_misses") != null ? doc.getLong(currentUser + "_misses") : 0;
            ref.update(
                    "playerTurn", otherPlayer,
                    currentUser + "_misses", currentMisses + 1
            );
        });
    }

    private void endGame(boolean didIWin) {
        isGameOver = true;
        if (turnTimer != null) turnTimer.cancel();
        view.showGameOver(didIWin);
        view.updateStatus(didIWin ? "You Won!" : "You Lost!");
    }

    @Override
    public void onCellClicked(int r, int c) {
        if (!currentUser.equals(currentTurn) || isGameOver) {
            view.showMessage("Wait for your turn!");
            return;
        }
        if (localBoard[r][c].isRevealed() || localBoard[r][c].isFlagged()) return;

        DocumentReference ref = firestore.collection("games").document(gameId);

        // יצירת מילון שיכיל את כל העדכונים בבת אחת
        Map<String, Object> updates = new HashMap<>();
        updates.put("playerTurn", otherPlayer);
        updates.put(currentUser + "_misses", 0); // איפוס פסילות כי הוא שיחק

        // הפעלת פתיחת השטחים
        floodFill(r, c, updates);

        // שליחה מרוכזת ל-Firebase
        ref.update(updates).addOnFailureListener(e -> view.showMessage("Error moving"));
    }

    // הפונקציה שפותחת משבצות ריקות מסביב (רקורסיה)
    private void floodFill(int r, int c, Map<String, Object> updates) {
        if (r < 0 || c < 0 || r >= size || c >= size) return;

        Cell cell = localBoard[r][c];
        if (cell.isRevealed() || cell.isFlagged()) return;

        // מסמנים כפתוח מקומית כדי שלא נחזור אליו בטעות, ומוסיפים לעדכון
        cell.setRevealed(true);
        updates.put("board." + r + "_" + c + ".revealed", true);

        // אם יש 0 מוקשים בסביבה - ממשיכים לכל השכנים
        if (cell.getNeighborMines() == 0 && !cell.getHasMine()) {
            for (int k = 0; k < 8; k++) {
                floodFill(r + dx[k], c + dy[k], updates);
            }
        }
    }

    @Override
    public void onCellLongClicked(int r, int c) {
        if (!currentUser.equals(currentTurn) || isGameOver) return;

        DocumentReference ref = firestore.collection("games").document(gameId);
        boolean currentFlag = localBoard[r][c].isFlagged();
        ref.update("board." + r + "_" + c + ".flagged", !currentFlag);
    }

    @Override
    public void onDestroy() {
        if (listener != null) listener.remove();
        if (turnTimer != null) turnTimer.cancel();
    }
}