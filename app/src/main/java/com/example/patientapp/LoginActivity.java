package com.example.patientapp;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.patientapp.api.AuthResponse;
import com.example.patientapp.api.RetrofitClient;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class LoginActivity extends AppCompatActivity {
    EditText etNik, etPassword;
    Button btnLogin;
    TextView tvRegister;
    SessionManager session;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_login);

        // 1. Cek Sesi Dulu
//        session = new SessionManager(getApplicationContext());
//        if (session.isLoggedIn()) {
//            startActivity(new Intent(this, MainActivity.class));
//            finish();
//            return;
//        }

        etNik = findViewById(R.id.etNik);
        etPassword = findViewById(R.id.etPassword);
        btnLogin = findViewById(R.id.btnLogin);
        tvRegister = findViewById(R.id.tvRegister);

        btnLogin.setOnClickListener(v -> loginProcess());
        tvRegister.setOnClickListener(v -> startActivity(new Intent(this, RegisterActivity.class)));

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
    }

    private void loginProcess() {
        String nik = etNik.getText().toString();
        String pass = etPassword.getText().toString();

        if (nik.isEmpty() || pass.isEmpty()) {
            Toast.makeText(this, "Data tidak boleh kosong", Toast.LENGTH_SHORT).show();
            return;
        }

        RetrofitClient.getInstance().login(nik, pass).enqueue(new Callback<AuthResponse>() {
            @Override
            public void onResponse(Call<com.example.patientapp.api.AuthResponse> call, Response<AuthResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    AuthResponse resp = response.body();

                    if (!resp.isError()) {
                        Toast.makeText(LoginActivity.this, "Login Berhasil", Toast.LENGTH_SHORT).show();

                        // Simpan Sesi
                        SessionManager session = new SessionManager(LoginActivity.this);
                        session.createLoginSession(
                                resp.getData().getId(),
                                resp.getData().getNama(),
                                resp.getData().getNik()
                        );

                        Intent intent = new Intent(LoginActivity.this, MainActivity.class);
                        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);
                        startActivity(intent);
                        finish();
                    } else {
                        Toast.makeText(LoginActivity.this, "Gagal: " + resp.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                } else {
                    Toast.makeText(LoginActivity.this, "Gagal Terhubung ke Server: ", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<AuthResponse> call, Throwable t) {
                Toast.makeText(LoginActivity.this, "Error: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }
}