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

import java.util.UUID;

public class MainActivity extends AppCompatActivity implements MqttClientManager.MqttMessageListener, OnMapReadyCallback {

    private static final String TAG = "MainActivity";
    private String clientId;

    private MqttClientManager mqttManager;
    private TextView statusTextView;
    private Button requestHelpButton;

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
        requestHelpButton = findViewById(R.id.requestHelpButton); // Asumsikan Anda punya Button ini

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
        String brokerUri = "tcp://mustang.rmq.cloudamqp.com:1883";
        this.clientId = "android-client-" + UUID.randomUUID().toString();
        String username = "hjppbzvg:hjppbzvg";
        String password = "zUg3ysf369ZnibIfjtSc7Qtj-ezmi5IB";

        // Inisialisasi MqttClientManager
        mqttManager = MqttClientManager.getInstance();
        mqttManager.setListener(this);
        String subscriptionTopic = "client/" + clientId + "/notification";
        mqttManager.setSubscriptionTopic(subscriptionTopic);
        mqttManager.connect(this, brokerUri, clientId, username, password);

        setupButtonListener();

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
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

    private void setupButtonListener() {
        requestHelpButton.setOnClickListener(view -> {

            // Periksa apakah lokasi sudah didapat
            if (mCurrentLocation == null) {
                Toast.makeText(this, "Lokasi belum didapat. Harap tunggu...", Toast.LENGTH_SHORT).show();
                // Coba dapatkan lokasi lagi jika tombol ditekan
                getLastKnownLocation();
                return;
            }

            String topic = "pasien/request";

            // --- GANTI PAYLOAD HARDCODED DENGAN LOKASI DINAMIS ---
            String payload = "{\"patientId\":\"" + this.clientId +
                    "\", \"name\":\"Pasien Darurat\", \"location\":{\"latitude\":" + mCurrentLocation.getLatitude() +
                    ", \"longitude\":" + mCurrentLocation.getLongitude() + "}}";

            // Publikasikan pesan
            mqttManager.publish(topic, payload, 0);

            Toast.makeText(this, "Permintaan bantuan dikirim...", Toast.LENGTH_SHORT).show();
            statusTextView.setText("Menunggu respons...");
        });
    }

    @Override
    public void onMessageReceived(String topic, MqttMessage message) {
        String payload = new String(message.getPayload());
        Log.d(TAG, "Pesan balasan diterima: " + payload);

        runOnUiThread(() -> {
            statusTextView.setText("Respons Diterima: " + payload);
        });
    }

    @Override
    protected void onDestroy() {
        mqttManager.disconnect();
        super.onDestroy();
    }
}