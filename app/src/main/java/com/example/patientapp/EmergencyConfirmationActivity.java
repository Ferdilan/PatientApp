package com.example.patientapp;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import androidx.appcompat.app.AppCompatActivity;

import com.example.patientapp.utils.ToastHelper;

import org.json.JSONObject;

public class EmergencyConfirmationActivity extends AppCompatActivity {

    private Button btnYes, btnNo;
    private double currentLat = 0.0;
    private double currentLng = 0.0;
    String uniqueId = "REQ_" + System.currentTimeMillis();

    private MqttClientManager mqttManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_emergency_confirmation);

        mqttManager = MqttClientManager.getInstance();

        // 1. Ambil Lokasi dari Intent (Dikirim dari MainActivity)
        if (getIntent() != null) {
            currentLat = getIntent().getDoubleExtra("LATITUDE", 0.0);
            currentLng = getIntent().getDoubleExtra("LONGITUDE", 0.0);
        }

        btnYes = findViewById(R.id.btnYes);
        btnNo = findViewById(R.id.btnNo);

        // LOGIKA KEPUTUSAN

        // JIKA IYA -> Eksekusi Panggilan
        btnYes.setOnClickListener(v -> {
            if (currentLat == 0.0 || currentLng == 0.0) {
                ToastHelper.showToast(this, "Lokasi tidak valid. Cek GPS Anda.");
                return;
            }
            sendEmergencyRequest();
        });

        // JIKA TIDAK -> Kembali ke Dashboard (Batalkan)
        btnNo.setOnClickListener(v -> {
            finish(); // Menutup activity ini dan kembali ke layer sebelumnya
        });
    }

    private void sendEmergencyRequest() {
        // 1. Kirim MQTT ke Driver
        try {
            JSONObject json = new JSONObject();
            json.put("id_panggilan", uniqueId);
            json.put("jenis_layanan", "DARURAT");
            json.put("lokasi_pasien_lat", currentLat);
            json.put("lokasi_pasien_lon", currentLng);

            // Tambahkan ID Pasien (Ambil dari SessionManager)
            SessionManager session = new SessionManager(this);
            json.put("id_pasien", session.getUserDetails().get(SessionManager.KEY_ID));
            json.put("nama_pasien", session.getUserDetails().get(SessionManager.KEY_NAMA));
            json.put("status", "requested");
            json.put("timestamp", System.currentTimeMillis());

            // Publish ke topik umum (Semua driver dengar)
            mqttManager.publish("panggilan/masuk", json.toString());

        } catch (Exception e) {
            e.printStackTrace();
        }

        // 2. Pindah ke Layar Mencari Driver
        Intent intent = new Intent(this, SearchingActivity.class);
        intent.putExtra("id_panggilan", uniqueId);
        intent.putExtra("LATITUDE", currentLat);
        intent.putExtra("LONGITUDE", currentLng);
        startActivity(intent);

        // 3. Tutup halaman konfirmasi agar tidak bisa di-back
        finish();
    }
}
