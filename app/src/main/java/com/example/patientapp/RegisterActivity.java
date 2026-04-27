package com.example.patientapp;

import android.app.DatePickerDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.patientapp.api.AuthResponse;
import com.example.patientapp.api.RetrofitClient;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.Calendar;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class RegisterActivity extends AppCompatActivity {
    private EditText etNik, etNama, etAlamat, etPassword, etDob;
    private ImageView ivKtpPreview, btnBack;
    private MaterialCardView cvUploadKtp;
    private MaterialButton btnMale, btnFemale, btnRegister;

    private Uri selectedImageUri;
    private File fileKtp;
    private String selectedGender = "Laki-laki"; // Default

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_register);

        // Init Views
        etNik = findViewById(R.id.etNik);
        etNama = findViewById(R.id.etFullName);
        etAlamat = findViewById(R.id.etAddress);
        etDob = findViewById(R.id.etDob);
        etPassword = findViewById(R.id.etPassword);
        ivKtpPreview = findViewById(R.id.ivKtpPreview);
        cvUploadKtp = findViewById(R.id.cvUploadKtp);
        btnMale = findViewById(R.id.btnMale);
        btnFemale = findViewById(R.id.btnFemale);
        btnRegister = findViewById(R.id.btnCompleteRegistration);
        btnBack = findViewById(R.id.btnBack);

        btnBack.setOnClickListener(v -> finish());

        // Gender Selection
        updateGenderUI();   
        btnMale.setOnClickListener(v -> {
            selectedGender = "Laki-laki";
            updateGenderUI();
        });
        btnFemale.setOnClickListener(v -> {
            selectedGender = "Perempuan";
            updateGenderUI();
        });

        // Date Picker
        etDob.setOnClickListener(v -> showDatePicker());

        // Image Picker
        ActivityResultLauncher<String> mGetContent = registerForActivityResult(
                new ActivityResultContracts.GetContent(),
                uri -> {
                    if (uri != null) {
                        selectedImageUri = uri;
                        ivKtpPreview.setImageURI(uri);
                        fileKtp = getFileFromUri(uri);
                    }
                });

        cvUploadKtp.setOnClickListener(v -> mGetContent.launch("image/*"));

        // Tombol Register
        btnRegister.setOnClickListener(v -> processRegister());

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
    }

    private void updateGenderUI() {
        if (selectedGender.equals("Laki-laki")) {
            btnMale.setStrokeWidth(4);
            btnFemale.setStrokeWidth(1);
        } else {
            btnMale.setStrokeWidth(1);
            btnFemale.setStrokeWidth(4);
        }
    }

    private void showDatePicker() {
        final Calendar c = Calendar.getInstance();
        int year = c.get(Calendar.YEAR);
        int month = c.get(Calendar.MONTH);
        int day = c.get(Calendar.DAY_OF_MONTH);

        new DatePickerDialog(this, (view, year1, month1, dayOfMonth) ->
                etDob.setText(year1 + "-" + (month1 + 1) + "-" + dayOfMonth),
                year, month, day).show();
    }

    private void processRegister() {
        String nik = etNik.getText().toString();
        String nama = etNama.getText().toString();
        String tgl = etDob.getText().toString();
        String alamat = etAlamat.getText().toString();
        String password = etPassword.getText().toString();

        if (nik.isEmpty() || nama.isEmpty() || tgl.isEmpty() || alamat.isEmpty() || password.isEmpty() || fileKtp == null) {
            Toast.makeText(this, "Harap lengkapi semua data dan foto KTP", Toast.LENGTH_SHORT).show();
            return;
        }

        if (password.length() < 6) {
            Toast.makeText(this, "Password minimal 6 karakter", Toast.LENGTH_SHORT).show();
            return;
        }

        String hashedPassword = hashPassword(password);

        // --- SIAPKAN REQUEST BODY ---
        RequestBody rbNik = RequestBody.create(MediaType.parse("text/plain"), nik);
        RequestBody rbNama = RequestBody.create(MediaType.parse("text/plain"), nama);
        RequestBody rbTgl = RequestBody.create(MediaType.parse("text/plain"), tgl);
        RequestBody rbAlamat = RequestBody.create(MediaType.parse("text/plain"), alamat);
        RequestBody rbJk = RequestBody.create(MediaType.parse("text/plain"), selectedGender);
        RequestBody rbPass = RequestBody.create(MediaType.parse("text/plain"), hashedPassword);

        // Siapkan File Gambar
        RequestBody requestFile = RequestBody.create(MediaType.parse("image/*"), fileKtp);
        MultipartBody.Part bodyFoto = MultipartBody.Part.createFormData("foto_ktp", fileKtp.getName(), requestFile);

        // --- EKSEKUSI API ---
        RetrofitClient.getInstance()
                .register(rbNik, rbNama, rbTgl, rbJk, rbAlamat, rbPass, bodyFoto)
                .enqueue(new Callback<AuthResponse>() {
                    @Override
                    public void onResponse(Call<AuthResponse> call, Response<AuthResponse> response) {
                        if (response.isSuccessful() && response.body() != null) {
                            AuthResponse resp = response.body();

                            if (!resp.isError()) {
                                Toast.makeText(RegisterActivity.this, "Registrasi Sukses!", Toast.LENGTH_SHORT).show();

                                SessionManager sessionManager = new SessionManager(RegisterActivity.this);
                                sessionManager.createLoginSession(
                                        resp.getData().getId(),
                                        resp.getData().getNama(),
                                        nik,
                                        alamat,
                                        tgl,
                                        selectedGender,
                                        resp.getData().getFoto()
                                );

                                Intent intent = new Intent(RegisterActivity.this, MainActivity.class);
                                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);
                                startActivity(intent);
                                finish();
                            } else {
                                Toast.makeText(RegisterActivity.this, "Gagal: " + resp.getMessage(), Toast.LENGTH_SHORT).show();
                            }
                        } else {
                            Toast.makeText(RegisterActivity.this, "Gagal Register", Toast.LENGTH_SHORT).show();
                        }
                    }

                    @Override
                    public void onFailure(Call<AuthResponse> call, Throwable t) {
                        Toast.makeText(RegisterActivity.this, "Koneksi Error: " + t.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private File getFileFromUri(Uri uri) {
        try {
            File tempFile = File.createTempFile("ktp_upload", ".jpg", getCacheDir());
            tempFile.deleteOnExit();
            InputStream inputStream = getContentResolver().openInputStream(uri);
            FileOutputStream out = new FileOutputStream(tempFile);
            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                out.write(buffer, 0, bytesRead);
            }
            out.flush();
            return tempFile;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
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
}