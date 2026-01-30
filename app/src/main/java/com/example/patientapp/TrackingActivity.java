package com.example.patientapp;

import android.graphics.Point;
import android.os.Bundle;
import android.os.Handler;
import android.os.SystemClock;
import android.util.Log;
import android.view.animation.Interpolator;
import android.view.animation.LinearInterpolator;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.Projection;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;

import org.json.JSONException;
import org.json.JSONObject;

public class TrackingActivity extends AppCompatActivity implements OnMapReadyCallback {

    private GoogleMap mMap;
    private Marker ambulanceMarker;
    private MqttClientManager mqttManager;
    private String idDriver = "1"; // Default

    // Variabel Animasi
    private Handler handler = new Handler();

    // UI Components
    private TextView tvDriverName, tvLicensePlate, tvStatus;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_tracking);

        // 1. Inisialisasi View
        tvDriverName = findViewById(R.id.tvDriverName);
        tvLicensePlate = findViewById(R.id.tvLicensePlate);
        tvStatus = findViewById(R.id.tvStatus);

        // 2. AMBIL DATA DARI INTENT
        if (getIntent().hasExtra("DRIVER_ID")) {
            idDriver = getIntent().getStringExtra("DRIVER_ID");
        }

        String namaSopir = getIntent().getStringExtra("DRIVER_NAME");
        String nopol = getIntent().getStringExtra("DRIVER_PLATE");

        // 3. Tampilkan ke Layar
        if (namaSopir != null) tvDriverName.setText(namaSopir);
        if (nopol != null) tvLicensePlate.setText(nopol);

        // 4. Siapkan Peta
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        if (mapFragment != null) {
            mapFragment.getMapAsync(this);
        }

        // 5. Siapkan MQTT (Singleton Baru HiveMQ)
        mqttManager = MqttClientManager.getInstance();
    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;

        // Posisikan kamera awal
        LatLng defaultLoc = new LatLng(-7.2575, 112.7521);
        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(defaultLoc, 15));

        // Setelah peta siap, baru kita konek dan subscribe
        connectAndSubscribe();
    }

    private void connectAndSubscribe() {
        // PERUBAHAN: Gunakan logic baru HiveMQ
        if (!mqttManager.isConnected()) {
            mqttManager.connect(new MqttClientManager.ConnectionListener() {
                @Override
                public void onSuccess() {
                    Log.d("Tracking", "MQTT Connected");
                    // Beri tahu user (Opsional)
                    runOnUiThread(() -> Toast.makeText(TrackingActivity.this, "Terhubung ke Ambulans", Toast.LENGTH_SHORT).show());
                    subscribeToDriverLocation();
                }

                @Override
                public void onError(String errorMessage) {
                    Log.e("Tracking", "Gagal Connect MQTT: " + errorMessage);
                }
            });
        } else {
            subscribeToDriverLocation();
        }
    }

    private void subscribeToDriverLocation() {
        String topic = "ambulans/lokasi/update/" + idDriver;

        // PERUBAHAN: Subscribe HiveMQ langsung terima String (message)
        mqttManager.subscribe(topic, (topicReceived, message) -> {
            Log.d("Tracking", "Lokasi Masuk: " + message);

            // Update UI/Map Wajib di Thread UI
            runOnUiThread(() -> processLocationUpdate(message));
        });
    }

    private void processLocationUpdate(String jsonString) {
        try {
            JSONObject data = new JSONObject(jsonString);

            // Ambil data Lat/Lng dari JSON
            double lat = data.getDouble("lokasi_latitude");
            double lon = data.getDouble("lokasi_longitude");

            // Ambil arah (bearing) jika ada
            float bearing = 0;
            if (data.has("bearing")) {
                bearing = (float) data.getDouble("bearing");
            }

            LatLng newPos = new LatLng(lat, lon);

            if (ambulanceMarker == null) {
                // Kalo marker belum ada, buat baru
                MarkerOptions options = new MarkerOptions()
                        .position(newPos)
                        .title("Ambulans")
                        // Pastikan resource gambar ada, jika error ganti ke defaultMarker
                        .icon(BitmapDescriptorFactory.fromResource(R.drawable.ic_ambulance_top_view))
                        .anchor(0.5f, 0.5f)
                        .flat(true);

                ambulanceMarker = mMap.addMarker(options);
                mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(newPos, 17));
            } else {
                // Kalo marker sudah ada, ANIMASIKAN gerakannya
                animateMarker(ambulanceMarker, newPos, bearing);
            }

        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    // --- FITUR ANIMASI HALUS (Interpolasi) ---
    public void animateMarker(final Marker marker, final LatLng toPosition, final float toRotation) {
        final Handler handler = new Handler();
        final long start = SystemClock.uptimeMillis();
        Projection proj = mMap.getProjection();
        Point startPoint = proj.toScreenLocation(marker.getPosition());
        final LatLng startLatLng = proj.fromScreenLocation(startPoint);
        final long duration = 2000;

        final Interpolator interpolator = new LinearInterpolator();

        handler.post(new Runnable() {
            @Override
            public void run() {
                long elapsed = SystemClock.uptimeMillis() - start;
                float t = interpolator.getInterpolation((float) elapsed / duration);

                double lng = t * toPosition.longitude + (1 - t) * startLatLng.longitude;
                double lat = t * toPosition.latitude + (1 - t) * startLatLng.latitude;

                marker.setPosition(new LatLng(lat, lng));
                marker.setRotation(toRotation);

                if (t < 1.0) {
                    handler.postDelayed(this, 16);
                }
            }
        });
    }
}