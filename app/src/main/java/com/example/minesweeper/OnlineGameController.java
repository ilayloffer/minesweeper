package com.example.minesweeper;

import android.os.CountDownTimer;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.database.*;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

public class OnlineGameController implements GameController {
    private final GameView view;
    private final int size;
    private DatabaseReference gameRef;
    private DatabaseReference roomRef; // רפרנס לחדר כדי לבדוק מי ה-Host

    private ValueEventListener stateListener;
    private ValueEventListener boardListener;

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

        this.gameRef = FirebaseDatabase.getInstance().getReference("games").child(gameId);
        this.roomRef = FirebaseDatabase.getInstance().getReference("rooms").child(gameId);

        this.localBoard = new Cell[size][size];

        listenToFirebase();
    }

    private void listenToFirebase() {
        // בודקים מי ה-Host הרשמי מתוך ה-room ב-Firebase
        roomRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot roomSnapshot) {
                String hostName = roomSnapshot.child("host").getValue(String.class);

                // רק אם המשתמש הנוכחי הוא ה-Host הרשמי, יש לו הרשאה לייצר את הלוח
                if (currentUser.equals(hostName)) {
                    gameRef.get().addOnSuccessListener(new OnSuccessListener<DataSnapshot>() {
                        @Override
                        public void onSuccess(DataSnapshot snapshot) {
                            if (!snapshot.exists()) {
                                OnlineGameController.this.createNewGame();
                            }
                        }
                    });
                }
            }

            @Override
            public void onCancelled(DatabaseError error) {}
        });

        // האזנה לשינויי סטטוס ותורות
        stateListener = gameRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                if (!snapshot.exists() || isGameOver) return;

                Long myMisses = snapshot.child(currentUser + "_misses").getValue(Long.class);
                String newTurn = snapshot.child("playerTurn").getValue(String.class);

                if (myMisses != null) myCurrentMisses = myMisses;

                if (myMisses != null && myMisses >= 3) {
                    endGame(false);
                    return;
                }
                Long otherMisses = snapshot.child(otherPlayer + "_misses").getValue(Long.class);
                if (otherMisses != null && otherMisses >= 3) {
                    endGame(true);
                    return;
                }

                if (newTurn != null && (!newTurn.equals(currentTurn) || currentTurn == null)) {
                    currentTurn = newTurn;
                    boolean isMyTurn = currentUser.equals(currentTurn);
                    view.setBoardEnabled(isMyTurn);
                    startTurnTimer(isMyTurn);
                }
            }

            @Override
            public void onCancelled(DatabaseError error) {
                view.showMessage("Connection error");
            }
        });

        // האזנה לעדכוני הלוח בשרת
        boardListener = gameRef.child("board").addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot boardSnap) {
                if (!boardSnap.exists() || isGameOver) return;

                int minesInOnlineBoard = 0;

                for (int i = 0; i < size; i++) {
                    for (int j = 0; j < size; j++) {
                        String key = i + "_" + j;
                        DataSnapshot cellSnap = boardSnap.child(key);

                        if (cellSnap.exists()) {
                            Cell cell = new Cell();
                            cell.setRevealed(Boolean.TRUE.equals(cellSnap.child("revealed").getValue(Boolean.class)));

                            boolean hasMine = Boolean.TRUE.equals(cellSnap.child("hasMine").getValue(Boolean.class));
                            cell.setHasMine(hasMine);
                            if (hasMine) {
                                minesInOnlineBoard++;
                            }

                            Long n = cellSnap.child("neighborMines").getValue(Long.class);
                            cell.setNeighborMines(n == null ? 0 : n.intValue());

                            if (localBoard[i][j] != null) {
                                cell.setFlagged(localBoard[i][j].isFlagged());
                            } else {
                                cell.setFlagged(false);
                            }

                            localBoard[i][j] = cell;
                            view.updateCell(i, j, cell);
                        }
                    }
                }

                if (view instanceof GameActivity) {
                    ((GameActivity) view).setDynamicBombsCount(minesInOnlineBoard);
                }
            }

            @Override
            public void onCancelled(DatabaseError error) {}
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
        // ה-Host תמיד קובע שהתור הראשון שייך לו, ובכך נמנעת התנגשות
        game.put("playerTurn", currentUser);
        game.put("board", boardMap);
        game.put("status", "ACTIVE");
        game.put(currentUser + "_misses", 0);
        game.put(otherPlayer + "_misses", 0);
        game.put("lastMoveTimestamp", ServerValue.TIMESTAMP);

        gameRef.setValue(game);
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

        view.showMessage("STRIKE! You missed your turn ⏳");

        long newMisses = myCurrentMisses + 1;

        Map<String, Object> updates = new HashMap<>();
        updates.put(currentUser + "_misses", newMisses);
        updates.put("playerTurn", otherPlayer);
        updates.put("lastMoveTimestamp", ServerValue.TIMESTAMP);

        gameRef.updateChildren(updates);
    }

    private void endGame(boolean didIWin) {
        isGameOver = true;
        if (turnTimer != null) turnTimer.cancel();

        if (stateListener != null) gameRef.removeEventListener(stateListener);
        if (boardListener != null) gameRef.child("board").removeEventListener(boardListener);

        view.showGameOver(didIWin);
        view.updateStatus(didIWin ? "You Won! 🎉" : "You Lost! 💥");

        if (didIWin && currentUser != null && !currentUser.equals("Guest")) {
            DatabaseReference userRef = FirebaseDatabase.getInstance().getReference("leaderboard").child(currentUser);
            userRef.addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(DataSnapshot snapshot) {
                    Long currentWins = snapshot.child("onlineWins").getValue(Long.class);
                    if (currentWins == null) currentWins = 0L;

                    Map<String, Object> updates = new HashMap<>();
                    updates.put("onlineWins", currentWins + 1);
                    updates.put("username", currentUser);

                    userRef.updateChildren(updates);
                }
                @Override
                public void onCancelled(DatabaseError error) {}
            });
        }
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

        Map<String, Object> updates = new HashMap<>();

        if (cell.getHasMine()) {
            updates.put("board/" + r + "_" + c + "/revealed", true);
            updates.put(currentUser + "_misses", 3);
            updates.put("lastMoveTimestamp", ServerValue.TIMESTAMP);
            gameRef.updateChildren(updates);
            return;
        }

        floodFill(r, c, updates);

        updates.put("playerTurn", otherPlayer);
        updates.put(currentUser + "_misses", 0);
        updates.put("lastMoveTimestamp", ServerValue.TIMESTAMP);

        gameRef.updateChildren(updates);
    }

    private void floodFill(int r, int c, Map<String, Object> updates) {
        if (r < 0 || c < 0 || r >= size || c >= size) return;

        Cell cell = localBoard[r][c];
        if (cell.isRevealed() || cell.isFlagged()) return;

        cell.setRevealed(true);
        updates.put("board/" + r + "_" + c + "/revealed", true);

        if (cell.getNeighborMines() == 0 && !cell.getHasMine()) {
            for (int k = 0; k < 8; k++) {
                floodFill(r + dx[k], c + dy[k], updates);
            }
        }
    }

    @Override
    public void onCellLongClicked(int r, int c) {
        if (isGameOver) return;

        Cell cell = localBoard[r][c];
        if (cell == null || cell.isRevealed()) return;

        boolean currentFlag = cell.isFlagged();
        cell.setFlagged(!currentFlag);

        view.updateCell(r, c, cell);
    }

    @Override
    public void onDestroy() {
        if (gameRef != null) {
            if (stateListener != null) gameRef.removeEventListener(stateListener);
            if (boardListener != null) gameRef.child("board").removeEventListener(boardListener);
        }

        if (turnTimer != null) {
            turnTimer.cancel();
        }
    }
}