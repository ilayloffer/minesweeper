package com.example.minesweeper;

import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

public class DBRef {

    private static final DatabaseReference ROOT =
            FirebaseDatabase.getInstance().getReference();

    public static final DatabaseReference USERS =
            ROOT.child("users");

    public static final DatabaseReference SCORES =
            ROOT.child("scores");

    public static final DatabaseReference USERNAMES =
            ROOT.child("usernames");
}