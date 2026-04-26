package com.example.minesweeper;

import android.os.CountDownTimer;

import com.google.firebase.database.*;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

public class OnlineGameController implements GameController {
    private final GameView view;
    private final int size;
    private DatabaseReference gameRef;
    private ValueEventListener listener;

    private final String gameId;
    private final String currentUser;
    private final String otherPlayer;
    private String currentTurn;

    private CountDownTimer turnTimer;
    private boolean isGameOver = false;
    private long myCurrentMisses = 0;

    private Cell[][] localBoard;

    private final int[] dx = {-1, -1, -1, 0, 0, 1, 1, 1};
    private final int[] dy = {-1, 0, 1, -1, 1, -1, 0, 1};

    public OnlineGameController(GameView view, int size, String gameId, String currentUser, String otherPlayer) {
        this.view = view;
        this.size = size;
        this.gameId = gameId;
        this.currentUser = currentUser;
        this.otherPlayer = otherPlayer;
        this.gameRef = FirebaseDatabase.getInstance()
                .getReference("games")
                .child(gameId);
        this.localBoard = new Cell[size][size];

        listenToFirebase();
    }

    private void listenToFirebase() {
        if (currentUser.equals("player1")) {
            createNewGame();
        }

        listener = gameRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                if (!snapshot.exists()) return;
                parseAndUpdateBoard(snapshot);
            }

            @Override
            public void onCancelled(DatabaseError error) {
                view.showMessage("Connection error");
            }
        });
    }

    private void createNewGame() {
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

        gameRef.setValue(game);
    }

    @SuppressWarnings("unchecked")
    private void parseAndUpdateBoard(DataSnapshot doc) {
        if (isGameOver) return;

        Long myMisses = doc.child(currentUser + "_misses").getValue(Long.class);
        Long otherMisses = doc.child(otherPlayer + "_misses").getValue(Long.class);

        if (myMisses != null) {
            myCurrentMisses = myMisses;
        }

        // בדיקת ניצחון/הפסד (בין אם מ-3 סטרייקים של חוסר פעילות, ובין אם מלחיצה על מוקש)
        if (myMisses != null && myMisses >= 3) {
            endGame(false);
            return;
        }
        if (otherMisses != null && otherMisses >= 3) {
            endGame(true);
            return;
        }

        currentTurn = doc.child("playerTurn").getValue(String.class);
        boolean isMyTurn = currentUser.equals(currentTurn);
        view.setBoardEnabled(isMyTurn);

        DataSnapshot boardSnap = doc.child("board");

        for (int i = 0; i < size; i++) {
            for (int j = 0; j < size; j++) {
                String key = i + "_" + j;
                DataSnapshot cellSnap = boardSnap.child(key);

                if (cellSnap.exists()) {
                    Cell cell = new Cell();
                    cell.setRevealed(Boolean.TRUE.equals(cellSnap.child("revealed").getValue(Boolean.class)));
                    cell.setHasMine(Boolean.TRUE.equals(cellSnap.child("hasMine").getValue(Boolean.class)));

                    Long n = cellSnap.child("neighborMines").getValue(Long.class);
                    cell.setNeighborMines(n == null ? 0 : n.intValue());

                    cell.setFlagged(Boolean.TRUE.equals(cellSnap.child("flagged").getValue(Boolean.class)));

                    localBoard[i][j] = cell;
                    view.updateCell(i, j, cell);
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
        if (isGameOver || !isMyTurn) return;

        // הודעה לשחקן שלא הספיק לשחק
        view.showMessage("STRIKE! You missed your turn ⏳");

        gameRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                if (!snapshot.exists()) return;

                Long misses = snapshot.child(currentUser + "_misses").getValue(Long.class);
                if (misses == null) misses = 0L;

                long newMisses = misses + 1; // הוספת STRIKE

                Map<String, Object> updates = new HashMap<>();
                updates.put(currentUser + "_misses", newMisses);
                updates.put("playerTurn", otherPlayer);

                gameRef.updateChildren(updates);
            }

            @Override
            public void onCancelled(DatabaseError error) {
                view.showMessage("Timeout error");
            }
        });
    }

    private void endGame(boolean didIWin) {
        isGameOver = true;
        if (turnTimer != null) turnTimer.cancel();
        view.showGameOver(didIWin);
        view.updateStatus(didIWin ? "You Won! 🎉" : "You Lost! 💥");
    }

    @Override
    public void onCellClicked(int r, int c) {
        if (isGameOver) return;

        if (!currentUser.equals(currentTurn)) {
            view.showMessage("Wait for your turn!");
            return;
        }

        if (localBoard[r][c] == null) return;
        Cell cell = localBoard[r][c];
        if (cell.isRevealed() || cell.isFlagged()) return;

        if (turnTimer != null) turnTimer.cancel();

        // --- כאן נמצא השינוי: אם לוחצים על מוקש ---
        if (cell.getHasMine()) {
            cell.setRevealed(true);

            gameRef.child("board")
                    .child(r + "_" + c)
                    .child("revealed")
                    .setValue(true);

            // בום! הפסד אוטומטי מיידי
            // מעדכנים את הפסילות ל-3 (או יותר) כדי שהמשחק ייגמר מיד לשני השחקנים
            gameRef.child(currentUser + "_misses").setValue(3);

            return;
        }

        // פתיחת שטח רגילה
        floodFill(r, c);

        // סיום מהלך רגיל (מוצלח) → איפוס הסטרייקים ומעבר תור
        gameRef.child("playerTurn").setValue(otherPlayer);
        gameRef.child(currentUser + "_misses").setValue(0);
    }

    private void floodFill(int r, int c) {
        if (r < 0 || c < 0 || r >= size || c >= size) return;

        Cell cell = localBoard[r][c];
        if (cell.isRevealed() || cell.isFlagged()) return;

        cell.setRevealed(true);

        gameRef.child("board")
                .child(r + "_" + c)
                .child("revealed")
                .setValue(true);

        if (cell.getNeighborMines() == 0 && !cell.getHasMine()) {
            for (int k = 0; k < 8; k++) {
                floodFill(r + dx[k], c + dy[k]);
            }
        }
    }

    @Override
    public void onCellLongClicked(int r, int c) {
        if (!currentUser.equals(currentTurn) || isGameOver) return;

        boolean currentFlag = localBoard[r][c].isFlagged();

        gameRef.child("board")
                .child(r + "_" + c)
                .child("flagged")
                .setValue(!currentFlag);
    }

    @Override
    public void onDestroy() {
        if (listener != null && gameRef != null) {
            gameRef.removeEventListener(listener);
        }

        if (turnTimer != null) {
            turnTimer.cancel();
        }
    }
}