package com.example.patientapp;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
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
import com.google.android.gms.maps.model.PolylineOptions;
import com.google.maps.android.PolyUtil;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class TrackingActivity extends AppCompatActivity implements OnMapReadyCallback {

    private GoogleMap mMap;
    private Marker ambulanceMarker;
    private MqttClientManager mqttManager;

    private String idAmbulans;
    private String namaDriver;
    private String platNomor;
    private double latPasien;
    private double lonPasien;
    private LatLng lastRoutedLocation = null;
    private com.google.android.gms.maps.model.Polyline currentPolyline = null;


    private SessionManager session;
    private String myPatientId;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    // Variabel Animasi
    private Handler handler = new Handler();

    // UI Components
    private TextView tvNamaDriver, tvPlatNomor, tvEstimasi, tvStatus;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_tracking);

        session = new SessionManager(this);
        myPatientId = session.getUserDetails().get(SessionManager.KEY_ID);

        // 1. Inisialisasi View
        tvNamaDriver = findViewById(R.id.tvDriverName);
        tvPlatNomor = findViewById(R.id.tvLicensePlate);
        tvEstimasi = findViewById(R.id.tvEstimasi);

        latPasien = getIntent().getDoubleExtra("lat_pasien", 0);
        lonPasien = getIntent().getDoubleExtra("lon_pasien", 0);

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
        mMap.setMapType(GoogleMap.MAP_TYPE_HYBRID);
        mMap.getUiSettings().setZoomControlsEnabled(true);

        LatLng posisiPasien = new LatLng(latPasien, lonPasien);
        mMap.addMarker(new MarkerOptions()
                .position(posisiPasien)
                .title("Lokasi Saya (Pasien)")
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_BLUE)));

        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(posisiPasien, 14));

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
        // Topik untuk mendengar pergerakan GPS Driver
        String topic = "ambulans/lokasi/update/" + idAmbulans;
        mqttManager.subscribe(topic, (topicReceived, message) -> {
            Log.d("Tracking", "Lokasi Masuk: " + message);
            runOnUiThread(() -> processLocationUpdate(message));
        });

        String topicStatus = "panggilan/status/pasien/" + myPatientId;
        mqttManager.subscribe(topicStatus, (topicReceived, message) -> {
            Log.d("Tracking", "Status Masuk: " + message);
            runOnUiThread(() -> processStatusUpdate(message));
        });
    }

    private void processStatusUpdate(String jsonString) {
        try {
            JSONObject data = new JSONObject(jsonString);
            String status = data.optString("status_panggilan", "");

            if ("selesai".equalsIgnoreCase(status) || "COMPLETED".equalsIgnoreCase(status)) {
                Toast.makeText(this, "Pengantaran Selesai", Toast.LENGTH_LONG).show();

                // Pindah kembali ke Beranda (MainActivity) atau Halaman Rating
                Intent intent = new Intent(TrackingActivity.this, MainActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);
                finish(); // Tutup halaman peta
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    private void processLocationUpdate(String jsonString) {
        try {
            JSONObject data = new JSONObject(jsonString);
            double lat = data.getDouble("lokasi_latitude");
            double lon = data.getDouble("lokasi_longitude");
            float bearing = data.has("bearing") ? (float) data.getDouble("bearing") : 0;

            LatLng newAmbulancePos = new LatLng(lat, lon);
            LatLng posisiPasien = new LatLng(latPasien, lonPasien);

            // --- LOGIKA THROTTLING RUTE (API GOOGLE) ---
            if (lastRoutedLocation == null) {
                // Pertama kali ambulans terdeteksi -> Langsung gambar rute!
                fetchRoute(newAmbulancePos, posisiPasien);
                lastRoutedLocation = newAmbulancePos;
            } else {
                // Hitung jarak antara lokasi ambulans saat ini dengan lokasi saat rute terakhir digambar
                float[] results = new float[1];
                android.location.Location.distanceBetween(
                        lastRoutedLocation.latitude, lastRoutedLocation.longitude,
                        newAmbulancePos.latitude, newAmbulancePos.longitude,
                        results
                );
                float distanceMovedInMeters = results[0];

                // Jika ambulans sudah bergerak lebih dari 500 meter, REFRESH RUTE!
                if (distanceMovedInMeters > 500) {
                    Log.d("Tracking", "Ambulans bergerak > 500m. Memperbarui rute...");
                    fetchRoute(newAmbulancePos, posisiPasien);
                    lastRoutedLocation = newAmbulancePos; // Catat lokasi pembaruan terbaru
                }
            }

//            // Ambil arah (bearing) jika ada
//            if (data.has("bearing")) {
//                bearing = (float) data.getDouble("bearing");
//            }
//
//            LatLng newPos = new LatLng(lat, lon);

            if (ambulanceMarker == null) {
                // Kalo marker belum ada, buat baru
                MarkerOptions options = new MarkerOptions()
                        .position(newAmbulancePos)
                        .title("Ambulans")
                        // Pastikan resource gambar ada, jika error ganti ke defaultMarker
                        .icon(getResizedMarkerIcon(R.drawable.ic_ambulance_top_view, 120, 120))
                        .anchor(0.5f, 0.5f)
                        .flat(true);

                ambulanceMarker = mMap.addMarker(options);
                mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(newAmbulancePos, 17));
            } else {
                // Kalo marker sudah ada, ANIMASIKAN gerakannya
                animateMarker(ambulanceMarker, newAmbulancePos, bearing);
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

    // --- LOGIKA RUTE DIRECTIONS API ---
    private void fetchRoute(LatLng origin, LatLng dest) {
        executor.execute(() -> {
            String apiKey = null;
            try {
                apiKey = getPackageManager().getApplicationInfo(getPackageName(), PackageManager.GET_META_DATA)
                        .metaData.getString("com.google.android.geo.API_KEY");
            } catch (Exception e) { e.printStackTrace(); }

            if (apiKey == null) {
                Log.e("Navigation", "API Key TIDAK DITEMUKAN di Manifest");
                return;
            }

            String url = "https://maps.googleapis.com/maps/api/directions/json?" +
                    "origin=" + origin.latitude + "," + origin.longitude +
                    "&destination=" + dest.latitude + "," + dest.longitude +
                    "&key=" + apiKey;

            Log.d("Navigation", "Mengirim Request ke: " + url); // Cek URL di Logcat

            OkHttpClient client = new OkHttpClient();
            Request request = new Request.Builder().url(url).build();

            try (Response response = client.newCall(request).execute()) {
                String jsonResp = response.body().string();

                // --- PENTING: LIHAT INI DI LOGCAT ---
                Log.d("Navigation", "Response Google: " + jsonResp);
                // ------------------------------------

                JSONObject json = new JSONObject(jsonResp);

                // Cek Status Jawaban Google
                String status = json.optString("status");
                if (!status.equals("OK")) {
                    Log.e("Navigation", "GAGAL GAMBAR RUTE. Status: " + status);
                    Log.e("Navigation", "Pesan Error: " + json.optString("error_message"));
                    return; // Stop di sini
                }

                JSONArray routes = json.getJSONArray("routes");
                if (routes.length() > 0) {
                    String encodedString = routes.getJSONObject(0)
                            .getJSONObject("overview_polyline")
                            .getString("points");

                    List<LatLng> path = PolyUtil.decode(encodedString);

                    handler.post(() -> {
                        mMap.addPolyline(new PolylineOptions()
                                .addAll(path)
                                .color(Color.BLUE)
                                .width(15)); // Pertebal garis jadi 15
                        Log.d("Tracking", "Garis Berhasil Digambar!");
                    });
                } else {
                    Log.e("Tracking", "Rute Kosong (ZERO_RESULTS)");
                }

            } catch (Exception e) {
                Log.e("Tracking", "Error Koneksi/Parsing", e);
            }
        });
    }
}