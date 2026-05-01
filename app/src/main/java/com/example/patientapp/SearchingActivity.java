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
    private String responseTopic;

    private Handler statusHandler = new Handler();
    private int dotCount = 0;
    private boolean isFound = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_searching);

        session = new SessionManager(this);
        tvStatus = findViewById(R.id.tvStatusTitle);
        btnCancel = findViewById(R.id.btnCancel);

        if (getIntent() != null) {
            idPanggilan = getIntent().getStringExtra("id_panggilan");
        }

        myId = session.getUserId();
        mqttManager = MqttClientManager.getInstance();

        responseTopic = "panggilan/status/pasien/" + myId;

        mqttManager.subscribe(responseTopic, (topic, message) -> {
            if (isFinishing() || isDestroyed()) return;
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
            
            // Perbarui idPanggilan jika server mengirimkan ID yang berbeda
            String serverId = json.optString("id_panggilan");
            if (!serverId.isEmpty()) {
                this.idPanggilan = serverId;
            }

            if ("menuju_lokasi".equalsIgnoreCase(status)) {
                isFound = true;
                tvStatus.setText("AMBULANS DITEMUKAN!");

                Intent intent = new Intent(SearchingActivity.this, TrackingActivity.class);
                intent.putExtra("id_ambulans", ambulansId);
                intent.putExtra("id_panggilan", this.idPanggilan); // PENTING: Teruskan ID Panggilan
                intent.putExtra("nama_driver", json.optString("nama_driver"));
                intent.putExtra("plat_nomor", json.optString("plat_nomor"));
                intent.putExtra("lat_pasien", json.optString("lat_pasien"));
                intent.putExtra("lon_pasien", json.optString("lon_pasien"));

                startActivity(intent);
                finish();
            }
        } catch (Exception e) {
            Log.e("Searching", "Error parsing JSON: " + jsonMessage);
        }
    }

    private void startStatusAnimation() {
        statusHandler.post(new Runnable() {
            @Override
            public void run() {
                if (isFound) return;
                StringBuilder dots = new StringBuilder();
                for (int i = 0; i < dotCount; i++) dots.append(".");
                tvStatus.setText("Mencari Ambulans" + dots);
                dotCount = (dotCount + 1) % 4;
                statusHandler.postDelayed(this, 500);
            }
        });
    }

    private void batalkanPanggilanDarurat() {
        if (idPanggilan == null || idPanggilan.isEmpty()) {
            finish();
            return;
        }
        try {
            JSONObject payload = new JSONObject();
            payload.put("id_panggilan", idPanggilan);
            payload.put("status", "cancelled");
            mqttManager.publish("panggilan/batal/pasien", payload.toString());
            finish();
        } catch (Exception e) { e.printStackTrace(); }
    }

    @Override
    protected void onDestroy() {
        if (mqttManager != null && responseTopic != null && !isFound) {
            mqttManager.unsubscribe(responseTopic);
        }
        statusHandler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }
}
