package com.example.patientapp;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.location.Location;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.IntentSenderRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.gms.common.api.ResolvableApiException;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.LocationSettingsRequest;
import com.google.android.gms.location.LocationSettingsResponse;
import com.google.android.gms.location.SettingsClient;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.tasks.Task;
import com.google.android.material.bottomnavigation.BottomNavigationView;

import org.json.JSONObject;

import java.util.HashMap;

public class MainActivity extends AppCompatActivity implements OnMapReadyCallback {

    private static final String TAG = "MainActivity";

    private MqttClientManager mqttManager;
    private Button btnCallAmbulance;
    private TextView tvAddressTitle;
    private TextView tvAddressSubtitle;
    private BottomNavigationView bottomNavigation;

    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1;
    private GoogleMap mMap;
    private FusedLocationProviderClient fusedLocationClient;
    private Location mCurrentLocation;
    private com.google.android.gms.location.LocationCallback locationCallback;
    private final HashMap<String, Marker> ambulanceMarkers = new HashMap<>();

    private final ActivityResultLauncher<IntentSenderRequest> resolutionForResult =
            registerForActivityResult(new ActivityResultContracts.StartIntentSenderForResult(), result -> {
                if (result.getResultCode() == Activity.RESULT_OK) {
                    fetchLiveLocation();
                } else {
                    Toast.makeText(this, "Aplikasi membutuhkan GPS aktif!", Toast.LENGTH_SHORT).show();
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        checkAndRequestLocationSettings();

        LinearLayout btnCallAmbulance = findViewById(R.id.btnCallAmbulance);
        tvAddressTitle = findViewById(R.id.tvAddressTitle);
        tvAddressSubtitle = findViewById(R.id.tvAddressSubtitle);
        bottomNavigation = findViewById(R.id.bottom_navigation);

        setupBottomNavigation();

        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        if (mapFragment != null) {
            mapFragment.getMapAsync(this);
        }

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        mqttManager = MqttClientManager.getInstance();

        connectToMqttBroker();
        
        btnCallAmbulance.setOnClickListener(v -> {
            if (mCurrentLocation != null) {
                Intent intent = new Intent(MainActivity.this, AmbulanceSelectionActivity.class);
                intent.putExtra("LATITUDE", mCurrentLocation.getLatitude());
                intent.putExtra("LONGITUDE", mCurrentLocation.getLongitude());
                startActivity(intent);
            } else {
                Toast.makeText(this, "Mencari lokasi Anda...", Toast.LENGTH_SHORT).show();
                fetchLiveLocation();
            }
        });

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
    }

    private void setupBottomNavigation() {
        bottomNavigation.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_home) {
                return true;
            } else if (id == R.id.nav_history) {
                startActivity(new Intent(this, HistoryActivity.class));
                return true;
            } else if (id == R.id.nav_profile) {
                startActivity(new Intent(this, ProfileActivity.class));
                return true;
            }
            return false;
        });
        bottomNavigation.setSelectedItemId(R.id.nav_home);
    }

    private void checkAndRequestLocationSettings() {
        LocationRequest locationRequest = LocationRequest.create();
        locationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
        LocationSettingsRequest.Builder builder = new LocationSettingsRequest.Builder().addLocationRequest(locationRequest);
        builder.setAlwaysShow(true);

        SettingsClient client = LocationServices.getSettingsClient(this);
        Task<LocationSettingsResponse> task = client.checkLocationSettings(builder.build());

        task.addOnFailureListener(this, e -> {
            if (e instanceof ResolvableApiException) {
                try {
                    ResolvableApiException resolvable = (ResolvableApiException) e;
                    IntentSenderRequest intentSenderRequest = new IntentSenderRequest.Builder(resolvable.getResolution()).build();
                    resolutionForResult.launch(intentSenderRequest);
                } catch (Exception sendEx) {
                    Log.e(TAG, "Gagal resolusi GPS", sendEx);
                }
            }
        });
    }

    private void connectToMqttBroker() {
        mqttManager.connect(new MqttClientManager.ConnectionListener() {
            @Override
            public void onSuccess() {
                runOnUiThread(() -> {
                    Toast.makeText(MainActivity.this, "Server Terhubung", Toast.LENGTH_SHORT).show();
                    dengarkanSemuaAmbulans();
                });
            }

            @Override
            public void onError(String errorMessage) {
                Log.e(TAG, "MQTT Error: " + errorMessage);
            }
        });
    }

    @Override
    public void onMapReady(@NonNull GoogleMap googleMap) {
        mMap = googleMap;
        fetchLiveLocation();
    }

    private void fetchLiveLocation() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, LOCATION_PERMISSION_REQUEST_CODE);
            return;
        }

        if (mMap != null) mMap.setMyLocationEnabled(true);

        LocationRequest locationRequest = LocationRequest.create();
        locationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
        locationRequest.setInterval(5000);

        locationCallback = new com.google.android.gms.location.LocationCallback() {
            @Override
            public void onLocationResult(@NonNull com.google.android.gms.location.LocationResult locationResult) {
                for (Location location : locationResult.getLocations()) {
                    if (location != null) {
                        mCurrentLocation = location;
                        LatLng userLocation = new LatLng(location.getLatitude(), location.getLongitude());
                        if (mMap != null) mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(userLocation, 15.0f));
                        dapatkanNamaJalan(location);
                        fusedLocationClient.removeLocationUpdates(locationCallback);
                        break;
                    }
                }
            }
        };
        fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, android.os.Looper.getMainLooper());
    }

    private void dapatkanNamaJalan(Location location) {
        new Thread(() -> {
            try {
                android.location.Geocoder geocoder = new android.location.Geocoder(MainActivity.this, new java.util.Locale("id", "ID"));
                java.util.List<android.location.Address> addresses = geocoder.getFromLocation(location.getLatitude(), location.getLongitude(), 1);
                if (addresses != null && !addresses.isEmpty()) {
                    android.location.Address address = addresses.get(0);
                    final String title = address.getThoroughfare() != null ? address.getThoroughfare() : address.getLocality();
                    final String subtitle = address.getAddressLine(0);
                    runOnUiThread(() -> {
                        if (tvAddressTitle != null) tvAddressTitle.setText(title);
                        if (tvAddressSubtitle != null) tvAddressSubtitle.setText(subtitle);
                    });
                }
            } catch (Exception e) {
                Log.e("GEO", "Gagal Geocode", e);
            }
        }).start();
    }

    private void dengarkanSemuaAmbulans() {
        mqttManager.subscribe("ambulans/lokasi/update/+", (topic, message) -> {
            try {
                String[] parts = topic.split("/");
                String id = parts[parts.length - 1];
                JSONObject json = new JSONObject(message);
                double lat = json.getDouble("lokasi_latitude");
                double lon = json.getDouble("lokasi_longitude");
                float bearing = (float) json.optDouble("bearing", 0.0);
                perbaruiPosisiAmbulans(id, lat, lon, bearing);
            } catch (Exception e) { e.printStackTrace(); }
        });
    }

    private void perbaruiPosisiAmbulans(String id, double lat, double lon, float bearing) {
        runOnUiThread(() -> {
            if (mMap == null) return;
            LatLng pos = new LatLng(lat, lon);
            if (ambulanceMarkers.containsKey(id)) {
                Marker m = ambulanceMarkers.get(id);
                if (m != null) {
                    m.setPosition(pos);
                    m.setRotation(bearing);
                }
            } else {
                Bitmap b = BitmapFactory.decodeResource(getResources(), R.drawable.ic_ambulance_top_view);
                if (b != null) {
                    Bitmap smallMarker = Bitmap.createScaledBitmap(b, 80, 80, false);
                    Marker m = mMap.addMarker(new MarkerOptions()
                            .position(pos).title("Ambulans " + id).rotation(bearing)
                            .icon(BitmapDescriptorFactory.fromBitmap(smallMarker)));
                    ambulanceMarkers.put(id, m);
                }
            }
        });
    }

    @Override
    protected void onDestroy() {
        if (mqttManager != null) mqttManager.disconnect();
        super.onDestroy();
    }
}