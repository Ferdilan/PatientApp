package com.example.patientapp;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import java.util.HashMap;

//Penyimpanan Sesi Login
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
    public static final String KEY_ALAMAT = "alamat";
    public static final String KEY_TGL = "tgl";
    public static final String KEY_JK = "jk";
    public static final String KEY_FOTO = "foto";


    public SessionManager(Context context){
        this._context = context;
        pref = _context.getSharedPreferences(PREF_NAME, PRIVATE_MODE);
        editor = pref.edit();
    }

    public void createLoginSession(String id, String nama, String nik, String alamat, String tgl, String jk, String foto){
        editor.putBoolean(IS_LOGIN, true);
        editor.putString(KEY_ID, id);
        editor.putString(KEY_NAMA, nama);
        editor.putString(KEY_NIK, nik);
        editor.putString(KEY_ALAMAT, alamat);
        editor.putString(KEY_TGL, tgl);
        editor.putString(KEY_JK, jk);
        editor.putString(KEY_FOTO, foto);
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
        user.put(KEY_ALAMAT, pref.getString(KEY_ALAMAT, null));
        user.put(KEY_TGL, pref.getString(KEY_TGL, null));
        user.put(KEY_JK, pref.getString(KEY_JK, null));
        user.put(KEY_FOTO, pref.getString(KEY_FOTO, null));
        return user;
    }

    public String getUserId() {
        return pref.getString(KEY_ID, null);
    }

    public void logoutUser(){
        editor.clear();
        editor.commit();
        // Redirect ke LoginActivity di sini atau di Activity pemanggil
        Intent i = new Intent(_context, LoginActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        _context.startActivity(i);
    }
}