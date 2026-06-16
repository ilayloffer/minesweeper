package com.example.minesweeper;

import android.app.AlertDialog;
import android.content.DialogInterface;
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

import com.google.android.gms.tasks.Task;
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

        startBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                int size = difficultySeek.getProgress() + 5;
                MainActivity.this.startGame(size, false);
            }
        });

        btnContinue.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                SharedPreferences sp = MainActivity.this.getSharedPreferences("SavedGame", MODE_PRIVATE);
                int savedSize = sp.getInt("size", 10);
                Intent intent = new Intent(MainActivity.this, GameActivity.class);
                intent.putExtra("size", savedSize);
                intent.putExtra("isOnline", false);
                intent.putExtra("loadSaved", true);
                intent.putExtra("currentUser", MainActivity.this.getPlayerName());
                MainActivity.this.startActivity(intent);
            }
        });

        btnOnlineMatch.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mAuth.getCurrentUser() == null) {
                    MainActivity.this.startActivity(new Intent(MainActivity.this, LoginActivity.class));
                } else {
                    MainActivity.this.showOnlineOptionsDialog();
                }
            }
        });

        btnLeaderboard.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent i = new Intent(MainActivity.this, LeaderboardActivity.class);
                i.putExtra("currentUser", MainActivity.this.getPlayerName());
                MainActivity.this.startActivity(i);
            }
        });

        btnInviteFriend.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                MainActivity.this.shareGame();
            }
        });
    }

    private void showOnlineOptionsDialog() {
        String[] options = {
                "1. Search for Players 🔍",
                "2. Game Code / Barcode 🔑",
                "3. Friends List 👥"
        };

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Online Game Options");
        builder.setItems(options, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                switch (which) {
                    case 0:
                        MainActivity.this.checkMatchmaking();
                        break;
                    case 1:
                        MainActivity.this.showCodeAndBarcodeDialog();
                        break;
                    case 2:
                        MainActivity.this.loadAndShowFriendsDialog();
                        break;
                }
            }
        });
        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

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
        btnScanQR.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                com.google.zxing.integration.android.IntentIntegrator integrator = new com.google.zxing.integration.android.IntentIntegrator(MainActivity.this);
                integrator.setDesiredBarcodeFormats(com.google.zxing.integration.android.IntentIntegrator.QR_CODE);
                integrator.setPrompt("Scan the Room QR Code");
                integrator.setCameraId(0);
                integrator.setBeepEnabled(true);
                integrator.setBarcodeImageEnabled(true);
                integrator.initiateScan();
            }
        });
        layout.addView(btnScanQR);

        builder.setView(layout);

        builder.setPositiveButton("Join Room", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                String roomCode = inputCode.getText().toString().trim();
                if (!roomCode.isEmpty()) {
                    MainActivity.this.checkAndJoinRoom(roomCode);
                } else {
                    Toast.makeText(MainActivity.this, "Please enter a valid code", Toast.LENGTH_SHORT).show();
                }
            }
        });

        builder.setNeutralButton("Create New Code", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                MainActivity.this.createNewRoom();
            }
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

        android.widget.ImageView qrImageView = new android.widget.ImageView(this);
        try {
            com.google.zxing.qrcode.QRCodeWriter writer = new com.google.zxing.qrcode.QRCodeWriter();
            com.google.zxing.common.BitMatrix bitMatrix = writer.encode(roomCode, com.google.zxing.BarcodeFormat.QR_CODE, 500, 500);
            com.journeyapps.barcodescanner.BarcodeEncoder barcodeEncoder = new com.journeyapps.barcodescanner.BarcodeEncoder();
            android.graphics.Bitmap bitmap = barcodeEncoder.createBitmap(bitMatrix);
            qrImageView.setImageBitmap(bitmap);
            qrImageView.setPadding(0, 40, 0, 0);
        } catch (com.google.zxing.WriterException e) {
            e.printStackTrace();
        }

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setGravity(android.view.Gravity.CENTER);

        TextView txtMessage = new TextView(this);
        txtMessage.setText("Give this code to your friend:\n\n " + roomCode + " \n\nOr let them scan this QR Code:");
        txtMessage.setTextSize(16);
        txtMessage.setGravity(android.view.Gravity.CENTER);
        txtMessage.setPadding(30, 30, 30, 10);

        layout.addView(txtMessage);
        layout.addView(qrImageView);

        AlertDialog.Builder codeDialog = new AlertDialog.Builder(this);
        codeDialog.setTitle("Room Created Successfully! 🎉");
        codeDialog.setView(layout);

        codeDialog.setPositiveButton("Start Game", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                Intent i = new Intent(MainActivity.this, GameActivity.class);
                i.putExtra("isOnline", true);
                i.putExtra("roomCode", roomCode);
                i.putExtra("gameId", roomCode); // התאמה לטעינת משחק אונליין
                i.putExtra("role", "host");
                i.putExtra("size", 10);
                i.putExtra("currentUser", MainActivity.this.getPlayerName());
                MainActivity.this.startActivity(i);
            }
        });

        codeDialog.setNegativeButton("Cancel Room", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                roomRef.removeValue();
                Toast.makeText(MainActivity.this, "Room cancelled", Toast.LENGTH_SHORT).show();
            }
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
                        i.putExtra("gameId", roomCode); // התאמה מלאה
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

    private void loadAndShowFriendsDialog() {
        FirebaseUser user = mAuth.getCurrentUser();
        if (user == null) return;

        DatabaseReference friendsRef = mDatabase.child("users").child(user.getUid()).child("friends");

        friendsRef.addListenerForSingleValueEvent(new ValueEventListener() {
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
                    builder.setMessage("Your friends list is empty.");
                } else {
                    String[] friendsArray = friendsList.toArray(new String[0]);
                    builder.setItems(friendsArray, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            String selectedFriendName = friendsArray[which];
                            MainActivity.this.sendInviteToFriend(selectedFriendName);
                        }
                    });
                }

                builder.setNeutralButton("Add Friend ➕", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        showAddFriendDialog();
                    }
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

    private void sendInviteToFriend(String friendName) {
        String roomCode = String.valueOf(new Random().nextInt(90000) + 10000);

        // תיקון קריטי: עבודה אחידה מול צומת rooms כדי שיתאים ל-GameActivity
        DatabaseReference roomRef = mDatabase.child("rooms").child(roomCode);

        roomRef.child("host").setValue(getPlayerName());
        roomRef.child("guest").setValue("");
        roomRef.child("status").setValue("waiting");

        DatabaseReference inviteRef = mDatabase.child("invitations").child(friendName);

        java.util.HashMap<String, Object> inviteData = new java.util.HashMap<>();
        inviteData.put("from", getPlayerName());
        inviteData.put("roomCode", roomCode);

        inviteRef.setValue(inviteData);

        Toast.makeText(this, "Invitation sent to " + friendName + "! Waiting for them to join...", Toast.LENGTH_LONG).show();

        waitForOpponent(roomCode);
    }

    private void listenForIncomingInvites() {
        String myName = getPlayerName();
        if ("Guest".equals(myName)) return;

        DatabaseReference myInviteRef = mDatabase.child("invitations").child(myName);

        myInviteRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    String fromPlayer = snapshot.child("from").getValue(String.class);
                    String roomCode = snapshot.child("roomCode").getValue(String.class);

                    myInviteRef.removeValue();

                    AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
                    builder.setTitle("Game Invitation! 🎮");
                    builder.setMessage("Your friend " + fromPlayer + " invited you to a game. Would you like to join?");
                    builder.setCancelable(false);

                    builder.setPositiveButton("Yes, Let's Play! ✅", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            // תיקון קריטי: החבר מעדכן את צומת rooms המדויק
                            mDatabase.child("rooms").child(roomCode).child("guest").setValue(getPlayerName());
                            mDatabase.child("rooms").child(roomCode).child("status").setValue("playing");

                            Intent i = new Intent(MainActivity.this, GameActivity.class);
                            i.putExtra("isOnline", true);
                            i.putExtra("roomCode", roomCode);
                            i.putExtra("gameId", roomCode);
                            i.putExtra("role", "guest");
                            i.putExtra("size", 10);
                            i.putExtra("currentUser", getPlayerName());
                            startActivity(i);
                        }
                    });

                    builder.setNegativeButton("No, Thanks ❌", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            mDatabase.child("rooms").child(roomCode).child("status").setValue("rejected");
                            dialog.dismiss();
                        }
                    });

                    builder.show();
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        });
    }

    private void showAddFriendDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Add New Friend");

        final EditText input = new EditText(this);
        input.setHint("Enter Friend's Username");
        builder.setView(input);

        builder.setPositiveButton("Add", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                String friendName = input.getText().toString().trim();
                FirebaseUser user = mAuth.getCurrentUser();

                if (!friendName.isEmpty() && user != null) {
                    mDatabase.child("users").child(user.getUid()).child("friends").push().setValue(friendName)
                            .addOnCompleteListener((Task<Void> task) -> {
                                if (task.isSuccessful()) {
                                    Toast.makeText(MainActivity.this, friendName + " added to your friends list!", Toast.LENGTH_SHORT).show();
                                } else {
                                    Toast.makeText(MainActivity.this, "Failed to add friend", Toast.LENGTH_SHORT).show();
                                }
                            });
                } else {
                    Toast.makeText(MainActivity.this, "Name cannot be empty", Toast.LENGTH_SHORT).show();
                }
            }
        });

        builder.setNeutralButton("Share Invite Link 🔗", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                MainActivity.this.shareGame();
            }
        });
        builder.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                MainActivity.this.loadAndShowFriendsDialog();
            }
        });
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

        listenForIncomingInvites();

        SharedPreferences settingsSp = getSharedPreferences("AppSettings", MODE_PRIVATE);
        String theme = settingsSp.getString("theme", "Light");
        String bgUriString = settingsSp.getString("bgUri", null);

        View myRootLayout = findViewById(R.id.main_layout);

        if (myRootLayout != null) {
            if ("Dark".equals(theme)) {
                myRootLayout.setBackgroundColor(android.graphics.Color.parseColor("#212121"));
            } else {
                myRootLayout.setBackgroundColor(android.graphics.Color.parseColor("#FFFFFF"));
            }

            if (bgUriString != null) {
                try {
                    android.net.Uri bgUri = android.net.Uri.parse(bgUriString);
                    java.io.InputStream inputStream = getContentResolver().openInputStream(bgUri);
                    android.graphics.drawable.Drawable drawable = android.graphics.drawable.Drawable.createFromStream(inputStream, bgUri.toString());
                    myRootLayout.setBackground(drawable);
                } catch (Exception e) {
                    e.printStackTrace();
                    Toast.makeText(this, "Failed to load custom background", Toast.LENGTH_SHORT).show();
                }
            }
        }
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
            Intent intent = new Intent(MainActivity.this, SettingsActivity.class);
            startActivity(intent);
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

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        com.google.zxing.integration.android.IntentResult result = com.google.zxing.integration.android.IntentIntegrator.parseActivityResult(requestCode, resultCode, data);
        if (result != null) {
            if (result.getContents() == null) {
                Toast.makeText(this, "Scanning cancelled", Toast.LENGTH_LONG).show();
            } else {
                String scannedRoomCode = result.getContents().trim();
                Toast.makeText(this, "Scanned Code: " + scannedRoomCode, Toast.LENGTH_SHORT).show();
                checkAndJoinRoom(scannedRoomCode);
            }
        } else {
            super.onActivityResult(requestCode, resultCode, data);
        }
    }

    private void checkMatchmaking() {
        com.google.firebase.auth.FirebaseUser currentUser = com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null) return;

        String currentUserName = getPlayerName(); // משתמשים בשם התצוגה המלא באופן עקבי

        com.google.firebase.database.DatabaseReference matchRef = com.google.firebase.database.FirebaseDatabase.getInstance().getReference("matchmaking").child("waiting_player");
        com.google.firebase.database.DatabaseReference roomsRef = com.google.firebase.database.FirebaseDatabase.getInstance().getReference("rooms");

        matchRef.addListenerForSingleValueEvent(new com.google.firebase.database.ValueEventListener() {
            @Override
            public void onDataChange(com.google.firebase.database.DataSnapshot snapshot) {
                if (!snapshot.exists()) {
                    String newRoomCode = roomsRef.push().getKey();

                    java.util.HashMap<String, Object> waitingData = new java.util.HashMap<>();
                    waitingData.put("playerName", currentUserName);
                    waitingData.put("gameId", newRoomCode);

                    matchRef.setValue(waitingData);

                    android.widget.Toast.makeText(MainActivity.this, "Searching for player...", android.widget.Toast.LENGTH_SHORT).show();
                    waitForOpponent(newRoomCode);

                } else {
                    String existingRoomCode = snapshot.child("gameId").getValue(String.class);
                    String player1Name = snapshot.child("playerName").getValue(String.class);

                    matchRef.removeValue();

                    // הגדרת החדר אונליין בצורה אחידה
                    roomsRef.child(existingRoomCode).child("host").setValue(player1Name);
                    roomsRef.child(existingRoomCode).child("guest").setValue(currentUserName);
                    roomsRef.child(existingRoomCode).child("status").setValue("playing");

                    Intent intent = new Intent(MainActivity.this, GameActivity.class);
                    intent.putExtra("isOnline", true);
                    intent.putExtra("roomCode", existingRoomCode);
                    intent.putExtra("gameId", existingRoomCode);
                    intent.putExtra("role", "guest");
                    intent.putExtra("size", 10);
                    intent.putExtra("currentUser", currentUserName);
                    startActivity(intent);
                }
            }

            @Override
            public void onCancelled(com.google.firebase.database.DatabaseError error) {}
        });
    }

    private void waitForOpponent(String roomCode) {
        // תיקון קריטי: האזנה לצומת rooms המאוחד
        com.google.firebase.database.DatabaseReference roomRef = com.google.firebase.database.FirebaseDatabase.getInstance().getReference("rooms").child(roomCode);
        roomRef.child("status").addValueEventListener(new com.google.firebase.database.ValueEventListener() {
            @Override
            public void onDataChange(com.google.firebase.database.DataSnapshot snapshot) {
                String status = snapshot.getValue(String.class);
                if ("playing".equals(status)) {
                    roomRef.child("status").removeEventListener(this);

                    Intent intent = new Intent(MainActivity.this, GameActivity.class);
                    intent.putExtra("isOnline", true);
                    intent.putExtra("roomCode", roomCode);
                    intent.putExtra("gameId", roomCode);
                    intent.putExtra("role", "host");
                    intent.putExtra("size", 10);
                    intent.putExtra("currentUser", getPlayerName());
                    startActivity(intent);
                }
            }

            @Override
            public void onCancelled(com.google.firebase.database.DatabaseError error) {}
        });
    }
}