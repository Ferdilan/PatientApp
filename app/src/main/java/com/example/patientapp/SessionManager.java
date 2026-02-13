package com.example.patientapp;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.HashMap;

public class SessionManager {
    SharedPreferences pref;
    SharedPreferences.Editor editor;
    Context _context;

    int PRIVATE_MODE = 0;
    private static final String PREF_NAME = "PatientAppPref";
    private static final String IS_LOGIN = "IsLoggedIn";

    // Data User
    public static final String KEY_ID = "123";
    public static final String KEY_NAMA = "nama";
    public static final String KEY_NIK = "nik";

    public SessionManager(Context context){
        this._context = context;
        pref = _context.getSharedPreferences(PREF_NAME, PRIVATE_MODE);
        editor = pref.edit();
    }

    public void createLoginSession(String id, String nama, String nik){
        editor.putBoolean(IS_LOGIN, true);
        editor.putString(KEY_ID, id);
        editor.putString(KEY_NAMA, nama);
        editor.putString(KEY_NIK, nik);
        editor.commit();
    }

    public boolean isLoggedIn(){
        return pref.getBoolean(IS_LOGIN, false);
    }

    public HashMap<String, String> getUserDetails(){
        HashMap<String, String> user = new HashMap<>();
        user.put(KEY_ID, pref.getString(KEY_ID, null));
        user.put(KEY_NAMA, pref.getString(KEY_NAMA, null));
        user.put(KEY_NIK, pref.getString(KEY_NIK, null));
        return user;
    }

    public void logoutUser(){
        editor.clear();
        editor.commit();
        // Redirect ke LoginActivity di sini atau di Activity pemanggil
    }
}