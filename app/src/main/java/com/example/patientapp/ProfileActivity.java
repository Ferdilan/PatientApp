package com.example.patientapp;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import java.util.HashMap;
import com.bumptech.glide.Glide;

public class ProfileActivity extends AppCompatActivity {

    SessionManager session;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile);

        TextView tvName = findViewById(R.id.tvProfileName);
        TextView tvNik = findViewById(R.id.tvProfileNik);
        TextView tvAlamat = findViewById(R.id.tvProfileAlamat);
        TextView tvTglLahir = findViewById(R.id.tvProfileTglLahir);
        TextView tvJk = findViewById(R.id.tvProfileJenisKelamin);
        ImageView imgKtp = findViewById(R.id.imgProfileKtp);
        Button btnLogout = findViewById(R.id.btnLogout);

        // 1. Ambil Data Sesi
        session = new SessionManager(this);
        HashMap<String, String> user = session.getUserDetails();

        String name = user.get(SessionManager.KEY_NAMA);
        String nik = user.get(SessionManager.KEY_NIK);
        String alamat = user.get(SessionManager.KEY_ALAMAT);
        String tgl = user.get(SessionManager.KEY_TGL);
        String jk = user.get(SessionManager.KEY_JK);
        String fotoKTP = user.get(SessionManager.KEY_FOTO);


        // 2. Tampilkan
        tvName.setText(name);
        tvNik.setText("NIK: " + (nik != null ? nik : "-"));
        tvAlamat.setText("Alamat: " + (alamat != null ? alamat : "-"));
        tvTglLahir.setText("Tgl Lahir: " + (tgl != null ? tgl : "-"));
        tvJk.setText("Jenis Kelamin: " + (jk != null ? jk : "-"));

        String baseUrl = " https://scared-prewashed-garden.ngrok-free.dev";
        String urlLengkap = baseUrl + fotoKTP;

        Glide.with(this)
                .load(urlLengkap)
                .placeholder(android.R.drawable.ic_menu_gallery) // Gambar saat loading
                .error(android.R.drawable.ic_menu_report_image)   // Gambar jika error
                .into(imgKtp);

        // 3. Logika Logout
        btnLogout.setOnClickListener(v -> {
            session.logoutUser(); // Fungsi bawaan SessionManager (pastikan ada method clear editor)

            // Arahkan ke Welcome Activity dan HAPUS history stack
            Intent i = new Intent(ProfileActivity.this, WelcomeActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
            finish();
        });
    }
}