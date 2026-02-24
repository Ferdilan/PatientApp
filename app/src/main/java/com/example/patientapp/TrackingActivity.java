package com.example.patientapp;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Point;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.SystemClock;
import android.util.Log;
import android.view.animation.Interpolator;
import android.view.animation.LinearInterpolator;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.Projection;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptor;
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

    private String idAmbulans;   // Bukan int lagi, dan jangan di-hardcode
    private String namaDriver;
    private String platNomor;

    // Variabel Animasi
    private Handler handler = new Handler();

    // UI Components
    private TextView tvNamaDriver, tvPlatNomor, tvEstimasi, tvStatus;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_tracking);

        // 1. Inisialisasi View
        tvNamaDriver = findViewById(R.id.tvDriverName);
        tvPlatNomor = findViewById(R.id.tvLicensePlate);
        tvEstimasi = findViewById(R.id.tvEstimasi);

        // 2. Tangkap data dari Activity sebelumnya
        if (getIntent() != null) {
            // 1. AMBIL ID AMBULANS (Dengan proteksi String vs Integer)
            idAmbulans = getIntent().getStringExtra("id_ambulans");
            if (idAmbulans == null) {
                // Jika null, mungkin sebelumnya dikirim sebagai integer. Coba tangkap sebagai int.
                int idInt = getIntent().getIntExtra("id_ambulans", -1);
                if (idInt != -1) {
                    idAmbulans = String.valueOf(idInt);
                }
            }

            // 2. TANGKAP NAMA & PLAT (Gunakan nilai default jika kosong)
            namaDriver = getIntent().getStringExtra("nama_driver");
            if (namaDriver == null) {
                namaDriver = "Driver Menuju Lokasi"; // Nilai aman pengganti null
            }

            platNomor = getIntent().getStringExtra("plat_nomor");
            if (platNomor == null) {
                platNomor = "Segera Tiba"; // Nilai aman pengganti null
            }

            // (Opsional) Ambil ID Panggilan jika dibutuhkan
            String idPanggilan = getIntent().getStringExtra("id_panggilan");
        }

//        Validasi Safety (Jaga-jaga jika null)
        if (idAmbulans == null) {
            Toast.makeText(this, "Error: Data Ambulans tidak ditemukan!", Toast.LENGTH_SHORT).show();
            finish(); // Tutup halaman jika tidak ada ID
            return;
        }

        // 3. Tampilkan ke Layar
        tvNamaDriver.setText(namaDriver != null ? namaDriver : "Driver Sedang Menuju");
        tvPlatNomor.setText(platNomor != null ? platNomor : "-- ---- --");
        tvEstimasi.setText("Menghitung..."); // Default text

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

        mMap.getUiSettings().setZoomControlsEnabled(true);

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
        String topic = "ambulans/lokasi/update/" + idAmbulans;

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
                        .icon(getResizedMarkerIcon(R.drawable.ic_ambulance_top_view, 120, 120))
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
        final long duration = 1000;

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

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Bersihkan koneksi saat keluar agar tidak memory leak
        if (mqttManager != null && idAmbulans != null) {
            // mqttManager.unsubscribe("ambulans/lokasi/" + idAmbulans); // Jika ada method unsubscribe
        }
    }

    private BitmapDescriptor getResizedMarkerIcon(int resourceId, int width, int height) {
        Drawable drawable = ContextCompat.getDrawable(this, resourceId);
        if (drawable == null) return null;

        // Buat kanvas kosong dengan ukuran baru (width x height)
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);

        // Gambar ulang ikon ke dalam kanvas yang sudah dikecilkan
        drawable.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
        drawable.draw(canvas);

        return BitmapDescriptorFactory.fromBitmap(bitmap);
    }
}