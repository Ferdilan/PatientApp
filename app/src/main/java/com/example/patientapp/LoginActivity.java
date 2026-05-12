package com.example.patientapp;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.patientapp.api.AuthResponse;
import com.example.patientapp.api.RetrofitClient;
import com.example.patientapp.utils.ToastHelper;

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

    private String hashPassword(String password) {
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(password.getBytes("UTF-8"));
            StringBuilder hexString = new StringBuilder();

            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            e.printStackTrace();
            return password;
        }
    }

    private void loginProcess() {
        String nik = etNik.getText().toString();
        String pass = etPassword.getText().toString();

        if (nik.isEmpty() || pass.isEmpty()) {
            ToastHelper.showToast(this, "Data tidak boleh kosong");
            return;
        }

        String hashedPassword = hashPassword(pass);

        RetrofitClient.getInstance().login(nik, hashedPassword).enqueue(new Callback<AuthResponse>() {
            @Override
            public void onResponse(Call<com.example.patientapp.api.AuthResponse> call, Response<AuthResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    AuthResponse resp = response.body();

                    if (!resp.isError()) {
                        ToastHelper.showToast(LoginActivity.this, "Login Berhasil");

                        // Simpan Sesi
                        SessionManager session = new SessionManager(LoginActivity.this);
                        session.createLoginSession(
                                resp.getData().getId(),
                                resp.getData().getNama(),
                                resp.getData().getNik(),
                                resp.getData().getAlamat(),
                                resp.getData().getTgl(),
                                resp.getData().getJk(),
                                resp.getData().getFoto()
                        );

                        Intent intent = new Intent(LoginActivity.this, MainActivity.class);
                        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);
                        startActivity(intent);
                        finish();
                    } else {
                        ToastHelper.showToast(LoginActivity.this, "Gagal: " + resp.getMessage());
                    }
                } else {
                    ToastHelper.showToast(LoginActivity.this, "Gagal Terhubung ke Server: ");
                }
            }

            @Override
            public void onFailure(Call<AuthResponse> call, Throwable t) {
                ToastHelper.showToast(LoginActivity.this, "Error: " + t.getMessage());
            }
        });
    }
}