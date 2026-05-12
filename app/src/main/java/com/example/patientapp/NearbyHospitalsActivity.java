package com.example.patientapp;

import android.Manifest;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ProgressBar;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.patientapp.adapter.HospitalAdapter;
import com.example.patientapp.model.HospitalModel;
import com.example.patientapp.utils.ToastHelper;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.libraries.places.api.Places;
import com.google.android.libraries.places.api.net.PlacesClient;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class NearbyHospitalsActivity extends AppCompatActivity {

    private static final String TAG = "NearbyHospitals";
    private RecyclerView recyclerView;
    private HospitalAdapter adapter;
    private final List<HospitalModel> hospitalList = new ArrayList<>();
    private PlacesClient placesClient;
    private ProgressBar progressBar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_nearby_hospitals);

        // --- INISIALISASI VIEW (Penting: Harus dipanggil agar tidak NullPointerException) ---
        progressBar = findViewById(R.id.progressBar);
        recyclerView = findViewById(R.id.recyclerViewHospitals);
        Toolbar toolbar = findViewById(R.id.toolbarNearby);

        // Setup RecyclerView
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new HospitalAdapter(hospitalList);
        recyclerView.setAdapter(adapter);

        // Setup Toolbar
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            toolbar.setNavigationOnClickListener(v -> onBackPressed());
        }

        String apiKey = null;
        try {
            ApplicationInfo ai = getPackageManager().getApplicationInfo(getPackageName(), PackageManager.GET_META_DATA);
            Bundle bundle = ai.metaData;
            if (bundle != null) {
                apiKey = bundle.getString("com.google.android.geo.API_KEY");
            }
        } catch (PackageManager.NameNotFoundException e) {
            Log.e(TAG, "Gagal menemukan package metadata", e);
        }

        // Validasi API Key sebelum inisialisasi
        if (apiKey == null || apiKey.isEmpty() || apiKey.equals("${MAPS_API_KEY}")) {
            Log.e(TAG, "API Key tidak ditemukan atau belum terkonfigurasi di local.properties");
            ToastHelper.showToast(this, "Kesalahan Konfigurasi API Key");
        } else {
            if (!Places.isInitialized()) {
                Places.initialize(getApplicationContext(), apiKey);
            }
            placesClient = Places.createClient(this);
            fetchNearbyHospitals(); 
        }
    }

    private void fetchNearbyHospitals() {
        if (progressBar != null) progressBar.setVisibility(View.VISIBLE);

        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            progressBar.setVisibility(View.GONE);
            ToastHelper.showToast(this, "Izin lokasi diperlukan.");
            return;
        }

        FusedLocationProviderClient fusedClient = LocationServices.getFusedLocationProviderClient(this);
        fusedClient.getLastLocation().addOnSuccessListener(location -> {
            if (location == null) {
                progressBar.setVisibility(View.GONE);
                ToastHelper.showToast(this, "Tidak dapat memperoleh lokasi saat ini.");
                return;
            }

            double lat = location.getLatitude();
            double lng = location.getLongitude();
            int radius = 5000; //Radius dalam kilometer

            // Ambil API Key dari metadata (sama seperti yang sudah Anda gunakan)
            String apiKey = getApiKeyFromManifest(); // lihat method di bawah

            String url = "https://maps.googleapis.com/maps/api/place/nearbysearch/json" +
                    "?location=" + lat + "," + lng +
                    "&radius=" + radius +
                    "&type=hospital" +
                    "&key=" + apiKey;

            OkHttpClient client = new OkHttpClient();
            Request request = new Request.Builder().url(url).build();

            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(@NonNull Call call, @NonNull IOException e) {
                    runOnUiThread(() -> {
                        progressBar.setVisibility(View.GONE);
                        ToastHelper.showToast(NearbyHospitalsActivity.this, "Network error: " + e.getMessage());
                    });
                }

                @Override
                public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                    if (response.isSuccessful()) {
                        String jsonData = response.body().string();
                        try {
                            JSONObject json = new JSONObject(jsonData);
                            JSONArray results = json.getJSONArray("results");
                            List<HospitalModel> hospitals = new ArrayList<>();
                            for (int i = 0; i < results.length(); i++) {
                                JSONObject place = results.getJSONObject(i);
                                String name = place.getString("name");
                                String address = place.optString("vicinity", "");
                                double rating = place.optDouble("rating", 0.0);
                                String placeId = place.getString("place_id");
                                hospitals.add(new HospitalModel(name, address, rating, placeId));
                            }
                            runOnUiThread(() -> {
                                hospitalList.clear();
                                hospitalList.addAll(hospitals);
                                adapter.notifyDataSetChanged();
                                progressBar.setVisibility(View.GONE);
                                if (hospitalList.isEmpty()) {
                                    ToastHelper.showToast(NearbyHospitalsActivity.this, "Tidak ada rumah sakit dalam radius " + (radius/1000) + " km");
                                } else {
                                    ToastHelper.showToast(NearbyHospitalsActivity.this, "Ditemukan " + hospitalList.size() + " rumah sakit");
                                }
                            });
                        } catch (JSONException e) {
                            runOnUiThread(() -> {
                                progressBar.setVisibility(View.GONE);
                                ToastHelper.showToast(NearbyHospitalsActivity.this, "Gagal parsing data");
                            });
                        }
                    } else {
                        runOnUiThread(() -> {
                            progressBar.setVisibility(View.GONE);
                            ToastHelper.showToast(NearbyHospitalsActivity.this, "API Error: " + response.code());
                        });
                    }
                }
            });
        }).addOnFailureListener(e -> {
            progressBar.setVisibility(View.GONE);
            ToastHelper.showToast(this, "Gagal mendapatkan lokasi perangkat: " + e.getMessage());
        });
    }

    private String getApiKeyFromManifest() {
        try {
            ApplicationInfo ai = getPackageManager().getApplicationInfo(getPackageName(), PackageManager.GET_META_DATA);
            Bundle bundle = ai.metaData;
            if (bundle != null) {
                return bundle.getString("com.google.android.geo.API_KEY");
            }
        } catch (PackageManager.NameNotFoundException e) {
            Log.e(TAG, "Metadata tidak ditemukan", e);
        }
        return null;
    }
}