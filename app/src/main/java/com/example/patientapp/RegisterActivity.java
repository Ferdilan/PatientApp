package com.example.patientapp;

import android.app.DatePickerDialog;
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
        etPassword = findViewById(R.id.etPassReg);
        tvTglLahir = findViewById(R.id.tvTglLahir);
        rgJk = findViewById(R.id.rgJk);
        imgKtp = findViewById(R.id.imgKtp);
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
                        // Konversi URI ke File
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
        String pass = etPassword.getText().toString();

        // Ambil Radio Button
        int selectedId = rgJk.getCheckedRadioButtonId();
        String jk = (selectedId != -1) ? ((RadioButton)findViewById(selectedId)).getText().toString() : "L";

        if (nik.isEmpty() || fileKtp == null) {
            Toast.makeText(this, "Lengkapi data dan foto KTP", Toast.LENGTH_SHORT).show();
            return;
        }

        // --- SIAPKAN REQUEST BODY ---
        RequestBody rbNik = RequestBody.create(MediaType.parse("text/plain"), nik);
        RequestBody rbNama = RequestBody.create(MediaType.parse("text/plain"), nama);
        RequestBody rbTgl = RequestBody.create(MediaType.parse("text/plain"), tgl);
        RequestBody rbAlamat = RequestBody.create(MediaType.parse("text/plain"), alamat);
        RequestBody rbPass = RequestBody.create(MediaType.parse("text/plain"), pass);
        RequestBody rbJk = RequestBody.create(MediaType.parse("text/plain"), jk);

        // Siapkan File Gambar
        RequestBody requestFile = RequestBody.create(MediaType.parse("image/*"), fileKtp);
        MultipartBody.Part bodyFoto = MultipartBody.Part.createFormData("foto_ktp", fileKtp.getName(), requestFile);

        // --- EKSEKUSI API ---
        com.example.patientapp.api.RetrofitClient.getInstance().register(rbNik, rbNama, rbTgl, rbJk, rbAlamat, rbPass, bodyFoto)
                .enqueue(new Callback<com.example.patientapp.api.AuthResponse>() {
                    @Override
                    public void onResponse(Call<com.example.patientapp.api.AuthResponse> call, Response<AuthResponse> response) {
                        if (response.isSuccessful() && response.body() != null) {
                            if (!response.body().isError()) {
                                Toast.makeText(RegisterActivity.this, "Registrasi Sukses!", Toast.LENGTH_SHORT).show();
                                finish(); // Kembali ke Login
                            } else {
                                Toast.makeText(RegisterActivity.this, response.body().getMessage(), Toast.LENGTH_SHORT).show();
                            }
                        } else {
                            Toast.makeText(RegisterActivity.this, "Gagal Register", Toast.LENGTH_SHORT).show();
                        }
                    }

                    @Override
                    public void onFailure(Call<com.example.patientapp.api.AuthResponse> call, Throwable t) {
                        Toast.makeText(RegisterActivity.this, "Error: " + t.getMessage(), Toast.LENGTH_SHORT).show();
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
}