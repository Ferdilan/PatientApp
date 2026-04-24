package com.example.patientapp;

import android.app.DatePickerDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
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
    EditText etNik, etNama, etAlamat, etPassword;
    TextView tvTglLahir;
    RadioGroup rgJk;
    ImageView imgKtp;
    Button btnPilihKtp, btnRegister;

    private Uri selectedImageUri;
    private File fileKtp;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_register);

        // Init Views
        etNik = findViewById(R.id.etNikReg);
        etNama = findViewById(R.id.etNamaReg);
        etAlamat = findViewById(R.id.etAlamatReg);
        tvTglLahir = findViewById(R.id.tvTglLahir);
        rgJk = findViewById(R.id.rgJk);
        imgKtp = findViewById(R.id.imgKtp);
        etPassword = findViewById(R.id.etPasswordReg);
        btnPilihKtp = findViewById(R.id.btnPilihKtp);
        btnRegister = findViewById(R.id.btnRegister);

        // Date Picker
        tvTglLahir.setOnClickListener(v -> showDatePicker());

        // Image Picker (Cara Modern)
        ActivityResultLauncher<String> mGetContent = registerForActivityResult(
                new ActivityResultContracts.GetContent(),
                uri -> {
                    if (uri != null) {
                        selectedImageUri = uri;
                        imgKtp.setImageURI(uri);
                        fileKtp = getFileFromUri(uri);
                    }
                });

        btnPilihKtp.setOnClickListener(v -> mGetContent.launch("image/*"));

        // Tombol Register
        btnRegister.setOnClickListener(v -> processRegister());

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
    }

    private void showDatePicker() {
        final Calendar c = Calendar.getInstance();
        int year = c.get(Calendar.YEAR);
        int month = c.get(Calendar.MONTH);
        int day = c.get(Calendar.DAY_OF_MONTH);

        new DatePickerDialog(this, (view, year1, month1, dayOfMonth) ->
                tvTglLahir.setText(year1 + "-" + (month1 + 1) + "-" + dayOfMonth),
                year, month, day).show();
    }

    private void processRegister() {
        String nik = etNik.getText().toString();
        String nama = etNama.getText().toString();
        String tgl = tvTglLahir.getText().toString();
        String alamat = etAlamat.getText().toString();
        String password = etPassword.getText().toString();

        if (password.length() < 6) {
            Toast.makeText(this, "Password minimal 6 karakter", Toast.LENGTH_SHORT).show();
            return;
        }

        // Ambil Radio Button
        int selectedId = rgJk.getCheckedRadioButtonId();
        String jk = (selectedId != -1) ? ((RadioButton)findViewById(selectedId)).getText().toString() : "L";

        String hashedPassword = hashPassword(password);

        // --- SIAPKAN REQUEST BODY ---
        RequestBody rbNik = RequestBody.create(MediaType.parse("text/plain"), nik);
        RequestBody rbNama = RequestBody.create(MediaType.parse("text/plain"), nama);
        RequestBody rbTgl = RequestBody.create(MediaType.parse("text/plain"), tgl);
        RequestBody rbAlamat = RequestBody.create(MediaType.parse("text/plain"), alamat);
        RequestBody rbJk = RequestBody.create(MediaType.parse("text/plain"), jk);
        RequestBody rbPass = RequestBody.create(MediaType.parse("text/plain"), hashedPassword);


        if (nik.isEmpty() || fileKtp == null) {
            Toast.makeText(this, "Lengkapi data dan foto KTP", Toast.LENGTH_SHORT).show();
            return;
        }

        if (nik.isEmpty() || password.isEmpty() || password.length() < 6) {
            Toast.makeText(this, "Data tidak lengkap atau password kurang dari 6 karakter", Toast.LENGTH_SHORT).show();
            return;
        }



        // Siapkan File Gambar
        RequestBody requestFile = RequestBody.create(MediaType.parse("image/*"), fileKtp);
        MultipartBody.Part bodyFoto = MultipartBody.Part.createFormData("foto_ktp", fileKtp.getName(), requestFile);

        // --- EKSEKUSI API ---
        RetrofitClient.getInstance()
                .register(rbNik, rbNama, rbTgl, rbJk, rbAlamat, rbPass, bodyFoto)
                .enqueue(new Callback<com.example.patientapp.api.AuthResponse>() {
                    @Override
                    public void onResponse(Call<com.example.patientapp.api.AuthResponse> call, Response<AuthResponse> response) {
                        if (response.isSuccessful() && response.body() != null) {
                            AuthResponse resp = response.body();

                            if (!response.body().isError()) {
                                Toast.makeText(RegisterActivity.this, "Registrasi Sukses!", Toast.LENGTH_SHORT).show();

                                // 1. Ambil Data dari Server
                                String idUser = resp.getData().getId();
                                String namaUser = resp.getData().getNama();

                                // 2. Simpan Session (Auto Login)
                                SessionManager sessionManager = new SessionManager(RegisterActivity.this);
                                sessionManager.createLoginSession(idUser, namaUser, nik, alamat, tgl, jk, resp.getData().getFoto());

                                // 3. Pindah ke Halaman Utama
                                Intent intent = new Intent(RegisterActivity.this, MainActivity.class);
                                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);
                                startActivity(intent);
                                finish(); // Kembali ke Login
                            } else {
                                Toast.makeText(RegisterActivity.this, "Gagal: " + resp.getMessage(), Toast.LENGTH_SHORT).show();
                            }
                        } else {
                            Toast.makeText(RegisterActivity.this, "Gagal Register", Toast.LENGTH_SHORT).show();
                        }
                    }

                    @Override
                    public void onFailure(Call<com.example.patientapp.api.AuthResponse> call, Throwable t) {
                        Toast.makeText(RegisterActivity.this, "Koneksi Error: " + t.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                });
    }

    // --- HELPER: Ubah URI Galeri jadi File Temp ---
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
            // Menggunakan algoritma SHA-256
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
            return password; // Jika gagal, kembalikan password asli (fallback)
        }
    }
}