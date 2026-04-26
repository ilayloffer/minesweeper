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
    private String currentUser; // הועבר לכאן כדי שפונקציית הצ'אט תכיר אותו

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_game);

        // 1. קבלת כל הנתונים מה-Intent קודם כל!
        Intent intent = getIntent();
        size = intent.getIntExtra("size", 10);
        isOnline = intent.getBooleanExtra("isOnline", false);
        String gameId = intent.getStringExtra("gameId");
        currentUser = intent.getStringExtra("currentUser");
        String otherPlayer = intent.getStringExtra("otherPlayer");

        // 2. חיבור רכיבי ה-UI מה-XML
        statusText = findViewById(R.id.statusText);
        boardContainer = findViewById(R.id.boardContainer);
        overlay = findViewById(R.id.overlay);
        overlayTitle = findViewById(R.id.overlayTitle);
        btnHome = findViewById(R.id.btnHome);
        Button btnOpenChat = findViewById(R.id.btnOpenChat);
        // 4. יצירת הלוח החזותי
        createBoardUI();
        // 3. הגדרת צ'אט והקונטרולר לפי סוג המשחק
        if (isOnline) {
            // הנתיב: games -> [room id] -> chat
            chatRef = FirebaseDatabase.getInstance().getReference("games").child(gameId).child("chat");

            btnOpenChat.setVisibility(View.VISIBLE); // נראה רק באונליין
            btnOpenChat.setOnClickListener(v -> showChatDialog());

            // אתחול קונטרולר אונליין
            controller = new OnlineGameController(this, size, gameId, currentUser, otherPlayer);
        } else {
            // אם זה אופליין נסתיר את כפתור הצ'אט
            btnOpenChat.setVisibility(View.GONE);

            // אתחול קונטרולר אופליין
            controller = new OfflineGameController(this, size, currentUser);
        }

        // כפתור חזרה לתפריט הראשי בסיום המשחק
        btnHome.setOnClickListener(v -> finish());
    }

    private void createBoardUI() {
        buttons = new Button[size][size];
        boardContainer.removeAllViews();

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
        if (controller != null) {
            controller.onDestroy();
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

        // האזנה להודעות חדשות מה-Realtime Database
        chatRef.addValueEventListener(new ValueEventListener() {
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
        });

        // כפתור השליחה
        btnSendChat.setOnClickListener(v -> {
            String text = etChatMessage.getText().toString().trim();
            if (!text.isEmpty()) {
                // ה-currentUser נלקח מהמשתנה של המחלקה
                String senderName = (currentUser != null) ? currentUser : "Player";
                ChatMessage newMessage = new ChatMessage(senderName, text);

                // דחיפת ההודעה לענף ה-chat ב-Realtime Database
                chatRef.push().setValue(newMessage);

                // ניקוי שורת הטקסט אחרי השליחה
                etChatMessage.setText("");
            }
        });

        AlertDialog dialog = builder.create();
        dialog.show();
    }
}