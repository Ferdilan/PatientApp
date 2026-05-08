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
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.IntentSenderRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.Fragment;

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

import org.json.JSONObject;

import java.util.HashMap;

public class HomeFragment extends Fragment implements OnMapReadyCallback {

    private static final String TAG = "HomeFragment";
    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1;

    private MqttClientManager mqttManager;
    private TextView tvAddressTitle;
    private TextView tvAddressSubtitle;
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
                    if (getContext() != null) {
                        Toast.makeText(getContext(), "Aplikasi membutuhkan GPS aktif!", Toast.LENGTH_SHORT).show();
                    }
                }
            });

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_home, container, false);

        tvAddressTitle = view.findViewById(R.id.tvAddressTitle);
        tvAddressSubtitle = view.findViewById(R.id.tvAddressSubtitle);
        LinearLayout btnCallAmbulance = view.findViewById(R.id.btnCallAmbulance);

        SupportMapFragment mapFragment = (SupportMapFragment) getChildFragmentManager().findFragmentById(R.id.map);
        if (mapFragment != null) {
            mapFragment.getMapAsync(this);
        }

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireContext());
        mqttManager = MqttClientManager.getInstance();

        checkAndRequestLocationSettings();
        connectToMqttBroker();

        btnCallAmbulance.setOnClickListener(v -> {
            if (mCurrentLocation != null) {
                Intent intent = new Intent(getActivity(), AmbulanceSelectionActivity.class);
                intent.putExtra("LATITUDE", mCurrentLocation.getLatitude());
                intent.putExtra("LONGITUDE", mCurrentLocation.getLongitude());
                startActivity(intent);
            } else {
                Toast.makeText(getContext(), "Mencari lokasi Anda...", Toast.LENGTH_SHORT).show();
                fetchLiveLocation();
            }
        });

        return view;
    }

    private void checkAndRequestLocationSettings() {
        LocationRequest locationRequest = LocationRequest.create();
        locationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
        LocationSettingsRequest.Builder builder = new LocationSettingsRequest.Builder().addLocationRequest(locationRequest);
        builder.setAlwaysShow(true);

        SettingsClient client = LocationServices.getSettingsClient(requireContext());
        Task<LocationSettingsResponse> task = client.checkLocationSettings(builder.build());

        task.addOnFailureListener(requireActivity(), e -> {
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
                if (isAdded()) {
                    requireActivity().runOnUiThread(() -> {
                        Toast.makeText(getContext(), "Server Terhubung", Toast.LENGTH_SHORT).show();
                        dengarkanSemuaAmbulans();
                    });
                }
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
        if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, LOCATION_PERMISSION_REQUEST_CODE);
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
                if (!isAdded()) return;
                android.location.Geocoder geocoder = new android.location.Geocoder(requireContext(), new java.util.Locale("id", "ID"));
                java.util.List<android.location.Address> addresses = geocoder.getFromLocation(location.getLatitude(), location.getLongitude(), 1);
                if (addresses != null && !addresses.isEmpty()) {
                    android.location.Address address = addresses.get(0);
                    final String title = address.getThoroughfare() != null ? address.getThoroughfare() : address.getLocality();
                    final String subtitle = address.getAddressLine(0);
                    if (isAdded()) {
                        requireActivity().runOnUiThread(() -> {
                            if (tvAddressTitle != null) tvAddressTitle.setText(title);
                            if (tvAddressSubtitle != null) tvAddressSubtitle.setText(subtitle);
                        });
                    }
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
        if (!isAdded()) return;
        requireActivity().runOnUiThread(() -> {
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
    public void onDestroy() {
        super.onDestroy();
        if (mqttManager != null) mqttManager.disconnect();
    }
}