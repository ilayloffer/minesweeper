package com.example.minesweeper;

import android.content.Intent;
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
    private LinearLayout boardContainer;
    private View overlay;
    private TextView overlayTitle;
    private Button btnHome;

    // --- רכיבי הצ'אט החדשים ---
    private LinearLayout chatContainer;
    private ListView chatListView;
    private EditText etChatMessage;
    private Button btnSendChat;

    private GameController controller;
    private Button[][] buttons;
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

        if (isOnline) {
            initOnlineGame();
        } else {
            initOfflineGame();
        }

        btnHome.setOnClickListener(v -> finish());
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
        statusText     = findViewById(R.id.statusText);
        boardContainer = findViewById(R.id.boardContainer);
        overlay        = findViewById(R.id.overlay);
        overlayTitle   = findViewById(R.id.overlayTitle);
        btnHome        = findViewById(R.id.btnHome);

        // קישור רכיבי הצ'אט מה-XML
        chatContainer  = findViewById(R.id.chatContainer);
        chatListView   = findViewById(R.id.chatListView);
        etChatMessage  = findViewById(R.id.etChatMessage);
        btnSendChat    = findViewById(R.id.btnSendChat);
    }

    private void readIntent() {
        Intent intent = getIntent();
        size        = intent.getIntExtra("size", 10);
        isOnline    = intent.getBooleanExtra("isOnline", false);
        roomId      = intent.getStringExtra("gameId");
        currentUser = intent.getStringExtra("currentUser");
    }

    private void initOfflineGame() {
        // במשחק רגיל (אופליין) אנחנו מסתירים את אזור הצ'אט לגמרי
        if (chatContainer != null) {
            chatContainer.setVisibility(View.GONE);
        }
        controller = new OfflineGameController(this, size, currentUser);
        createBoardUI();
    }

    private void initOnlineGame() {
        // במשחק אונליין מוודאים שהצ'אט מוצג
        if (chatContainer != null) {
            chatContainer.setVisibility(View.VISIBLE);
        }

        roomRef = FirebaseDatabase.getInstance().getReference("rooms").child(roomId);
        chatRef = FirebaseDatabase.getInstance().getReference("games").child(roomId).child("chat");

        setupDisconnectHook();
        listenForRoomChanges();
        setupChat(); // מפעילים את הלוגיקה של הצ'אט ישירות על המסך
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
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                String player1 = snapshot.child("players").child("player1").getValue(String.class);
                String player2 = snapshot.child("players").child("player2").getValue(String.class);

                if (player1 == null || player2 == null) {
                    if (gameStarted) { finish(); }
                    else { statusText.setText("Waiting for opponent..."); }
                    return;
                }

                if (!gameStarted) {
                    gameStarted = true;
                    otherPlayer = currentUser.equals(player1) ? player2 : player1;

                    runOnUiThread(() -> {
                        statusText.setText("Game started vs " + otherPlayer);
                        controller = new OnlineGameController(GameActivity.this, size, roomId, currentUser, otherPlayer);
                        createBoardUI();
                    });
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
                btn.setOnClickListener(v -> { if (controller != null) controller.onCellClicked(r, c); });
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

    @Override
    public void updateStatus(String status) {
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
                    int n = cell.getNeighborMines();
                    btn.setText(n > 0 ? String.valueOf(n) : "");
                }
            } else {
                btn.setText(cell.isFlagged() ? "🚩" : "");
            }
        });
    }

    @Override
    public void setBoardEnabled(boolean enabled) {
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
        runOnUiThread(() -> Toast.makeText(this, msg, Toast.LENGTH_SHORT).show());
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

    // --- לוגיקת הצ'אט החדשה המשולבת במסך ---
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
                    // גלילה אוטומטית להודעה האחרונה
                    if (!chatMessagesList.isEmpty()) chatListView.setSelection(chatMessagesList.size() - 1);
                }
                @Override public void onCancelled(@NonNull DatabaseError error) {}
            };
            chatRef.addValueEventListener(chatListener);
        }

        btnSendChat.setOnClickListener(v -> {
            String text = etChatMessage.getText().toString().trim();
            if (!text.isEmpty() && chatRef != null) {
                chatRef.push().setValue(new ChatMessage(currentUser != null ? currentUser : "Player", text));
                etChatMessage.setText(""); // ניקוי השורה אחרי השליחה
            }
        });
    }
}