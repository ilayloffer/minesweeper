package com.example.minesweeper;

import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class MainActivity extends AppCompatActivity {

    private SeekBar difficultySeek;
    private TextView difficultyLabel, tvWelcome;
    private Button startBtn, btnOnlineMatch, btnLeaderboard, btnContinue, btnInviteFriend;

    private FirebaseAuth mAuth;
    private DatabaseReference mDatabase;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mAuth = FirebaseAuth.getInstance();
        mDatabase = FirebaseDatabase.getInstance().getReference();

        // אתחול רכיבים
        difficultySeek = findViewById(R.id.difficultySeek);
        difficultyLabel = findViewById(R.id.difficultyLabel);
        tvWelcome = findViewById(R.id.tvWelcome);
        startBtn = findViewById(R.id.startBtn);
        btnOnlineMatch = findViewById(R.id.btnOnlineMatch);
        btnLeaderboard = findViewById(R.id.btnLeaderboard);
        btnContinue = findViewById(R.id.btnContinue);
        btnInviteFriend = findViewById(R.id.btnInviteFriend);

        handleIncomingInvite(getIntent());
        setupListeners();
    }

    private void setupListeners() {
        difficultySeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int size = progress + 5;
                difficultyLabel.setText("Board Size: " + size + " x " + size);
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        startBtn.setOnClickListener(v -> {
            int size = difficultySeek.getProgress() + 5;
            startGame(size, false);
        });

        btnContinue.setOnClickListener(v -> {
            SharedPreferences sp = getSharedPreferences("SavedGame", MODE_PRIVATE);
            int savedSize = sp.getInt("size", 10);
            Intent intent = new Intent(this, GameActivity.class);
            intent.putExtra("size", savedSize);
            intent.putExtra("isOnline", false);
            intent.putExtra("loadSaved", true);
            intent.putExtra("currentUser", getPlayerName());
            startActivity(intent);
        });

        btnOnlineMatch.setOnClickListener(v -> {
            if (mAuth.getCurrentUser() == null) {
                startActivity(new Intent(this, LoginActivity.class));
            } else {
                showOnlineOptionsDialog();
            }
        });

        btnLeaderboard.setOnClickListener(v -> {
            Intent i = new Intent(this, LeaderboardActivity.class);
            i.putExtra("currentUser", getPlayerName());
            startActivity(i);
        });

        btnInviteFriend.setOnClickListener(v -> shareGame());
    }

    // --- הדיאלוג הראשי (כעת מכיל רק 3 אופציות) ---
    private void showOnlineOptionsDialog() {
        String[] options = {
                "1. Search for Players 🔍",
                "2. Game Code / Barcode 🔑",
                "3. Friends List 👥" // לחיצה כאן תציג את החברים ואת כפתור ההוספה
        };

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Online Game Options");
        builder.setItems(options, (dialog, which) -> {
            switch (which) {
                case 0:
                    Toast.makeText(this, "Searching for online match...", Toast.LENGTH_SHORT).show();
                    break;
                case 1:
                    showCodeAndBarcodeDialog();
                    break;
                case 2:
                    loadAndShowFriendsDialog();
                    break;
            }
        });
        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    // --- אופציה 2: קוד חדר וברקוד מול Firebase ---
    private void showCodeAndBarcodeDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Join or Create with Code");

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(50, 40, 50, 10);

        final EditText inputCode = new EditText(this);
        inputCode.setHint("Enter Room Code here");
        layout.addView(inputCode);

        Button btnScanQR = new Button(this);
        btnScanQR.setText("Scan QR / Barcode 📷");
        btnScanQR.setOnClickListener(v -> {
            Toast.makeText(this, "Opening Camera Scanner...", Toast.LENGTH_SHORT).show();
        });
        layout.addView(btnScanQR);

        builder.setView(layout);

        builder.setPositiveButton("Join Room", (dialog, which) -> {
            String roomCode = inputCode.getText().toString().trim();
            if (!roomCode.isEmpty()) {
                checkAndJoinRoom(roomCode);
            } else {
                Toast.makeText(this, "Please enter a valid code", Toast.LENGTH_SHORT).show();
            }
        });

        builder.setNeutralButton("Create New Code", (dialog, which) -> {
            createNewRoom();
        });

        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    private void createNewRoom() {
        String roomCode = String.valueOf(new Random().nextInt(90000) + 10000);
        DatabaseReference roomRef = mDatabase.child("rooms").child(roomCode);

        roomRef.child("host").setValue(getPlayerName());
        roomRef.child("guest").setValue("");
        roomRef.child("status").setValue("waiting");

        AlertDialog.Builder codeDialog = new AlertDialog.Builder(this);
        codeDialog.setTitle("Room Created Successfully! 🎉");
        codeDialog.setMessage("Give this code to your friend:\n\n👉 " + roomCode + " 👈\n\nClick 'Start' when they are ready to connect.");

        codeDialog.setPositiveButton("Start Game", (dialog, which) -> {
            Intent i = new Intent(MainActivity.this, GameActivity.class);
            i.putExtra("isOnline", true);
            i.putExtra("roomCode", roomCode);
            i.putExtra("role", "host");
            i.putExtra("size", 10);
            i.putExtra("currentUser", getPlayerName());
            startActivity(i);
        });

        codeDialog.setNegativeButton("Cancel Room", (dialog, which) -> {
            roomRef.removeValue();
            Toast.makeText(MainActivity.this, "Room cancelled", Toast.LENGTH_SHORT).show();
        });

        codeDialog.setCancelable(false);
        codeDialog.show();
    }

    private void checkAndJoinRoom(String roomCode) {
        mDatabase.child("rooms").child(roomCode).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    String status = snapshot.child("status").getValue(String.class);
                    if ("waiting".equals(status)) {
                        mDatabase.child("rooms").child(roomCode).child("guest").setValue(getPlayerName());
                        mDatabase.child("rooms").child(roomCode).child("status").setValue("playing");

                        Intent i = new Intent(MainActivity.this, GameActivity.class);
                        i.putExtra("isOnline", true);
                        i.putExtra("roomCode", roomCode);
                        i.putExtra("role", "guest");
                        i.putExtra("size", 10);
                        i.putExtra("currentUser", getPlayerName());
                        startActivity(i);
                    } else {
                        Toast.makeText(MainActivity.this, "Room is full or finished", Toast.LENGTH_SHORT).show();
                    }
                } else {
                    Toast.makeText(MainActivity.this, "Room code does not exist", Toast.LENGTH_SHORT).show();
                }
            }

            @Override public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(MainActivity.this, "Database error: " + error.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    // --- אופציה 3: רשימת חברים + כפתור הוספת חבר מובנה בפנים ---
    private void loadAndShowFriendsDialog() {
        FirebaseUser user = mAuth.getCurrentUser();
        if (user == null) return;

        mDatabase.child("users").child(user.getUid()).child("friends").addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                List<String> friendsList = new ArrayList<>();
                for (DataSnapshot friendSnap : snapshot.getChildren()) {
                    String friendName = friendSnap.getValue(String.class);
                    if (friendName != null) {
                        friendsList.add(friendName);
                    }
                }

                AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
                builder.setTitle("Friends List 👥");

                if (friendsList.isEmpty()) {
                    // אם הרשימה ריקה, נציג טקסט מתאים
                    builder.setMessage("Your friends list is empty.");
                } else {
                    // אם יש חברים, נציג אותם לבחירה
                    String[] friendsArray = friendsList.toArray(new String[0]);
                    builder.setItems(friendsArray, (dialog, which) -> {
                        String selectedFriend = friendsArray[which];
                        Toast.makeText(MainActivity.this, "Inviting " + selectedFriend + "...", Toast.LENGTH_SHORT).show();
                        createNewRoom();
                    });
                }

                // הוספת כפתור "הוסף חבר חדש" בתחתית הדיאלוג של הרשימה!
                builder.setNeutralButton("Add Friend ➕", (dialog, which) -> {
                    showAddFriendDialog();
                });

                builder.setNegativeButton("Close", null);
                builder.show();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(MainActivity.this, "Failed to load friends", Toast.LENGTH_SHORT).show();
            }
        });
    }

    // --- חלון הוספת חבר (נפתח כעת רק מתוך רשימת החברים) ---
    private void showAddFriendDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Add New Friend");

        final EditText input = new EditText(this);
        input.setHint("Enter Friend's Username");
        builder.setView(input);

        builder.setPositiveButton("Add", (dialog, which) -> {
            String friendName = input.getText().toString().trim();
            FirebaseUser user = mAuth.getCurrentUser();

            if (!friendName.isEmpty() && user != null) {
                mDatabase.child("users").child(user.getUid()).child("friends").push().setValue(friendName)
                        .addOnCompleteListener(task -> {
                            if (task.isSuccessful()) {
                                Toast.makeText(MainActivity.this, friendName + " added to your friends list!", Toast.LENGTH_SHORT).show();
                            } else {
                                Toast.makeText(MainActivity.this, "Failed to add friend", Toast.LENGTH_SHORT).show();
                            }
                        });
            } else {
                Toast.makeText(this, "Name cannot be empty", Toast.LENGTH_SHORT).show();
            }
        });

        builder.setNeutralButton("Share Invite Link 🔗", (dialog, which) -> shareGame());
        builder.setNegativeButton("Cancel", (dialog, which) -> loadAndShowFriendsDialog()); // חוזר חזרה לרשימה
        builder.show();
    }

    private void startGame(int size, boolean loadSaved) {
        Intent i = new Intent(this, GameActivity.class);
        i.putExtra("size", size);
        i.putExtra("isOnline", false);
        i.putExtra("loadSaved", loadSaved);
        i.putExtra("currentUser", getPlayerName());
        startActivity(i);
    }

    private String getPlayerName() {
        FirebaseUser user = mAuth.getCurrentUser();
        if (user != null) {
            return user.getDisplayName() != null ? user.getDisplayName() : user.getEmail().split("@")[0];
        }
        return "Guest";
    }

    @Override
    protected void onResume() {
        super.onResume();
        tvWelcome.setText("Welcome, " + getPlayerName() + "!");
        SharedPreferences sp = getSharedPreferences("SavedGame", MODE_PRIVATE);
        boolean hasSaved = sp.getBoolean("hasSaved", false);
        btnContinue.setVisibility(hasSaved ? View.VISIBLE : View.GONE);
        invalidateOptionsMenu();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIncomingInvite(intent);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main_menu, menu);
        boolean isLoggedIn = mAuth.getCurrentUser() != null;
        menu.findItem(R.id.menu_login).setVisible(!isLoggedIn);
        menu.findItem(R.id.menu_register).setVisible(!isLoggedIn);
        menu.findItem(R.id.menu_logout).setVisible(isLoggedIn);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.menu_login) {
            startActivity(new Intent(this, LoginActivity.class));
            return true;
        }
        else if (id == R.id.menu_register) {
            startActivity(new Intent(this, RegisterActivity.class));
            return true;
        }
        else if (id == R.id.menu_logout) {
            mAuth.signOut();
            recreate();
            return true;
        }
        else if (id == R.id.leaderboard) {
            Intent intent = new Intent(this, LeaderboardActivity.class);
            intent.putExtra("currentUser", getPlayerName());
            startActivity(intent);
            return true;
        }
        else if (id == R.id.menu_settings) {
            Toast.makeText(this, "Settings clicked", Toast.LENGTH_SHORT).show();
            return true;
        }
        else if (id == R.id.action_music) {
            item.setChecked(!item.isChecked());
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void shareGame() {
        String deepLink = "https://minesweeper-app.com/invite?user=" + getPlayerName();
        String message = "Hey! Come play Minesweeper with me. Click this link to join: " + deepLink;
        Intent sendIntent = new Intent();
        sendIntent.setAction(Intent.ACTION_SEND);
        sendIntent.putExtra(Intent.EXTRA_TEXT, message);
        sendIntent.setType("text/plain");
        Intent shareIntent = Intent.createChooser(sendIntent, "Invite friend via:");
        startActivity(shareIntent);
    }

    private void handleIncomingInvite(Intent intent) {
        String action = intent.getAction();
        android.net.Uri data = intent.getData();
        if (Intent.ACTION_VIEW.equals(action) && data != null) {
            String invitedBy = data.getQueryParameter("user");
            if (invitedBy != null) {
                Toast.makeText(this, "You were invited by: " + invitedBy, Toast.LENGTH_LONG).show();
            }
        }
    }
}