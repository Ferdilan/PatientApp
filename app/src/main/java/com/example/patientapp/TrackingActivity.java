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

import androidx.appcompat.app.AlertDialog;
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
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
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

    private SessionManager session;
    private String myPatientId;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private Handler handler = new Handler();

    private TextView tvNamaDriver, tvPlatNomor, tvEstimasi;
    private FloatingActionButton fabMyLocation;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_tracking);

        session = new SessionManager(this);
        myPatientId = session.getUserId();
        
        Log.d(TAG, "Tracking UI started. Patient ID: " + myPatientId);

        tvNamaDriver = findViewById(R.id.tvDriverName);
        tvPlatNomor = findViewById(R.id.tvLicensePlate);
        tvEstimasi = findViewById(R.id.tvEstimasi);
        fabMyLocation = findViewById(R.id.fabMyLocation);

        if (getIntent() != null) {
            idAmbulans = getIntent().getStringExtra("id_ambulans");
            idPanggilan = getIntent().getStringExtra("id_panggilan");
            namaDriver = getIntent().getStringExtra("nama_driver");
            platNomor = getIntent().getStringExtra("plat_nomor");

            if (namaDriver == null) namaDriver = "Driver Ambulans";

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
        tvPlatNomor.setText(platNomor != null ? platNomor : "---");
        tvEstimasi.setText("Menghitung estimasi...");

        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map);
        if (mapFragment != null) mapFragment.getMapAsync(this);

        mqttManager = MqttClientManager.getInstance();

        fabMyLocation.setOnClickListener(v -> {
            if (mMap != null && latPasien != 0) {
                mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(latPasien, lonPasien), 17));
            }
        });
    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;
        
        // Mengaktifkan lapisan lalu lintas (traffic layer)
        mMap.setTrafficEnabled(true);
        
        // Mengubah tipe peta ke NORMAL agar indikator lalu lintas lebih jelas terlihat
        mMap.setMapType(GoogleMap.MAP_TYPE_NORMAL);
        
        // Aktifkan kontrol UI standar
        mMap.getUiSettings().setZoomControlsEnabled(true);
        mMap.getUiSettings().setCompassEnabled(true);

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
            patientMarker = mMap.addMarker(new MarkerOptions()
                    .position(pos)
                    .title("Lokasi Saya")
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_BLUE)));
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
        // 1. Subscribe Lokasi Ambulans
        if (idAmbulans != null) {
            String locTopic = "ambulans/lokasi/update/" + idAmbulans;
            mqttManager.subscribe(locTopic, (topic, msg) -> runOnUiThread(() -> processLocationUpdate(msg)));
        }
        
        // 2. Subscribe Status via ID Pasien
        if (myPatientId != null) {
            String statusTopic = "panggilan/status/pasien/" + myPatientId;
            Log.d(TAG, "Subscribing to Status Topic: " + statusTopic);
            mqttManager.subscribe(statusTopic, (topic, msg) -> {
                Log.d(TAG, "STATUS MESSAGE RECEIVED: " + msg);
                runOnUiThread(() -> processStatusUpdate(msg));
            });
        }

        // 3. Subscribe Status via ID Panggilan
        if (idPanggilan != null) {
            String callTopic = "panggilan/status/update/" + idPanggilan;
            Log.d(TAG, "Subscribing to Call Status Topic: " + callTopic);
            mqttManager.subscribe(callTopic, (topic, msg) -> {
                Log.d(TAG, "CALL STATUS RECEIVED: " + msg);
                runOnUiThread(() -> processStatusUpdate(msg));
            });
        }
    }

    private void processStatusUpdate(String message) {
        String status = "";
        try {
            if (message.trim().startsWith("{")) {
                JSONObject data = new JSONObject(message);
                status = data.optString("status_panggilan", "");
                if (status.isEmpty()) status = data.optString("status", "");
                if (status.isEmpty()) status = data.optString("panggilan_status", "");
            } else {
                status = message.trim();
            }
        } catch (JSONException e) {
            status = message.trim();
        }

        Log.d(TAG, "Evaluated Status: [" + status + "]");

        if (status.equalsIgnoreCase("selesai") || 
            status.equalsIgnoreCase("completed") || 
            status.equalsIgnoreCase("finished") || 
            status.equalsIgnoreCase("arrived")) {
            
            showCompletionDialog();
        }
    }

    private void showCompletionDialog() {
        if (isFinishing() || isDestroyed()) return;

        Log.i(TAG, "Displaying Completion Dialog");
        
        // Hentikan langganan agar tidak memakan baterai/data
        if (mqttManager != null) {
            mqttManager.unsubscribe("ambulans/lokasi/update/" + idAmbulans);
            if (myPatientId != null) mqttManager.unsubscribe("panggilan/status/pasien/" + myPatientId);
            if (idPanggilan != null) mqttManager.unsubscribe("panggilan/status/update/" + idPanggilan);
        }

        new AlertDialog.Builder(this)
                .setTitle("Layanan Selesai")
                .setMessage("Driver telah menyelesaikan panggilan ini. Semoga Anda sehat selalu.")
                .setCancelable(false)
                .setPositiveButton("Kembali ke Beranda", (d, w) -> {
                    Intent i = new Intent(TrackingActivity.this, MainActivity.class);
                    i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(i);
                    finish();
                })
                .show();
    }

    private void processLocationUpdate(String jsonString) {
        try {
            JSONObject data = new JSONObject(jsonString);
            
            // Cek jika status selesai terselip di pesan lokasi
            String embeddedStatus = data.optString("status", "");
            if (embeddedStatus.equalsIgnoreCase("selesai") || embeddedStatus.equalsIgnoreCase("arrived")) {
                showCompletionDialog();
                return;
            }

            double lat = data.optDouble("lokasi_latitude", 0);
            double lon = data.optDouble("lokasi_longitude", 0);
            if (lat == 0) return;

            float bearing = (float) data.optDouble("bearing", 0);
            LatLng ambPos = new LatLng(lat, lon);
            LatLng pasPos = new LatLng(latPasien, lonPasien);

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

    private double getDistance(LatLng p1, LatLng p2) {
        float[] res = new float[1];
        android.location.Location.distanceBetween(p1.latitude, p1.longitude, p2.latitude, p2.longitude, res);
        return res[0];
    }

    private void animateMarker(Marker marker, LatLng toPos, float rotation) {
        long start = SystemClock.uptimeMillis();
        LatLng startPos = marker.getPosition();
        long duration = 1000;
        Interpolator interp = new LinearInterpolator();
        handler.post(new Runnable() {
            @Override
            public void run() {
                long elapsed = SystemClock.uptimeMillis() - start;
                float t = interp.getInterpolation((float) elapsed / duration);
                double lat = t * toPos.latitude + (1 - t) * startPos.latitude;
                double lng = t * toPos.longitude + (1 - t) * startPos.longitude;
                marker.setPosition(new LatLng(lat, lng));
                marker.setRotation(rotation);
                if (t < 1.0) handler.postDelayed(this, 16);
            }
        });
    }

    private BitmapDescriptor getResizedMarkerIcon(int resId, int w, int h) {
        Drawable d = ContextCompat.getDrawable(this, resId);
        if (d == null) return null;
        Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        d.setBounds(0, 0, w, h);
        d.draw(c);
        return BitmapDescriptorFactory.fromBitmap(b);
    }

    private void fetchRoute(LatLng origin, LatLng dest) {
        if (dest.latitude == 0) return;
        executor.execute(() -> {
            try {
                String key = getPackageManager().getApplicationInfo(getPackageName(), PackageManager.GET_META_DATA).metaData.getString("com.google.android.geo.API_KEY");
                String url = "https://maps.googleapis.com/maps/api/directions/json?origin=" + origin.latitude + "," + origin.longitude + "&destination=" + dest.latitude + "," + dest.longitude + "&key=" + key;
                Response resp = new OkHttpClient().newCall(new Request.Builder().url(url).build()).execute();
                JSONObject json = new JSONObject(resp.body().string());
                if ("OK".equals(json.optString("status"))) {
                    JSONObject route = json.getJSONArray("routes").getJSONObject(0);
                    JSONObject leg = route.getJSONArray("legs").getJSONObject(0);
                    List<LatLng> path = PolyUtil.decode(route.getJSONObject("overview_polyline").getString("points"));
                    String dur = leg.getJSONObject("duration").getString("text");
                    String dist = leg.getJSONObject("distance").getString("text");
                    handler.post(() -> {
                        if (currentPolyline != null) currentPolyline.remove();
                        currentPolyline = mMap.addPolyline(new PolylineOptions().addAll(path).color(Color.BLUE).width(15));
                        tvEstimasi.setText("Tiba dalam " + dur + " (" + dist + ")");
                    });
                }
            } catch (Exception e) { Log.e(TAG, "Route Error", e); }
        });
    }
}
