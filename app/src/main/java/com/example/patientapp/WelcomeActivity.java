package com.example.patientapp;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import androidx.appcompat.app.AppCompatActivity;

public class WelcomeActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 1. CEK SESI (Apakah user sudah pernah login?)
        SessionManager session = new SessionManager(this);
        if (session.isLoggedIn()) {
            // Jika sudah login, langsung lempar ke Main (Peta)
            goToMain();
            return;
        }

        // 2. Jika belum login, tampilkan layar sambutan
        setContentView(R.layout.activity_welcome);

        Button btnLogin = findViewById(R.id.btnWelcomeLogin);
        Button btnRegister = findViewById(R.id.btnWelcomeRegister);

        // Arahkan ke halaman masing-masing
        btnLogin.setOnClickListener(v -> {
            startActivity(new Intent(WelcomeActivity.this, LoginActivity.class));
        });

        btnRegister.setOnClickListener(v -> {
            startActivity(new Intent(WelcomeActivity.this, RegisterActivity.class));
        });
    }

    private void  goToMain() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
        finish();
    }
}