package com.example.minesweeper;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
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
    private LinearLayout boardContainer;
    private View overlay;
    private TextView overlayTitle;
    private Button btnHome;

    // משתני הצ'אט והמשחק
    private DatabaseReference chatRef;
    private List<String> chatMessagesList = new ArrayList<>();
    private ArrayAdapter<String> chatAdapter;
    private GameController controller;
    private Button[][] buttons;
    private int size;
    private boolean isOnline;
    DatabaseReference roomRef;
    String currentUser;
    String otherPlayer;
    String roomId;
    boolean gameStarted;
    private boolean chatListenerAdded;
    private ValueEventListener chatListener;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_game);

        // 2. חיבור רכיבי ה-UI מה-XML
        statusText = findViewById(R.id.statusText);
        boardContainer = findViewById(R.id.boardContainer);
        overlay = findViewById(R.id.overlay);
        overlayTitle = findViewById(R.id.overlayTitle);
        btnHome = findViewById(R.id.btnHome);
        Button btnOpenChat = findViewById(R.id.btnOpenChat);

        gameStarted = false;
        chatListenerAdded = false;

        // 1. קבלת כל הנתונים מה-Intent קודם כל!
        Intent intent = getIntent();

        size = intent.getIntExtra("size", 10);
        isOnline = intent.getBooleanExtra("isOnline", false);
        roomId = intent.getStringExtra("gameId");
        currentUser = intent.getStringExtra("currentUser");

        roomRef = FirebaseDatabase.getInstance()
                .getReference("rooms")
                .child(roomId);

        // 2. קביעה מי אני
        roomRef.child("players").addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                String p1 = snapshot.child("player1").getValue(String.class);
                String p2 = snapshot.child("player2").getValue(String.class);

                if (currentUser != null && currentUser.equals(p1)) {
                    setupDisconnect("player1");
                } else if (currentUser != null && currentUser.equals(p2)) {
                    setupDisconnect("player2");
                }
            }

            @Override
            public void onCancelled(DatabaseError error) {}
        });

        // 3. listener למשחק
        roomRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {

                String player1 = snapshot.child("players").child("player1").getValue(String.class);
                String player2 = snapshot.child("players").child("player2").getValue(String.class);

                if (player1 == null || player2 == null) {
                    if (gameStarted) {
                        Toast.makeText(GameActivity.this, "Opponent left", Toast.LENGTH_SHORT).show();
                        finish();
                    } else {
                        statusText.setText("Waiting for opponent...");
                    }
                    return;
                }

                // 🔥 קביעת היריב
                if (currentUser != null && player1 != null && currentUser.equals(player1)) {
                    otherPlayer = player2;
                } else if (currentUser != null && player2 != null && currentUser.equals(player2)) {
                    otherPlayer = player1;
                } else {
                    otherPlayer = null; // או טיפול חריג
                }

                // 🔥 יצירת משחק רק פעם אחת
                ///////if (controller == null) {
                if (!gameStarted && currentUser != null && player1 != null && player2 != null) {
                    gameStarted = true;

                    if (currentUser.equals(player1)) {
                        otherPlayer = player2;
                    } else {
                        otherPlayer = player1;
                    }

                    runOnUiThread(() -> {
                        statusText.setText("Game started vs " + otherPlayer);

                        controller = new OnlineGameController(
                                GameActivity.this,
                                size,
                                roomId,
                                currentUser,
                                otherPlayer
                        );

                        createBoardUI();
                    });
                }
                }
            /////////}

            @Override
            public void onCancelled(DatabaseError error) {}
        });


        Log.d("Rinat", "currentUser: " + currentUser);


        // 4. יצירת הלוח החזותי
        ////////////////////////createBoardUI();
        // 3. הגדרת צ'אט והקונטרולר לפי סוג המשחק
        if (isOnline) {
            chatRef = FirebaseDatabase.getInstance()
                    .getReference("games")
                    .child(roomId)
                    .child("chat");

            btnOpenChat.setVisibility(View.VISIBLE);
            btnOpenChat.setOnClickListener(v -> showChatDialog());

        } else {
            btnOpenChat.setVisibility(View.GONE);
            controller = new OfflineGameController(this, size, currentUser);
            createBoardUI();
        }

        // כפתור חזרה לתפריט הראשי בסיום המשחק
        btnHome.setOnClickListener(v -> finish());
    }

    /*
    private void startGameIfReady(String p1, String p2) {
        if (p1 != null && p2 != null) {
            // פה תתחיל את הלוח
            createBoardUI();
        }
    }
    */

    private void setupDisconnect(String playerKey) {
        DatabaseReference playerRef = roomRef.child("players").child(playerKey);

        // אם השחקן מתנתק (אינטרנט / סוגר אפליקציה)
        playerRef.onDisconnect().removeValue();

        // אפשר גם לעדכן סטטוס
        roomRef.child("status").onDisconnect().setValue("waiting");
    }

    private void createBoardUI() {
        buttons = new Button[size][size];
        boardContainer.removeAllViews();

        ///////////?gameStarted = true;

        for (int i = 0; i < size; i++) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            for (int j = 0; j < size; j++) {
                Button btn = new Button(this);
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(100, 100);
                params.setMargins(2, 2, 2, 2);
                btn.setLayoutParams(params);
                btn.setBackgroundColor(Color.LTGRAY);

                final int r = i;
                final int c = j;

                btn.setOnClickListener(v -> {
                    if (controller != null) controller.onCellClicked(r, c);
                });

                btn.setOnLongClickListener(v -> {
                    if (controller != null) controller.onCellLongClicked(r, c);
                    return true;
                });

                buttons[i][j] = btn;
                row.addView(btn);
            }
            boardContainer.addView(row);
        }
    }

    // --- מימוש פונקציות ה-GameView ---

    @Override
    public void updateStatus(String status) {
        // פונקציה זו קריטית! היא מעדכנת את הטיימר שמופיע במסך
        runOnUiThread(() -> statusText.setText(status));
    }

    @Override
    public void updateCell(int r, int c, Cell cell) {
        runOnUiThread(() -> {
            Button btn = buttons[r][c];
            if (cell.isRevealed()) {
                btn.setEnabled(false);
                if (cell.getHasMine()) {
                    btn.setText("💣");
                    btn.setBackgroundColor(Color.RED);
                } else {
                    btn.setBackgroundColor(Color.WHITE);
                    int neighbors = cell.getNeighborMines();
                    if (neighbors > 0) {
                        btn.setText(String.valueOf(neighbors));
                    } else {
                        btn.setText("");
                    }
                }
            } else if (cell.isFlagged()) {
                btn.setText("🚩");
            } else {
                btn.setText("");
                btn.setEnabled(true);
            }
        });
    }

    @Override
    public void setBoardEnabled(boolean enabled) {
        // מונע או מאפשר לחיצה על הלוח בהתאם לתור
        runOnUiThread(() -> boardContainer.setAlpha(enabled ? 1.0f : 0.5f));
    }

    @Override
    public void showGameOver(boolean didIWin) {
        runOnUiThread(() -> {
            overlayTitle.setText(didIWin ? "YOU WIN! 🎉" : "YOU LOSE! 💥");
            overlayTitle.setTextColor(didIWin ? Color.GREEN : Color.RED);
            overlay.setVisibility(View.VISIBLE);
        });
    }

    @Override
    public void showMessage(String msg) {
        runOnUiThread(() -> Toast.makeText(GameActivity.this, msg, Toast.LENGTH_SHORT).show());
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        removePlayer();

        if (chatRef != null && chatListener != null) {
            chatRef.removeEventListener(chatListener);
        }

        if (controller != null) {
            controller.onDestroy();
        }

        detachChatListener();
    }

    private void removePlayer() {
        roomRef.child("players").addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                if (!snapshot.exists()) return;

                String p1 = snapshot.child("player1").getValue(String.class);
                String p2 = snapshot.child("player2").getValue(String.class);

                if (currentUser != null &&currentUser.equals(p1)) {
                    roomRef.child("players").child("player1").removeValue();
                } else if (currentUser != null &&currentUser.equals(p2)) {
                    roomRef.child("players").child("player2").removeValue();
                }

                roomRef.child("status").setValue("waiting");
            }

            @Override
            public void onCancelled(DatabaseError error) {}
        });
    }

    private void detachChatListener() {
        if (chatRef != null && chatListener != null) {
            chatRef.removeEventListener(chatListener);
            chatListenerAdded = false;
        }
    }

    // --- פונקציית הצ'אט ---

    private void showChatDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        // טעינת העיצוב שיצרנו
        View chatView = getLayoutInflater().inflate(R.layout.dialog_chat, null);
        builder.setView(chatView);

        ListView listViewChat = chatView.findViewById(R.id.listViewChat);
        EditText etChatMessage = chatView.findViewById(R.id.etChatMessage);
        Button btnSendChat = chatView.findViewById(R.id.btnSendChat);

        // יצירת אדפטר פשוט לרשימה
        chatAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, chatMessagesList);
        listViewChat.setAdapter(chatAdapter);

        if (isOnline && chatRef != null && !chatListenerAdded) {
            chatListenerAdded = true;            // האזנה להודעות חדשות מה-Realtime Database

            chatListener = new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot snapshot) {
                    chatMessagesList.clear();
                    for (DataSnapshot doc : snapshot.getChildren()) {
                        ChatMessage msg = doc.getValue(ChatMessage.class);
                        if (msg != null) {
                            // חיבור שם השולח וההודעה למחרוזת אחת שתופיע ברשימה
                            chatMessagesList.add(msg.getSender() + ": " + msg.getText());
                        }
                    }
                    chatAdapter.notifyDataSetChanged();

                    // גלילה אוטומטית להודעה האחרונה
                    if (!chatMessagesList.isEmpty()) {
                        listViewChat.setSelection(chatMessagesList.size() - 1);
                    }
                }

                @Override
                public void onCancelled(@NonNull DatabaseError error) {
                    Toast.makeText(GameActivity.this, "Chat sync failed", Toast.LENGTH_SHORT).show();
                }
            };

            chatRef.addValueEventListener(chatListener);
        }

        // כפתור השליחה
        btnSendChat.setOnClickListener(v -> {
            String text = etChatMessage.getText().toString().trim();
            if (!text.isEmpty()) {
                // ה-currentUser נלקח מהמשתנה של המחלקה
                String senderName = (currentUser != null) ? currentUser : "Player";
                ChatMessage newMessage = new ChatMessage(senderName, text);

                // דחיפת ההודעה לענף ה-chat ב-Realtime Database
                if (chatRef != null) {
                    chatRef.push().setValue(newMessage);
                }

                // ניקוי שורת הטקסט אחרי השליחה
                etChatMessage.setText("");
            }
        });

        AlertDialog dialog = builder.create();
        dialog.show();
    }
}