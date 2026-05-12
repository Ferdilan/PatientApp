package com.example.patientapp;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.Button;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MapStyleOptions;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class SearchingActivity extends AppCompatActivity implements OnMapReadyCallback {

    private MqttClientManager mqttManager;
    private TextView tvStatus;
    private Button btnCancel;
    private String myId;
    private SessionManager session;

    private String idPanggilan;
    private String responseTopic;
    private double latPasien, lonPasien;

    private GoogleMap mMap;
    private Handler statusHandler = new Handler();
    private boolean isFound = false;
    private final List<AnimatorSet> animators = new ArrayList<>();

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
            latPasien = getIntent().getDoubleExtra("LATITUDE", 0);
            lonPasien = getIntent().getDoubleExtra("LONGITUDE", 0);
        }

        myId = session.getUserId();
        mqttManager = MqttClientManager.getInstance();

        // Setup Map
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.mapSearching);
        if (mapFragment != null) {
            mapFragment.getMapAsync(this);
        }

        responseTopic = "panggilan/status/pasien/" + myId;
        mqttManager.subscribe(responseTopic, (topic, message) -> {
            if (isFinishing() || isDestroyed()) return;
            runOnUiThread(() -> handleDriverResponse(message));
        });

        btnCancel.setOnClickListener(v -> batalkanPanggilanDarurat());
        
        startRadarAnimation();

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
    }

    @Override
    public void onMapReady(@NonNull GoogleMap googleMap) {
        mMap = googleMap;
        mMap.setMapType(GoogleMap.MAP_TYPE_HYBRID);
        mMap.setTrafficEnabled(true);
        
        // Sembunyikan UI yang tidak perlu di layar pencarian
        mMap.getUiSettings().setAllGesturesEnabled(false);
        mMap.getUiSettings().setZoomControlsEnabled(false);

        if (latPasien != 0) {
            LatLng location = new LatLng(latPasien, lonPasien);
            mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(location, 18f));
        }
    }

    private void startRadarAnimation() {
        View p1 = findViewById(R.id.radarPulse1);
        View p2 = findViewById(R.id.radarPulse2);
        View p3 = findViewById(R.id.radarPulse3);

        animatePulse(p1, 0);
        animatePulse(p2, 600);
        animatePulse(p3, 1200);
    }

    private void animatePulse(View view, long delay) {
        ObjectAnimator scaleX = ObjectAnimator.ofFloat(view, "scaleX", 1.0f, 1.5f);
        ObjectAnimator scaleY = ObjectAnimator.ofFloat(view, "scaleY", 1.0f, 1.5f);
        ObjectAnimator alpha = ObjectAnimator.ofFloat(view, "alpha", 1.0f, 0.0f);

        scaleX.setRepeatCount(ValueAnimator.INFINITE);
        scaleY.setRepeatCount(ValueAnimator.INFINITE);
        alpha.setRepeatCount(ValueAnimator.INFINITE);

        AnimatorSet set = new AnimatorSet();
        set.playTogether(scaleX, scaleY, alpha);
        set.setDuration(2000);
        set.setStartDelay(delay);
        set.setInterpolator(new AccelerateDecelerateInterpolator());
        set.start();
        animators.add(set);
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

            if ("menuju_lokasi".equalsIgnoreCase(status)) {
                isFound = true;
                tvStatus.setText("AMBULANS DITEMUKAN!");

                Intent intent = new Intent(SearchingActivity.this, TrackingActivity.class);
                intent.putExtra("id_ambulans", ambulansId);
                intent.putExtra("id_panggilan", this.idPanggilan);
                intent.putExtra("nama_driver", json.optString("nama_driver"));
                intent.putExtra("plat_nomor", json.optString("plat_nomor"));
                intent.putExtra("lat_pasien", String.valueOf(latPasien));
                intent.putExtra("lon_pasien", String.valueOf(lonPasien));

                startActivity(intent);
                finish();
            }
        } catch (Exception e) {
            Log.e("Searching", "Error parsing JSON: " + jsonMessage);
        }
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
            if (myId != null) {
                try {
                    payload.put("id_pasien", Integer.parseInt(myId));
                } catch (NumberFormatException e) {
                    payload.put("id_pasien", myId);
                }
            }
            mqttManager.publish("panggilan/batal/pasien", payload.toString());
            finish();
        } catch (Exception e) { 
            Log.e("Searching", "Error cancel: " + e.getMessage());
        }
    }

    @Override
    protected void onDestroy() {
        for (AnimatorSet set : animators) {
            set.cancel();
        }
        if (mqttManager != null && responseTopic != null && !isFound) {
            mqttManager.unsubscribe(responseTopic);
        }
        statusHandler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }
}
