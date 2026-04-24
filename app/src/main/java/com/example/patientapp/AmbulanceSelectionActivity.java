package com.example.patientapp;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONObject;

public class AmbulanceSelectionActivity extends AppCompatActivity {

    private RadioGroup rgConditions;
    private Button btnConfirm;
    private double currentLat, currentLng;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ambulance_selection);

        rgConditions = findViewById(R.id.rgConditions);
        btnConfirm = findViewById(R.id.btnConfirm);

        currentLat = getIntent().getDoubleExtra("LATITUDE", 0.0);
        currentLng = getIntent().getDoubleExtra("LONGITUDE", 0.0);

        btnConfirm.setOnClickListener(v -> {
            int selectedId = rgConditions.getCheckedRadioButtonId();
            if (selectedId == -1) {
                Toast.makeText(this, "Silakan pilih kondisi terlebih dahulu", Toast.LENGTH_SHORT).show();
                return;
            }

            RadioButton selectedRb = findViewById(selectedId);
            String category = (String) selectedRb.getTag(); // EMERGENCY atau TRANSPORT
            String kondisi = selectedRb.getText().toString();

            new android.app.AlertDialog.Builder(this)
                    .setTitle("Konfirmasi Panggilan")
                    .setMessage("Apakah Anda yakin ingin memanggil ambulans untuk kondisi: " + kondisi + "?")
                    .setPositiveButton("Ya, Panggil", (dialog, which) -> {
                        if ("EMERGENCY".equals(category)) {
                            panggilAmbulans("DARURAT", kondisi);
                        } else {
                            panggilAmbulans("TRANSPORT", kondisi);
                        }
                    })
                    .setNegativeButton("Batal", null)
                    .show();

            if ("EMERGENCY".equals(category)) {
                // Langsung panggil untuk Darurat tanpa halaman konfirmasi lagi
                panggilAmbulans("DARURAT", kondisi);
            } else {
                // Langsung panggil untuk Non-Darurat
                panggilAmbulans("TRANSPORT", kondisi);
            }
            finish();
        });
    }

    private void panggilAmbulans(String jenis, String kondisi) {
        String requestId = "REQ_" + System.currentTimeMillis();
        
        // Kirim MQTT
        MqttClientManager.getInstance().publish("panggilan/masuk", 
            createPayload(jenis, kondisi, requestId));
            
        // Pindah ke SearchingActivity
        Intent intent = new Intent(this, SearchingActivity.class);
        intent.putExtra("id_panggilan", requestId);
        intent.putExtra("LATITUDE", currentLat);
        intent.putExtra("LONGITUDE", currentLng);
        intent.putExtra("JENIS_LAYANAN", jenis);
        intent.putExtra("KONDISI", kondisi);
        
        startActivity(intent);
    }

    private String createPayload(String jenis, String kondisi, String requestId) {
        try {
            JSONObject json = new JSONObject();
            SessionManager session = new SessionManager(this);
            String idPasien = session.getUserDetails().get(SessionManager.KEY_ID);
            android.util.Log.d("DEBUG_PAYLOAD", "ID Pasien: " + idPasien);

            json.put("id_panggilan", requestId);
            json.put("jenis_layanan", jenis);
            json.put("kondisi", kondisi);
            json.put("lokasi_pasien_lat", currentLat);
            json.put("lokasi_pasien_lon", currentLng);
            json.put("id_pasien", session.getUserDetails().get(SessionManager.KEY_ID));
            json.put("nama_pasien", session.getUserDetails().get(SessionManager.KEY_NAMA));
            
            return json.toString();
        } catch (Exception e) {
            e.printStackTrace();
            return "";
        }
    }
}