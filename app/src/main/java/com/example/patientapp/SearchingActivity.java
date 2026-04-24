package com.example.patientapp;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import org.json.JSONObject;

public class SearchingActivity extends AppCompatActivity {

    private MqttClientManager mqttManager;
    private TextView tvStatus;
    private Button btnCancel;
    private String myId;
    private SessionManager session;

    private String idPanggilan;
    private String idPasien;
    private String idDriverDitemukan;

    private Handler statusHandler = new Handler();
    private int dotCount = 0;
    private boolean isFound = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_searching);

        session = new SessionManager(this);

//        java.util.HashMap<String, String> user = session.getUserDetails();

        tvStatus = findViewById(R.id.tvStatus);
        btnCancel = findViewById(R.id.btnCancel);

        // AMBIL ID DARI INTENT
        if (getIntent() != null) {
            idPanggilan = getIntent().getStringExtra("id_panggilan");
            Log.d("Searching", "Diterima idPanggilan: " + idPanggilan);
        }

//        SessionManager session = new SessionManager(this);
        myId = session.getUserDetails().get(SessionManager.KEY_ID);

        mqttManager = MqttClientManager.getInstance();

        // 1. Subscribe ke Topik Respons Pribadi
        String responseTopic = "panggilan/status/pasien/" + myId;

        Log.d("Searching", "Menunggu balasan di topik: " + responseTopic);

        mqttManager.subscribe(responseTopic, (topic, message) -> {
            Log.d("Searching", "DAPAT BALASAN: " + message);
            runOnUiThread(() -> handleDriverResponse(message));
        });

        btnCancel.setOnClickListener(v -> batalkanPanggilanDarurat());

        startStatusAnimation();

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
    }

    private void handleDriverResponse(String jsonMessage) {
        if (isFound) return;

        try {
            JSONObject json = new JSONObject(jsonMessage);
            String status = json.optString("status_panggilan", "");
            String ambulansId = json.optString("id_ambulans");

            String serverId = json.optString("id_panggilan");
            if (!serverId.isEmpty()) {
                this.idPanggilan = serverId;
            }

            this.idDriverDitemukan = ambulansId;
            String latPasien = json.optString("lat_pasien");
            String lonPasien = json.optString("lon_pasien");
            

            if ("menuju_lokasi".equalsIgnoreCase(status)) {
                isFound = true;
                tvStatus.setText("AMBULANS DITEMUKAN!");

                new Handler().postDelayed(() -> {
                    try {
                        Intent intent = new Intent(SearchingActivity.this, TrackingActivity.class);
                        intent.putExtra("id_ambulans", ambulansId);
                        intent.putExtra("id_panggilan", this.idPanggilan);
                        intent.putExtra("lat_pasien", latPasien);
                        intent.putExtra("lon_pasien", lonPasien);

                        startActivity(intent);
                        finish();
                    } catch (Exception e) {
                        Log.e("SearchingActivity", "CRASH SAAT PINDAH ACTIVITY: " + e.getMessage(), e);
                    }
            }, 1000);
        }
        } catch (Exception e) {
            Log.e("Searching", "Error parsing JSON: " + jsonMessage);
        }
    }

    private void startStatusAnimation() {
        Runnable statusRunnable = new Runnable() {
            @Override
            public void run() {
                if (isFound) return;

                StringBuilder dots = new StringBuilder();
                for (int i = 0; i < dotCount; i++) dots.append(".");

                tvStatus.setText("MENCARI AMBULANS" + dots);

                dotCount++;
                if (dotCount > 3) dotCount = 0;

                statusHandler.postDelayed(this, 500);
            }
        };
        statusHandler.post(statusRunnable);
    }

    private void batalkanPanggilanDarurat() {
        Log.d("Searching", "Mencoba batal. idPanggilan saat ini: " + idPanggilan);

        if (idPanggilan == null || idPanggilan.isEmpty()) {
            Toast.makeText(this, "Tidak ada panggilan aktif untuk dibatalkan", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        try {
            JSONObject payload = new JSONObject();
            payload.put("id_panggilan", idPanggilan);

            if (idDriverDitemukan != null) {
                payload.put("id_ambulans", idDriverDitemukan);
            }

            payload.put("status", "cancelled");
            payload.put("alasan", "Dibatalkan oleh Pasien");

            String finalJsonPayload = payload.toString();
            Log.w("CANCEL_ORDER", "PAYLOAD BATAL: \n" + finalJsonPayload);

            mqttManager.publish("panggilan/batal/pasien", payload.toString());

            Toast.makeText(this, "Membatalkan pesanan...", Toast.LENGTH_SHORT).show();
            finish();

        } catch (Exception e) {
            Log.e("CANCEL_ORDER", "Gagal mengirim sinyal batal", e);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        statusHandler.removeCallbacksAndMessages(null);
    }
}