package com.example.patientapp;

import android.content.Context;
import android.util.Log;

import com.hivemq.client.mqtt.MqttClient;
import com.hivemq.client.mqtt.mqtt3.Mqtt3AsyncClient;

import java.nio.charset.StandardCharsets;
import java.util.UUID;


public class MqttClientManager {

    private static final String TAG = "MqttClientManager";
    private static MqttClientManager instance;
    private Mqtt3AsyncClient client;

    private static final String SERVER_HOST = BuildConfig.MQTT_HOST;
    private static final int SERVER_PORT = 1883;
    private static final String USERNAME = BuildConfig.MQTT_USERNAME;
    private static final String PASSWORD = BuildConfig.MQTT_PASSWORD;

    private MqttClientManager() {
        // Private Constructor
    }

    public static synchronized MqttClientManager getInstance() {
        if (instance == null) {
            instance = new MqttClientManager();
        }
        return instance;
    }

    // Cek Status Koneksi
    public boolean isConnected() {
        return client != null && client.getState().isConnected();
    }

    // --- FUNGSI CONNECT (SIMPEL) ---
    // Callback opsional: Runnable onSuccess, Consumer<Throwable> onError
    public interface ConnectionListener {
        void onSuccess();

        void onError(String errorMessage);
    }

    public void connect(ConnectionListener listener) {
        if (isConnected()) {
            if (listener != null) listener.onSuccess();
            return;
        }

        String clientId = "Android_" + UUID.randomUUID().toString().substring(0, 8);

        // Build Client Dasar
        client = MqttClient.builder()
                .useMqttVersion3()
                .identifier(clientId)
                .serverHost(SERVER_HOST)
                .serverPort(SERVER_PORT)
                .automaticReconnectWithDefaultConfig() // Auto Reconnect
                .buildAsync();

        // Eksekusi Koneksi dengan Autentikasi
        client.connectWith()
                .simpleAuth() // Tambahkan Autentikasi
                .username(USERNAME)
                .password(PASSWORD.getBytes(StandardCharsets.UTF_8))
                .applySimpleAuth()
                .cleanSession(true)
                .keepAlive(60)
                .send()
                .whenComplete((connAck, throwable) -> {
                    if (throwable != null) {
                        Log.e(TAG, "Gagal Connect: " + throwable.getMessage());
                        if (listener != null) listener.onError(throwable.getMessage());
                    } else {
                        Log.d(TAG, "BERHASIL CONNECT ke " + SERVER_HOST);
                        if (listener != null) listener.onSuccess();
                    }
                });
    }

    // Interface Callback Pesan
    public interface MessageListener {
        void onMessage(String topic, String message);
    }

    public void subscribe(String topic, MessageListener listener) {
        if (client == null) return;

        client.subscribeWith()
                .topicFilter(topic)
                .callback(publish -> {
                    // Convert byte[] ke String otomatis
                    String message = new String(publish.getPayloadAsBytes(), StandardCharsets.UTF_8);
                    Log.d(TAG, "Pesan masuk [" + topic + "]: " + message);

                    if (listener != null) {
                        listener.onMessage(topic, message);
                    }
                })
                .send()
                .whenComplete((subAck, throwable) -> {
                    if (throwable != null) {
                        Log.e(TAG, "Gagal Subscribe: " + topic);
                    } else {
                        Log.d(TAG, "Sukses Subscribe: " + topic);
                    }
                });
    }

    // --- FUNGSI PUBLISH ---
    public void publish(String topic, String message) {
        if (!isConnected()) return;

        client.publishWith()
                .topic(topic)
                .payload(message.getBytes(StandardCharsets.UTF_8))
                .send()
                .whenComplete((publish, throwable) -> {
                    if (throwable != null) {
                        Log.e(TAG, "Gagal Publish ke " + topic);
                    } else {
                        Log.d(TAG, "Terkirim ke " + topic);
                    }
                });
    }

    public void disconnect() {
        if (client != null) {
            client.disconnect();
        }
    }
}

    // Interface untuk Callback Pesan (Lambda Friendly)
//    public interface MessageListener {
//        void onMessageReceived(String topic, MqttMessage message);
//    }
//
//    // Simpan listener sementara (Simple version)
//    private MessageListener currentListener;
//
//    private MqttClientManager() {
//        // Singleton Constructor
//    }

//    public static synchronized MqttClientManager getInstance(Context context) {
//        if (instance == null) {
//            instance = new MqttClientManager();
//        }
//        return instance;
//    }
//
//    // --- FIX ERROR 1: Tambah Method isConnected ---
//    public boolean isConnected() {
//        return client != null && client.isConnected();
//    }

    // --- FIX ERROR 2: Method connect yang simpel ---
//    public void connect(IMqttActionListener callback) {
//        // Kita butuh context, tapi karena Singleton ini dipanggil di Activity,
//        // idealnya inisialisasi client dilakukan terpisah atau pass Context disini.
//        // TAPI, agar kode TrackingActivity tidak error, kita akan mengakali sedikit.
//        Log.e(TAG, "Gunakan connect(Context, Callback) agar lebih aman!");
//    }

//    // Method Connect yang SEBENARNYA dipakai
//    public void connect(Context context, IMqttActionListener externalCallback) {
//        String clientId = MqttClient.generateClientId();
//        client = new MqttAndroidClient(context.getApplicationContext(), SERVER_URI, clientId);
//
//        client.setCallback(new MqttCallbackExtended() {
//            @Override
//            public void connectComplete(boolean reconnect, String serverURI) {
//                Log.d(TAG, "Connected to: " + serverURI);
//            }
//            @Override
//            public void connectionLost(Throwable cause) {
//                Log.e(TAG, "Connection Lost");
//            }
//            @Override
//            public void messageArrived(String topic, MqttMessage message) throws Exception {
//                // Teruskan ke listener yang aktif
//                if (currentListener != null) {
//                    currentListener.onMessageReceived(topic, message);
//                }
//            }
//            @Override
//            public void deliveryComplete(IMqttDeliveryToken token) {}
//        });
//
//        MqttConnectOptions options = new MqttConnectOptions();
//        options.setAutomaticReconnect(true);
//        options.setCleanSession(true);
//
//        try {
//            client.connect(options, null, externalCallback);
//        } catch (MqttException e) {
//            e.printStackTrace();
//        }
//    }
//
//    // --- FIX ERROR 3: Subscribe dengan Callback (Lambda) ---
//    public void subscribe(String topic, MessageListener listener) {
//        if (client != null && client.isConnected()) {
//            try {
//                // Simpan listenernya
//                this.currentListener = listener;
//
//                client.subscribe(topic, 1, null, new IMqttActionListener() {
//                    @Override
//                    public void onSuccess(IMqttToken asyncActionToken) {
//                        Log.d(TAG, "Subscribed to " + topic);
//                    }
//                    @Override
//                    public void onFailure(IMqttToken asyncActionToken, Throwable exception) {
//                        Log.e(TAG, "Failed to subscribe");
//                    }
//                });
//            } catch (MqttException e) {
//                e.printStackTrace();
//            }
//        }
//    }
//
//    // Helper untuk generate ID
//    public static class MqttClient {
//        public static String generateClientId() {
//            return "Android_" + System.currentTimeMillis();
//        }
//    }
//}