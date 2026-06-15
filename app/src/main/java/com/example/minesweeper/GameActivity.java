package com.example.minesweeper;

import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.List;

public class GameActivity extends AppCompatActivity implements GameView {

    private TextView statusText;
    private TextView tvCellsRemaining;
    private LinearLayout boardContainer;
    private View overlay;
    private TextView overlayTitle;
    private Button btnHome;
    private Button btnExitSave;

    // --- רכיבי הצ'אט ---
    private LinearLayout chatContainer;
    private ListView chatListView;
    private EditText etChatMessage;
    private Button btnSendChat;

    private GameController controller;
    private Button[][] buttons;

    // --- ניהול מונה דינמי אמין ---
    private int bombsCount = 10;
    private int size;
    private boolean isOnline;
    private boolean gameStarted = false;

    private DatabaseReference roomRef;
    private DatabaseReference chatRef;
    private String currentUser;
    private String otherPlayer;
    private String roomId;

    private List<String> chatMessagesList = new ArrayList<>();
    private ArrayAdapter<String> chatAdapter;
    private ValueEventListener chatListener;
    private boolean chatListenerAdded = false;
    private ValueEventListener roomListener;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_game);

        bindViews();
        readIntent();

        Intent intent = getIntent();
        String matchmakingGameId = intent.getStringExtra("gameId");

        if (matchmakingGameId != null) {
            isOnline = true;
            roomId = matchmakingGameId;
            boolean isPlayer1 = intent.getBooleanExtra("isPlayer1", true);

            btnExitSave.setVisibility(View.GONE);
            if (chatContainer != null) {
                chatContainer.setVisibility(View.VISIBLE);
            }

            roomRef = FirebaseDatabase.getInstance().getReference("rooms").child(roomId);
            chatRef = FirebaseDatabase.getInstance().getReference("games").child(roomId).child("chat");

            setupDisconnectHook();
            setupChat();

            statusText.setText("Game started! Playing online...");
            controller = new OnlineGameController(this, size, roomId, currentUser, isPlayer1 ? "Player 2" : "Player 1");
            createBoardUI();

        } else {
            if (isOnline) {
                initOnlineGame();
            } else {
                initOfflineGame();
            }
        }

        btnHome.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                GameActivity.this.finish();
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (roomRef != null && roomListener != null) {
            roomRef.removeEventListener(roomListener);
        }
        detachChatListener();

        if (controller != null) {
            controller.onDestroy();
        }
        removePlayerFromRoom();
    }

    private void bindViews() {
        statusText        = findViewById(R.id.statusText);
        tvCellsRemaining  = findViewById(R.id.tvCellsRemaining);
        boardContainer    = findViewById(R.id.boardContainer);
        overlay           = findViewById(R.id.overlay);
        overlayTitle      = findViewById(R.id.overlayTitle);
        btnHome           = findViewById(R.id.btnHome);
        btnExitSave       = findViewById(R.id.btnExitSave);

        chatContainer     = findViewById(R.id.chatContainer);
        chatListView      = findViewById(R.id.chatListView);
        etChatMessage     = findViewById(R.id.etChatMessage);
        btnSendChat       = findViewById(R.id.btnSendChat);
    }

    private void readIntent() {
        Intent intent = getIntent();
        size        = intent.getIntExtra("size", 10);
        isOnline    = intent.getBooleanExtra("isOnline", false);
        roomId      = intent.getStringExtra("roomCode");
        currentUser = intent.getStringExtra("currentUser");

        if (size == 10) {
            bombsCount = 10;
        } else {
            bombsCount = (int) (size * size * 0.12);
        }
    }

    private void initOfflineGame() {
        if (chatContainer != null) {
            chatContainer.setVisibility(View.GONE);
        }

        btnExitSave.setVisibility(View.VISIBLE);
        btnExitSave.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                GameActivity.this.saveAndExitOffline();
            }
        });

        controller = new OfflineGameController(this, size, currentUser);
        createBoardUI();

        if (getIntent().getBooleanExtra("loadSaved", false)) {
            loadSavedOfflineData();
        }
    }

    private void initOnlineGame() {
        btnExitSave.setVisibility(View.GONE);

        if (chatContainer != null) {
            chatContainer.setVisibility(View.VISIBLE);
        }

        if (roomId == null || roomId.isEmpty()) {
            Toast.makeText(this, "Error: Invalid Room Code", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        roomRef = FirebaseDatabase.getInstance().getReference("rooms").child(roomId);
        chatRef = FirebaseDatabase.getInstance().getReference("games").child(roomId).child("chat");

        setupDisconnectHook();
        listenForRoomChanges();
        setupChat();
    }

    private void saveAndExitOffline() {
        if (controller instanceof OfflineGameController) {
            OfflineGameController offline = (OfflineGameController) controller;
            SharedPreferences sp = getSharedPreferences("SavedGame", MODE_PRIVATE);
            sp.edit()
                    .putBoolean("hasSaved", true)
                    .putInt("size", size)
                    .putInt("time", offline.getSecondsElapsed())
                    .putString("data", offline.getBoardData())
                    .apply();

            Toast.makeText(this, "Game Saved Offline", Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    private void loadSavedOfflineData() {
        SharedPreferences sp = getSharedPreferences("SavedGame", MODE_PRIVATE);
        String data = sp.getString("data", "");
        int time = sp.getInt("time", 0);

        if (controller instanceof OfflineGameController) {
            ((OfflineGameController) controller).loadExistingGame(data, time);
            sp.edit().putBoolean("hasSaved", false).apply();
        }
    }

    private void setupDisconnectHook() {
        roomRef.child("players").addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                String p1 = snapshot.child("player1").getValue(String.class);
                String p2 = snapshot.child("player2").getValue(String.class);
                if (currentUser == null) return;

                String myKey = currentUser.equals(p1) ? "player1" : (currentUser.equals(p2) ? "player2" : null);
                if (myKey != null) {
                    roomRef.child("players").child(myKey).onDisconnect().removeValue();
                    roomRef.child("status").onDisconnect().setValue("waiting");
                }
            }
            @Override public void onCancelled(@NonNull DatabaseError error) {}
        });
    }

    private void listenForRoomChanges() {
        roomListener = new ValueEventListener() {
            private void run() {
                statusText.setText("Game started vs " + otherPlayer);
                controller = new OnlineGameController(GameActivity.this, size, roomId, currentUser, otherPlayer);
                createBoardUI();
            }

            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                String host = snapshot.child("host").getValue(String.class);
                String guest = snapshot.child("guest").getValue(String.class);

                if (host == null || guest == null || guest.isEmpty()) {
                    if (gameStarted) { finish(); }
                    else { statusText.setText("Waiting for opponent..."); }
                    return;
                }

                if (!gameStarted) {
                    gameStarted = true;
                    otherPlayer = currentUser.equals(host) ? guest : host;

                    runOnUiThread(this::run);
                }
            }
            @Override public void onCancelled(@NonNull DatabaseError error) {}
        };
        roomRef.addValueEventListener(roomListener);
    }

    private void createBoardUI() {
        buttons = new Button[size][size];
        boardContainer.removeAllViews();

        for (int i = 0; i < size; i++) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            for (int j = 0; j < size; j++) {
                Button btn = new Button(this);
                btn.setLayoutParams(new LinearLayout.LayoutParams(100, 100));

                final int r = i, c = j;
                btn.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        if (controller != null) controller.onCellClicked(r, c);
                    }
                });
                btn.setOnLongClickListener(new View.OnLongClickListener() {
                    @Override
                    public boolean onLongClick(View v) {
                        if (controller != null) controller.onCellLongClicked(r, c);
                        return true;
                    }
                });
                buttons[i][j] = btn;
                row.addView(btn);
            }
            boardContainer.addView(row);
        }
        updateRemainingUI();
    }

    @Override
    public void updateStatus(String status) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                statusText.setText(status);
            }
        });
    }

    @Override
    public void updateCell(int r, int c, Cell cell) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Button btn = buttons[r][c];

                if (cell.isRevealed()) {
                    btn.setEnabled(false);
                    if (cell.getHasMine()) {
                        btn.setText("💣");
                        btn.setBackgroundColor(Color.RED);
                    } else {
                        btn.setBackgroundColor(Color.WHITE);
                        int n = cell.getNeighborMines();
                        btn.setText(n > 0 ? String.valueOf(n) : "");
                    }
                } else {
                    btn.setText(cell.isFlagged() ? "🚩" : "");
                }
                GameActivity.this.updateRemainingUI();
            }
        });
    }

    private void updateRemainingUI() {
        if (buttons == null) return;

        int closedButtonsCount = 0;
        for (int i = 0; i < size; i++) {
            for (int j = 0; j < size; j++) {
                if (buttons[i][j] != null && buttons[i][j].isEnabled()) {
                    closedButtonsCount++;
                }
            }
        }

        int safeCellsLeft = closedButtonsCount - bombsCount;
        if (safeCellsLeft < 0) {
            safeCellsLeft = 0;
        }
        tvCellsRemaining.setText("תאים ללא פצצות: " + safeCellsLeft);
    }

    public void setDynamicBombsCount(int actualBombs) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                GameActivity.this.bombsCount = actualBombs;
                GameActivity.this.updateRemainingUI();
            }
        });
    }

    @Override
    public void setBoardEnabled(boolean enabled) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                boardContainer.setAlpha(enabled ? 1.0f : 0.5f);
            }
        });
    }

    @Override
    public void showGameOver(boolean didIWin) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                overlayTitle.setText(didIWin ? "YOU WIN! 🎉" : "YOU LOSE! 💥");
                overlayTitle.setTextColor(didIWin ? Color.GREEN : Color.RED);

                if (didIWin) {
                    tvCellsRemaining.setText("תאים ללא פצצות: 0");

                    // --- תרחיש 1: ניצחון באופליין ---
                    if (!isOnline) {
                        SharedPreferences scoreSp = getSharedPreferences("OfflineScores", MODE_PRIVATE);

                        int currentWins = scoreSp.getInt("offline_wins", 0);
                        scoreSp.edit().putInt("offline_wins", currentWins + 1).apply();

                        if (controller instanceof OfflineGameController) {
                            OfflineGameController offlineController = (OfflineGameController) controller;
                            int finalTime = offlineController.getSecondsElapsed();

                            int currentHighScore = scoreSp.getInt("high_score", Integer.MAX_VALUE);

                            if (finalTime < currentHighScore && finalTime > 0) {
                                scoreSp.edit().putInt("high_score", finalTime).apply();
                                Toast.makeText(GameActivity.this, "New Offline Best Time: " + finalTime + "s! ⏱️", Toast.LENGTH_SHORT).show();
                            }
                        }

                        if (currentUser != null && !"Guest".equals(currentUser)) {
                            DatabaseReference leaderboardRef = FirebaseDatabase.getInstance().getReference("leaderboard").child(currentUser);

                            leaderboardRef.addListenerForSingleValueEvent(new ValueEventListener() {
                                @Override
                                public void onDataChange(@NonNull DataSnapshot snapshot) {
                                    long cloudScore = snapshot.hasChild("bestOfflineTime") ? snapshot.child("bestOfflineTime").getValue(Long.class) : Integer.MAX_VALUE;
                                    long currentCloudWins = snapshot.hasChild("offlineWins") ? snapshot.child("offlineWins").getValue(Long.class) : 0;

                                    java.util.HashMap<String, Object> updates = new java.util.HashMap<>();
                                    updates.put("username", currentUser);
                                    updates.put("offlineWins", currentCloudWins + 1);

                                    if (controller instanceof OfflineGameController) {
                                        int finalTime = ((OfflineGameController) controller).getSecondsElapsed();
                                        if (finalTime < cloudScore && finalTime > 0) {
                                            updates.put("bestOfflineTime", finalTime);
                                        }
                                    }

                                    leaderboardRef.updateChildren(updates);
                                }

                                @Override
                                public void onCancelled(@NonNull DatabaseError error) {}
                            });
                        }
                    }
                    // --- תרחיש 2: ניצחון באונליין ---
                    else {
                        if (currentUser != null && !"Guest".equals(currentUser)) {
                            DatabaseReference leaderboardRef = FirebaseDatabase.getInstance().getReference("leaderboard").child(currentUser);

                            leaderboardRef.addListenerForSingleValueEvent(new ValueEventListener() {
                                @Override
                                public void onDataChange(@NonNull DataSnapshot snapshot) {
                                    long currentOnlineWins = snapshot.hasChild("onlineWins") ? snapshot.child("onlineWins").getValue(Long.class) : 0;

                                    java.util.HashMap<String, Object> updates = new java.util.HashMap<>();
                                    updates.put("username", currentUser);
                                    updates.put("onlineWins", currentOnlineWins + 1);

                                    // שימוש ב-updateChildren מונע פגיעה בשדות אופליין קיימים
                                    leaderboardRef.updateChildren(updates);
                                }

                                @Override
                                public void onCancelled(@NonNull DatabaseError error) {}
                            });
                        }
                    }
                }

                overlay.setVisibility(View.VISIBLE);
                GameActivity.this.getSharedPreferences("SavedGame", MODE_PRIVATE).edit().putBoolean("hasSaved", false).apply();
            }
        });
    }

    @Override
    public void showMessage(String msg) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(GameActivity.this, msg, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void removePlayerFromRoom() {
        if (roomRef == null || currentUser == null) return;
        roomRef.child("players").addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (!snapshot.exists()) return;
                String p1 = snapshot.child("player1").getValue(String.class);
                String p2 = snapshot.child("player2").getValue(String.class);

                if (currentUser.equals(p1)) roomRef.child("players").child("player1").removeValue();
                else if (currentUser.equals(p2)) roomRef.child("players").child("player2").removeValue();

                roomRef.child("status").setValue("waiting");
            }
            @Override public void onCancelled(@NonNull DatabaseError error) {}
        });
    }

    private void detachChatListener() {
        if (chatRef != null && chatListener != null && chatListenerAdded) {
            chatRef.removeEventListener(chatListener);
            chatListenerAdded = false;
        }
    }

    private void setupChat() {
        if (chatAdapter == null) {
            chatAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, chatMessagesList);
        }
        chatListView.setAdapter(chatAdapter);

        if (!chatListenerAdded) {
            chatListenerAdded = true;
            chatListener = new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot snapshot) {
                    chatMessagesList.clear();
                    for (DataSnapshot doc : snapshot.getChildren()) {
                        ChatMessage msg = doc.getValue(ChatMessage.class);
                        if (msg != null) chatMessagesList.add(msg.getSender() + ": " + msg.getText());
                    }
                    chatAdapter.notifyDataSetChanged();
                    if (!chatMessagesList.isEmpty()) chatListView.setSelection(chatMessagesList.size() - 1);
                }
                @Override public void onCancelled(@NonNull DatabaseError error) {}
            };
            chatRef.addValueEventListener(chatListener);
        }

        btnSendChat.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String text = etChatMessage.getText().toString().trim();
                if (!text.isEmpty() && chatRef != null) {
                    chatRef.push().setValue(new ChatMessage(currentUser != null ? currentUser : "Player", text));
                    etChatMessage.setText("");
                }
            }
        });
    }
}