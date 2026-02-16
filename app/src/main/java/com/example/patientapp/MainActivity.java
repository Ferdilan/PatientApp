package com.example.patientapp;

import static androidx.core.location.LocationManagerCompat.getCurrentLocation;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.tasks.OnSuccessListener;

import java.util.Locale;

public class MainActivity extends AppCompatActivity implements OnMapReadyCallback {

    private static final String TAG = "MainActivity";

    private MqttClientManager mqttManager;
    private TextView statusTextView;
    private Button btnEmergency;
    private Button btnTransport;

    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1;
    private GoogleMap mMap;
    private FusedLocationProviderClient fusedLocationClient;
    private Location mCurrentLocation;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        androidx.appcompat.widget.Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        statusTextView = findViewById(R.id.statusTextView);
        btnEmergency = findViewById(R.id.btnEmergency);
        btnTransport = findViewById(R.id.btnTransport);

        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        if (mapFragment != null) {
            mapFragment.getMapAsync(this);
        }

        // Inisialisasi FusedLocationProviderClient
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        // --- KONFIGURASI MQTT BARU (HIVEMQ) ---
        // Kita tidak perlu lagi mengambil BuildConfig disini secara manual,
        // karena MqttClientManager sudah menanganinya secara internal.

        mqttManager = MqttClientManager.getInstance();

        // PERUBAHAN 2: Cara Konek menggunakan Listener
        connectToMqttBroker();

        setupButtonListeners();

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
    }

    private void connectToMqttBroker() {
        statusTextView.setText("Menghubungkan ke Server...");

        mqttManager.connect(new MqttClientManager.ConnectionListener() {
            @Override
            public void onSuccess() {
                runOnUiThread(() -> {
                    statusTextView.setText("Terhubung ke Server (Siap).");
                    Toast.makeText(MainActivity.this, "MQTT Connected!", Toast.LENGTH_SHORT).show();

                    // Opsional: Subscribe ke topik notifikasi umum jika perlu
                    // String myTopic = "client/notifikasi/umum";
                    // mqttManager.subscribe(myTopic, (t, m) -> Log.d(TAG, "Notif: " + m));
                });
            }

            @Override
            public void onError(String errorMessage) {
                runOnUiThread(() -> {
                    statusTextView.setText("Koneksi Gagal: " + errorMessage);
                    Log.e(TAG, "MQTT Error: " + errorMessage);
                });
            }
        });
    }

    private void setupButtonListeners() {
        btnEmergency.setOnClickListener(v -> {
            if (mCurrentLocation == null) {
                Toast.makeText(MainActivity.this, "Sedang mengunci lokasi GPS... Mohon tunggu 2 detik & tekan lagi.", Toast.LENGTH_LONG).show();
                // Pancing update lokasi lagi (Force Update)
                getLastKnownLocation();
                return;
            }

            if (mCurrentLocation.getLatitude() == 0.0 && mCurrentLocation.getLongitude() == 0.0) {
                Toast.makeText(MainActivity.this, "Lokasi belum akurat.", Toast.LENGTH_SHORT).show();
                return;
            }
            Intent intent = new Intent(MainActivity.this, EmergencyConfirmationActivity.class);
            // Kirim Lokasi Terakhir User
            // Kirim Lokasi Terakhir User
            intent.putExtra("LATITUDE", mCurrentLocation.getLatitude());
            intent.putExtra("LONGITUDE", mCurrentLocation.getLongitude());
            startActivity(intent);
        });
//                sendHelpRequest("DARURAT"));
        btnTransport.setOnClickListener(v -> sendHelpRequest("TRANSPORT"));
    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;
        getLastKnownLocation();
    }

    private void getLastKnownLocation() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION},
                    LOCATION_PERMISSION_REQUEST_CODE);
            return;
        }

        mMap.setMyLocationEnabled(true);

        fusedLocationClient.getLastLocation()
                .addOnSuccessListener(this, new OnSuccessListener<Location>() {
                    @Override
                    public void onSuccess(Location location) {
                        if (location != null) {
                            mCurrentLocation = location;
                            LatLng userLocation = new LatLng(location.getLatitude(), location.getLongitude());

                            mMap.clear(); // Bersihkan marker lama
                            mMap.addMarker(new MarkerOptions().position(userLocation).title("Lokasi Jemput"));
                            mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(userLocation, 15.0f));

                            statusTextView.setText("Lokasi siap. Silakan pilih layanan.");
                        } else {
                            statusTextView.setText("Gagal mendapatkan lokasi. Cek GPS.");
                        }
                    }
                });
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                getLastKnownLocation();
            } else {
                Toast.makeText(this, "Izin lokasi ditolak.", Toast.LENGTH_LONG).show();
            }
        }
    }

    private void sendHelpRequest(String jenisLayanan) {
        if (mCurrentLocation == null) {
            Toast.makeText(this, "Mencari lokasi...", Toast.LENGTH_SHORT).show();
            getLastKnownLocation();
            return;
        }

        if (!mqttManager.isConnected()) {
            Toast.makeText(this, "Sedang menyambungkan ulang ke server...", Toast.LENGTH_SHORT).show();
            connectToMqttBroker();
            return;
        }

        // Topik Pemesanan
        String topic = "panggilan/masuk";
        SessionManager session = new SessionManager(this);
        String idPasien = session.getUserDetails().get(SessionManager.KEY_ID);


        String payload = String.format(Locale.US,
                "{\"id_pasien\": %s, \"lokasi_pasien_lat\": %f, \"lokasi_pasien_lon\": %f, \"jenis_layanan\": \"%s\"}",
                idPasien,
                mCurrentLocation.getLatitude(),
                mCurrentLocation.getLongitude(),
                jenisLayanan
        );

        // PERUBAHAN 3: Publish tanpa QoS (Default library sudah handle)
        mqttManager.publish(topic, payload);

        Toast.makeText(this, "Meminta bantuan " + jenisLayanan + "...", Toast.LENGTH_SHORT).show();
        statusTextView.setText("Mencari Driver...");

        // Langsung subscribe untuk mendengar balasan status
        subscribeToStatusUpdates(idPasien);
    }

    private void subscribeToStatusUpdates(String id_pasien) {
        String statusTopic = "panggilan/status/pasien/" + id_pasien;

        // PERUBAHAN 4: Subscribe menggunakan Lambda & runOnUiThread
        mqttManager.subscribe(statusTopic, (topic, message) -> {
            Log.d(TAG, "Update Status Masuk: " + message);

            runOnUiThread(() -> {
                // Tampilkan pesan mentah dari server/driver
                statusTextView.setText("Status Terkini: " + message);

                // Disini nanti Anda bisa menambahkan logika:
                // Jika status == "accepted", pindah ke TrackingActivity
                // if (message.contains("accepted")) { ... }
            });
        });
    }

    @Override
    public boolean onCreateOptionsMenu(android.view.Menu menu) {
        getMenuInflater().inflate(R.menu.main_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(android.view.MenuItem item) {
        int id = item.getItemId();

        if (id == R.id.menu_profile) {
            // Buka Halaman Profil
            startActivity(new Intent(this, ProfileActivity.class));
            return true;
        } else if (id == R.id.menu_history) {
            // Buka Halaman Riwayat
             startActivity(new Intent(this, HistoryActivity.class));
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onDestroy() {
        if (mqttManager != null) {
            mqttManager.disconnect();
        }
        super.onDestroy();
    }
}