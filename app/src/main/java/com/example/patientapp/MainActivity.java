package com.example.patientapp;

import android.Manifest;
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

import org.eclipse.paho.client.mqttv3.MqttMessage;

import java.util.Locale;
import java.util.UUID;

public class MainActivity extends AppCompatActivity implements MqttClientManager.MqttMessageListener, OnMapReadyCallback {

    private static final String TAG = "MainActivity";
    private String clientId;

    private MqttClientManager mqttManager;
    private TextView statusTextView;
    private Button btnEmergency;
    private Button btnTransport;

    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1;
    private GoogleMap mMap; // Objek Peta
    private FusedLocationProviderClient fusedLocationClient; // Klien untuk mendapat lokasi
    private Location mCurrentLocation; // Variabel untuk menyimpan lokasi terakhir

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        statusTextView = findViewById(R.id.statusTextView); // Asumsikan Anda punya TextView ini di layout
        btnEmergency = findViewById(R.id.btnEmergency);
        btnTransport = findViewById(R.id.btnTransport);

        // --- INISIALISASI PETA ---
        // Dapatkan SupportMapFragment dan beri tahu saat peta siap digunakan.
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        if (mapFragment != null) {
            mapFragment.getMapAsync(this);
        }

        // Inisialisasi FusedLocationProviderClient
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        // --- KONFIGURASI MQTT ---
        String brokerUri = com.example.patientapp.BuildConfig.MQTT_HOST;
        String username = com.example.patientapp.BuildConfig.MQTT_USERNAME;
        String password = com.example.patientapp.BuildConfig.MQTT_PASSWORD;
        this.clientId = "android-client-" + UUID.randomUUID().toString();


        // Inisialisasi MqttClientManager
        mqttManager = MqttClientManager.getInstance();
        mqttManager.setListener(this);
        String subscriptionTopic = "client/" + clientId + "/notification";
        mqttManager.setSubscriptionTopic(subscriptionTopic);
        mqttManager.connect(this, brokerUri, clientId, username, password);

        setupButtonListeners();

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
    }

    private void setupButtonListeners() {
        // Listener untuk Tombol DARURAT
        btnEmergency.setOnClickListener(v -> {
            sendHelpRequest("DARURAT"); // Kirim jenis layanan DARURAT
        });

        // Listener untuk Tombol TRANSPORT
        btnTransport.setOnClickListener(v -> {
            sendHelpRequest("TRANSPORT"); // Kirim jenis layanan TRANSPORT
        });
    }

    // --- Dipanggil saat peta siap digunakan ---
    @Override
    public void onMapReady(@NonNull GoogleMap googleMap) {
        mMap = googleMap;

        // Panggil fungsi untuk mendapatkan lokasi
        getLastKnownLocation();
    }

    private void getLastKnownLocation() {
        // Cek izin terlebih dahulu
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {

            // Jika izin tidak ada, minta izin
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION},
                    LOCATION_PERMISSION_REQUEST_CODE);
            return;
        }
        // Aktifkan tombol "My Location" (titik biru) di peta
        mMap.setMyLocationEnabled(true);

        // Dapatkan lokasi terakhir
        fusedLocationClient.getLastLocation()
                .addOnSuccessListener(this, new OnSuccessListener<Location>() {
                    @Override
                    public void onSuccess(Location location) {
                        if (location != null) {
                            // Simpan lokasi ke variabel global
                            mCurrentLocation = location;

                            // Buat objek LatLng untuk peta
                            LatLng userLocation = new LatLng(location.getLatitude(), location.getLongitude());

                            // Tambahkan penanda (marker) di lokasi pengguna
                            mMap.addMarker(new MarkerOptions().position(userLocation).title("Lokasi Saya"));

                            // Arahkan kamera peta ke lokasi pengguna
                            mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(userLocation, 15.0f)); // Zoom level 15

                            statusTextView.setText("Lokasi ditemukan. Siap mengirim bantuan.");
                        } else {
                            statusTextView.setText("Gagal mendapatkan lokasi. Aktifkan GPS Anda.");
                            Log.w(TAG, "getLastLocation:onSuccess: Gagal mendapatkan lokasi (null)");
                        }
                    }
                });
        }

    // --- Callback setelah pengguna merespon permintaan izin
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Izin diberikan, coba dapatkan lokasi lagi
                getLastKnownLocation();
            } else {
                // Izin ditolak
                Toast.makeText(this, "Izin lokasi ditolak. Aplikasi tidak dapat mengirim lokasi Anda.", Toast.LENGTH_LONG).show();
                statusTextView.setText("Izin lokasi ditolak.");
            }
        }
    }

    private void sendHelpRequest(String jenisLayanan) {
            // Periksa apakah lokasi sudah didapat
            if (mCurrentLocation == null) {
                Toast.makeText(this, "Lokasi belum didapat. Harap tunggu...", Toast.LENGTH_SHORT).show();
                getLastKnownLocation();
                return;
            }

            String topic = "panggilan/masuk";

            int id_pasien = 123; //id 123 sebagai contoh

            // --- GANTI PAYLOAD HARDCODED DENGAN LOKASI DINAMIS ---
            String payload = String.format(Locale.US,
                    "{\"id_pasien\": %d, \"lokasi_pasien_lat\": %f, \"lokasi_pasien_lon\": %f, \"jenis_layanan\": \"%s\"}",
                    id_pasien,
                    mCurrentLocation.getLatitude(),
                    mCurrentLocation.getLongitude(),
                    jenisLayanan
            );

            // Publikasikan pesan
            mqttManager.publish(topic, payload, 1); //mengapa menggunakan qos 1?

            Toast.makeText(this, "Permintaan bantuan dikirim...", Toast.LENGTH_SHORT).show();
            statusTextView.setText("Menunggu respons...");

            subscribeToStatusUpdates(id_pasien);
    }

    /**
     * Berlangganan ke topik status T8
     * @param id_pasien ID pasien saat ini
     */
    private void subscribeToStatusUpdates(int id_pasien) {
        // Kita gunakan topik yang disederhanakan
        String statusTopic = "panggilan/status/pasien/" + id_pasien;

        // Pastikan manager tidak null dan terhubung
        if (mqttManager != null) {
            mqttManager.subscribe(statusTopic, 1); // QoS 1
            Log.d(TAG, "Berlangganan ke topik status: " + statusTopic);
        }
    }

    @Override
    public void onMessageReceived(String topic, MqttMessage message) {
        String payload = new String(message.getPayload());
        Log.d(TAG, "Pesan balasan diterima: " + payload);

        // Dijalankan di UI Thread untuk memperbarui TextView
        runOnUiThread(() -> {
            // Di sini Anda akan mem-parsing JSON payload
            // Untuk saat ini, kita tampilkan mentah:
            statusTextView.setText("Respons Server: " + payload);

            // Contoh Parsing (Opsional):
            // try {
            //    JSONObject json = new JSONObject(payload);
            //    String status = json.getString("status_panggilan");
            //    String driverId = json.getString("id_ambulans");
            //    int eta = json.getInt("eta_detik") / 60; // ubah ke menit
            //    statusTextView.setText("Driver " + driverId + " sedang " + status + ". ETA: " + eta + " menit.");
            // } catch (JSONException e) {
            //    statusTextView.setText("Respons Diterima (format salah): " + payload);
            // }
        });
    }

    @Override
    protected void onDestroy() {
        mqttManager.disconnect();
        super.onDestroy();
    }
}