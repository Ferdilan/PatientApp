package com.example.patientapp;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import org.json.JSONObject;

public class AmbulanceSelectionActivity extends AppCompatActivity {

    private RadioGroup rgConditions;
    private Button btnPanggilPsc, btnPanggilRelawan;
    private double currentLat, currentLng;
    private SessionManager session;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ambulance_selection);

        // 1. INISIALISASI WAJIB
        session = new SessionManager(this);
        rgConditions = findViewById(R.id.rgConditions);
        btnPanggilPsc = findViewById(R.id.btnPanggilPsc);
        btnPanggilRelawan = findViewById(R.id.btnPanggilRelawan);

        currentLat = getIntent().getDoubleExtra("LATITUDE", 0.0);
        currentLng = getIntent().getDoubleExtra("LONGITUDE", 0.0);

        // 2. HUBUNGKAN TOMBOL DENGAN SATU FUNGSI PUSAT (Delegate Pattern)
        // Kategori armada WAJIB menggunakan huruf kapital (PSC / RELAWAN) sesuai konvensi Database
        btnPanggilPsc.setOnClickListener(v -> prosesValidasiPanggilan("PSC"));
        btnPanggilRelawan.setOnClickListener(v -> prosesValidasiPanggilan("RELAWAN"));

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
    }

    // FASE 1: VALIDASI INPUT PENGGUNA SEBELUM DIKIRIM
    private void prosesValidasiPanggilan(String kategoriArmada) {
        int selectedId = rgConditions.getCheckedRadioButtonId();

        // Cek apakah pasien sudah memilih kondisi
        if (selectedId == -1) {
            Toast.makeText(this, "Silakan pilih kondisi medis terlebih dahulu!", Toast.LENGTH_SHORT).show();
            return;
        }

        RadioButton selectedRb = findViewById(selectedId);

        // Asumsi: Anda memasang tag "EMERGENCY" atau "TRANSPORT" di file XML RadioButton Anda
        String jenisLayanan = (String) selectedRb.getTag();
        if (jenisLayanan == null) {
            jenisLayanan = "DARURAT"; // Fallback aman jika XML lupa diberi tag
        } else {
            jenisLayanan = jenisLayanan.equals("EMERGENCY") ? "DARURAT" : "TRANSPORT";
        }

        String kondisiDeskripsi = selectedRb.getText().toString();

        // Tampilkan Dialog Konfirmasi
        String finalJenisLayanan = jenisLayanan;
        new android.app.AlertDialog.Builder(this)
                .setTitle("Konfirmasi Panggilan " + kategoriArmada)
                .setMessage("Panggil armada " + kategoriArmada + " untuk kondisi: " + kondisiDeskripsi + "?")
                .setPositiveButton("Ya, Panggil Sekarang", (dialog, which) -> {
                    // Eksekusi HANYA terjadi jika tombol Ya ditekan
                    eksekusiKirimServer(kategoriArmada, finalJenisLayanan, kondisiDeskripsi);
                })
                .setNegativeButton("Batal", null)
                .show();
    }

    // FASE 2: PERAKITAN DATA DAN PENGIRIMAN (Data Transport Layer)
    private void eksekusiKirimServer(String kategoriArmada, String jenisLayanan, String kondisi) {
        String requestId = "REQ_" + System.currentTimeMillis();
        String payloadJson = createPayload(kategoriArmada, jenisLayanan, kondisi, requestId);

        if (payloadJson.isEmpty()) {
            Toast.makeText(this, "Gagal merakit data. Coba lagi.", Toast.LENGTH_SHORT).show();
            return;
        }

        // 1. Tembak ke MQTT (Broker)
        Log.w("ORDER_PAYLOAD", "MENGIRIM PESANAN: " + payloadJson);
        MqttClientManager.getInstance().publish("panggilan/masuk", payloadJson);

        // 2. Pindah ke layar pencarian dan bawa ID-nya
        Intent intent = new Intent(this, SearchingActivity.class);
        intent.putExtra("id_panggilan", requestId);
        intent.putExtra("LATITUDE", currentLat);
        intent.putExtra("LONGITUDE", currentLng);
        intent.putExtra("JENIS_LAYANAN", jenisLayanan);
        intent.putExtra("KONDISI", kondisi);

        startActivity(intent);
        finish(); // Bunuh activity ini agar pasien tidak bisa kembali dengan tombol Back
    }

    // FASE 3: PEMBUNGKUS JSON
    private String createPayload(String kategoriArmada, String jenisLayanan, String kondisi, String requestId) {
        try {
            JSONObject json = new JSONObject();

            String idPasien = session.getUserId(); // Pastikan getUserId() merujuk ke kunci yang benar
            if (idPasien == null || idPasien.isEmpty()) {
                Log.e("PAYLOAD_ERROR", "ID Pasien Kosong!");
            }

            json.put("id_panggilan", requestId);
            json.put("id_pasien", idPasien); // WAJIB ADA untuk menghindari log 'undefined' di Node.js
            json.put("nama_pasien", session.getUserDetails().get(SessionManager.KEY_NAMA));
            json.put("lokasi_pasien_lat", currentLat);
            json.put("lokasi_pasien_lon", currentLng);

            // Parameter Kritis
            json.put("kategori_armada", kategoriArmada); // "PSC" atau "RELAWAN"
            json.put("jenis_layanan", jenisLayanan);     // "DARURAT" atau "TRANSPORT"
            json.put("kondisi", kondisi);                // Teks dari RadioButton

            return json.toString();
        } catch (Exception e) {
            Log.e("PAYLOAD_ERROR", "JSON Exception", e);
            return "";
        }
    }
}