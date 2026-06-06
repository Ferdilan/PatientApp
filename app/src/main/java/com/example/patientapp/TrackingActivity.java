package com.example.patientapp;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import android.view.animation.LinearInterpolator;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.example.patientapp.api.ApiService;
import com.example.patientapp.api.RetrofitClient;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptor;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.maps.android.PolyUtil;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;

public class TrackingActivity extends AppCompatActivity implements OnMapReadyCallback {

    private static final String TAG = "TrackingActivity";
    private GoogleMap mMap;
    private Marker ambulanceMarker;
    private Marker patientMarker;
    private Polyline currentPolyline = null;
    private MqttClientManager mqttManager;

    private String idAmbulans;
    private String idPanggilan;
    private String namaDriver;
    private String platNomor;
    private double latPasien;
    private double lonPasien;
    private LatLng lastRoutedLocation = null;

    private String myPatientId;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler handler = new Handler(Looper.getMainLooper());

    private TextView tvNamaDriver, tvPlatNomor, tvEstimasi;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_tracking);

        SessionManager session = new SessionManager(this);
        myPatientId = session.getUserId();
        
        tvNamaDriver = findViewById(R.id.tvDriverName);
        tvPlatNomor = findViewById(R.id.tvLicensePlate);
        tvEstimasi = findViewById(R.id.tvEstimasi);
        FloatingActionButton fabMyLocation = findViewById(R.id.fabMyLocation);

        if (getIntent() != null) {
            idAmbulans = getIntent().getStringExtra("id_ambulans");
            idPanggilan = getIntent().getStringExtra("id_panggilan");
            
            namaDriver = getIntent().getStringExtra("nama_driver");
            platNomor = getIntent().getStringExtra("plat_nomor");

            Log.d(TAG, "Data Intent: idAmb=" + idAmbulans + ", idCall=" + idPanggilan + ", name=" + namaDriver + ", plat=" + platNomor);

            if (namaDriver == null || namaDriver.isEmpty()) namaDriver = "Memuat nama...";
            if (platNomor == null || platNomor.isEmpty()) platNomor = "Loading...";

            try {
                String latS = getIntent().getStringExtra("lat_pasien");
                String lonS = getIntent().getStringExtra("lon_pasien");
                latPasien = (latS != null && !latS.isEmpty()) ? Double.parseDouble(latS) : getIntent().getDoubleExtra("lat_pasien", 0);
                lonPasien = (lonS != null && !lonS.isEmpty()) ? Double.parseDouble(lonS) : getIntent().getDoubleExtra("lon_pasien", 0);
            } catch (Exception e) {
                latPasien = getIntent().getDoubleExtra("lat_pasien", 0);
                lonPasien = getIntent().getDoubleExtra("lon_pasien", 0);
            }
        }

        tvNamaDriver.setText(namaDriver);
        tvPlatNomor.setText(platNomor);
        tvEstimasi.setText("Menghitung estimasi...");

        if (idAmbulans != null && !idAmbulans.isEmpty()) {
            fetchAmbulanceDetails(idAmbulans);
        }

        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map);
        if (mapFragment != null) mapFragment.getMapAsync(this);

        mqttManager = MqttClientManager.getInstance();

        fabMyLocation.setOnClickListener(v -> {
            if (mMap != null && latPasien != 0) {
                mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(latPasien, lonPasien), 17));
            }
        });
    }

    private void fetchAmbulanceDetails(String id) {
        RetrofitClient.getInstance().getAmbulansDetail(id).enqueue(new Callback<ApiService.AmbulansResponse>() {
            @Override
            public void onResponse(@NonNull Call<ApiService.AmbulansResponse> call, @NonNull retrofit2.Response<ApiService.AmbulansResponse> response) {
                if (response.isSuccessful() && response.body() != null && !response.body().error) {
                    ApiService.AmbulansData data = response.body().data;
                    if (data != null) {
                        Log.d(TAG, "API Detail Sukses: " + data.namaDriver + " - " + (data.nomorPolisi != null ? data.nomorPolisi : data.noPolisi));
                        namaDriver = data.namaDriver;
                        platNomor = data.nomorPolisi != null ? data.nomorPolisi : data.noPolisi;
                        runOnUiThread(() -> {
                            tvNamaDriver.setText(namaDriver);
                            tvPlatNomor.setText(platNomor);
                        });
                    }
                } else {
                    Log.e(TAG, "API Detail Gagal. Code: " + response.code());
                }
            }
            @Override
            public void onFailure(@NonNull Call<ApiService.AmbulansResponse> call, @NonNull Throwable t) {
                Log.e(TAG, "API Detail Failure (Cek apakah server mengembalikan JSON valid): " + t.getMessage());
            }
        });
    }

    private void updateDriverInfoIfMissing(JSONObject data) {
        try {
            String name = data.optString("nama_driver", "");
            if (name.isEmpty()) name = data.optString("nama", "");
            
            String plate = data.optString("nomor_polisi", "");
            if (plate.isEmpty()) plate = data.optString("plat_nomor", "");
            if (plate.isEmpty()) plate = data.optString("no_polisi", "");

            boolean isUpdated = false;
            if (!name.isEmpty() && (namaDriver == null || namaDriver.contains("Memuat"))) {
                namaDriver = name;
                isUpdated = true;
            }
            if (!plate.isEmpty() && (platNomor == null || platNomor.contains("Loading") || platNomor.equals("..."))) {
                platNomor = plate;
                isUpdated = true;
            }

            if (isUpdated) {
                Log.d(TAG, "Profil diperbarui dari MQTT: " + namaDriver + " (" + platNomor + ")");
                runOnUiThread(() -> {
                    tvNamaDriver.setText(namaDriver);
                    tvPlatNomor.setText(platNomor);
                });
            }
        } catch (Exception e) {
            Log.e(TAG, "Gagal update info dari JSON: " + e.getMessage());
        }
    }

    @Override
    public void onMapReady(@NonNull GoogleMap googleMap) {
        mMap = googleMap;
        mMap.setTrafficEnabled(true);
        mMap.setMapType(GoogleMap.MAP_TYPE_HYBRID);
        updatePatientMarker();
        if (latPasien != 0) {
            mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(latPasien, lonPasien), 15));
        }
        connectAndSubscribe();
    }

    private void updatePatientMarker() {
        if (mMap == null || latPasien == 0) return;
        LatLng pos = new LatLng(latPasien, lonPasien);
        if (patientMarker == null) {
            patientMarker = mMap.addMarker(new MarkerOptions().position(pos).title("Lokasi Saya").icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_BLUE)));
        } else {
            patientMarker.setPosition(pos);
        }
    }

    private void connectAndSubscribe() {
        if (!mqttManager.isConnected()) {
            mqttManager.connect(new MqttClientManager.ConnectionListener() {
                @Override
                public void onSuccess() { subscribeTopics(); }
                @Override
                public void onError(String err) { Log.e(TAG, "MQTT Connect Error: " + err); }
            });
        } else {
            subscribeTopics();
        }
    }

    private void subscribeTopics() {
        if (idAmbulans != null) {
            mqttManager.subscribe("ambulans/lokasi/update/" + idAmbulans, (topic, msg) -> runOnUiThread(() -> processLocationUpdate(msg)));
        }
        if (myPatientId != null) {
            mqttManager.subscribe("panggilan/status/pasien/" + myPatientId, (topic, msg) -> runOnUiThread(() -> processStatusUpdate(msg)));
        }
        if (idPanggilan != null) {
            mqttManager.subscribe("panggilan/status/update/" + idPanggilan, (topic, msg) -> runOnUiThread(() -> processStatusUpdate(msg)));
        }
    }

    private void processStatusUpdate(String message) {
        Log.d(TAG, "Status Update Recv: " + message);
        try {
            if (message.trim().startsWith("{")) {
                JSONObject data = new JSONObject(message);
                updateDriverInfoIfMissing(data);
                String status = data.optString("status_panggilan", data.optString("status", ""));
                if (status.equalsIgnoreCase("selesai") || status.equalsIgnoreCase("arrived")) showCompletionDialog();
            }
        } catch (JSONException e) { Log.e(TAG, "Status Parse Error", e); }
    }

    private void processLocationUpdate(String jsonString) {
        try {
            JSONObject data = new JSONObject(jsonString);
            updateDriverInfoIfMissing(data);

            double lat = data.optDouble("lokasi_latitude", 0);
            double lon = data.optDouble("lokasi_longitude", 0);
            if (lat == 0) return;

            float bearing = (float) data.optDouble("bearing", 0);
            LatLng ambPos = new LatLng(lat, lon);
            LatLng pasPos = new LatLng(latPasien, lonPasien);

            if (patientMarker != null && getDistance(ambPos, pasPos) < 20) {
                patientMarker.remove();
                patientMarker = null;
            }

            if (lastRoutedLocation == null || getDistance(lastRoutedLocation, ambPos) > 300) {
                fetchRoute(ambPos, pasPos);
                lastRoutedLocation = ambPos;
            }

            if (ambulanceMarker == null) {
                ambulanceMarker = mMap.addMarker(new MarkerOptions().position(ambPos).title("Ambulans").icon(getResizedMarkerIcon(R.drawable.ic_ambulance_top_view, 120, 120)).anchor(0.5f, 0.5f).flat(true));
                mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(ambPos, 17));
            } else {
                animateMarker(ambulanceMarker, ambPos, bearing);
            }
        } catch (Exception e) { Log.e(TAG, "Loc Error", e); }
    }

    private void showCompletionDialog() {
        if (isFinishing() || isDestroyed()) return;
        new AlertDialog.Builder(this)
                .setTitle("Layanan Selesai")
                .setMessage("Driver telah mencapai lokasi atau menyelesaikan panggilan.")
                .setCancelable(false)
                .setPositiveButton("OK", (d, w) -> {
                    startActivity(new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP));
                    finish();
                }).show();
    }

    private double getDistance(LatLng p1, LatLng p2) {
        float[] res = new float[1];
        android.location.Location.distanceBetween(p1.latitude, p1.longitude, p2.latitude, p2.longitude, res);
        return res[0];
    }

    private void animateMarker(Marker marker, LatLng toPos, float rotation) {
        long start = SystemClock.uptimeMillis();
        LatLng startPos = marker.getPosition();
        handler.post(new Runnable() {
            @Override
            public void run() {
                long elapsed = SystemClock.uptimeMillis() - start;
                float t = new LinearInterpolator().getInterpolation((float) elapsed / 1000);
                marker.setPosition(new LatLng(t * toPos.latitude + (1 - t) * startPos.latitude, t * toPos.longitude + (1 - t) * startPos.longitude));
                marker.setRotation(rotation);
                if (t < 1.0) handler.postDelayed(this, 16);
            }
        });
    }

    private BitmapDescriptor getResizedMarkerIcon(int resId, int w, int h) {
        Drawable d = ContextCompat.getDrawable(this, resId);
        Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        if (d != null) {
            d.setBounds(0, 0, w, h);
            d.draw(c);
        }
        return BitmapDescriptorFactory.fromBitmap(b);
    }

    private void fetchRoute(LatLng origin, LatLng dest) {
        executor.execute(() -> {
            try {
                String key = getPackageManager().getApplicationInfo(getPackageName(), PackageManager.GET_META_DATA).metaData.getString("com.google.android.geo.API_KEY");
                String url = "https://maps.googleapis.com/maps/api/directions/json?origin=" + origin.latitude + "," + origin.longitude + "&destination=" + dest.latitude + "," + dest.longitude + "&key=" + key;
                
                try (Response resp = new OkHttpClient().newCall(new Request.Builder().url(url).build()).execute()) {
                    ResponseBody body = resp.body();
                    if (body == null) return;
                    JSONObject json = new JSONObject(body.string());
                    if ("OK".equals(json.optString("status"))) {
                        JSONObject leg = json.getJSONArray("routes").getJSONObject(0).getJSONArray("legs").getJSONObject(0);
                        String durationText = leg.getJSONObject("duration").getString("text");
                        List<LatLng> path = PolyUtil.decode(json.getJSONArray("routes").getJSONObject(0).getJSONObject("overview_polyline").getString("points"));
                        
                        handler.post(() -> {
                            if (currentPolyline != null) currentPolyline.remove();
                            currentPolyline = mMap.addPolyline(new PolylineOptions().addAll(path).color(Color.BLUE).width(15));
                            tvEstimasi.setText("Tiba dalam " + durationText);
                        });
                    }
                }
            } catch (Exception e) { Log.e(TAG, "Route Error", e); }
        });
    }
}
