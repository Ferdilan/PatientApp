package com.example.patientapp;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.widget.Button;
import android.widget.TextView;

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

    // Handler untuk simulasi teks berubah-ubah
    private Handler statusHandler = new Handler();
    private int dotCount = 0;
    private boolean isFound = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_searching);

        tvStatus = findViewById(R.id.tvStatus);

        SessionManager session = new SessionManager(this);
        myId = session.getUserDetails().get(SessionManager.KEY_ID);

        mqttManager = MqttClientManager.getInstance();

        // 1. Subscribe ke Topik Respons Pribadi
        // Topik: pasien/respons/{ID_SAYA}
        String responseTopic = "pasien/respons/" + myId;

        Log.d("Searching", "Menunggu balasan di topik: " + responseTopic);

        mqttManager.subscribe(responseTopic, (topic, message) -> {
            Log.d("Searching", "DAPAT BALASAN: " + message);

            // Proses di Thread Utama karena update UI
            runOnUiThread(() -> handleDriverResponse(message));
        });

        // 2. Animasi Teks Status (Agar user tidak bosan)
        startStatusAnimation();

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
    }

    private void handleDriverResponse(String jsonMessage) {
        if (isFound) return; // Cegah double proses

        try {
            JSONObject json = new JSONObject(jsonMessage);
            String status = json.optString("status");
            String driverId = json.optString("id_driver");

            // Validasi: Apakah driver MENERIMA?
            if ("ACCEPTED".equalsIgnoreCase(status)) {
                isFound = true;
                tvStatus.setText("AMBULANS DITEMUKAN!");

                // Beri jeda 1 detik agar user baca status "Ditemukan"
                new Handler().postDelayed(() -> {
                    Intent intent = new Intent(SearchingActivity.this, TrackingActivity.class);
                    intent.putExtra("ID_DRIVER", driverId); // PENTING: Bawa ID Driver ke peta

                    // (Opsional) Bawa data lokasi driver awal jika ada di JSON
                    double latDriver = json.optDouble("lat_driver", 0.0);
                    double lonDriver = json.optDouble("lon_driver", 0.0);
                    intent.putExtra("LAT_DRIVER_AWAL", latDriver);
                    intent.putExtra("LON_DRIVER_AWAL", lonDriver);

                    startActivity(intent);
                    finish(); // Tutup layar searching
                }, 1000);
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void startStatusAnimation() {
        Runnable statusRunnable = new Runnable() {
            @Override
            public void run() {
                if (isFound) return;

                String dots = "";
                for (int i = 0; i < dotCount; i++) dots += ".";

                tvStatus.setText("MENCARI AMBULANS" + dots);

                dotCount++;
                if (dotCount > 3) dotCount = 0;

                statusHandler.postDelayed(this, 500); // Update tiap 0.5 detik
            }
        };
        statusHandler.post(statusRunnable);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        statusHandler.removeCallbacksAndMessages(null); // Hentikan animasi saat keluar
        // Jangan disconnect MQTT di sini, karena TrackingActivity masih butuh koneksinya!
    }
}