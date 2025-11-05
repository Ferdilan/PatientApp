package com.example.patientapp;

import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import org.eclipse.paho.client.mqttv3.MqttMessage;

import java.util.UUID;

public class MainActivity extends AppCompatActivity implements MqttClientManager.MqttMessageListener{

    private static final String TAG = "MainActivity";

    //    private static final String BROKER_URI = "tcp://mustang.rmq.cloudamqp.com:1883";
    private String clientId;

    private MqttClientManager mqttManager;
    private TextView statusTextView;
    private Button requestHelpButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        statusTextView = findViewById(R.id.statusTextView); // Asumsikan Anda punya TextView ini di layout
        requestHelpButton = findViewById(R.id.requestHelpButton); // Asumsikan Anda punya Button ini

        String brokerUri = "tcp://mustang.rmq.cloudamqp.com:1883";
        this.clientId = "android-client-" + UUID.randomUUID().toString();

        String username = "hjppbzvg:hjppbzvg";
        String password = "zUg3ysf369ZnibIfjtSc7Qtj-ezmi5IB";

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

    private void setupButtonListener() {
        requestHelpButton.setOnClickListener(view -> {
            String topic = "pasien/request";
            // Buat payload JSON sesuai struktur yang telah kita definisikan
            String payload = "{\"patientId\":\"" + this.clientId + "\", \"name\":\"Pasien Darurat\", \"location\":{\"latitude\":-6.2088, \"longitude\":106.8456}}";

            // Publikasikan pesan dengan Quality of Service level 1 (at least once)
            mqttManager.publish(topic, payload, 0);

            Toast.makeText(this, "Permintaan bantuan dikirim...", Toast.LENGTH_SHORT).show();
            statusTextView.setText("Menunggu respons...");
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Setelah koneksi berhasil, subscribe ke topik notifikasi pribadi
        // Delay kecil untuk memastikan koneksi sudah stabil
//        new android.os.Handler().postDelayed(
//                () -> mqttManager.subscribe("client/" + clientId + "/notification", 1),
//                2000 // 2 detik
//        );
    }

    @Override
    public void onMessageReceived(String topic, MqttMessage message) {
        String payload = new String(message.getPayload());
        Log.d(TAG, "Pesan balasan diterima: " + payload);

        // Callback MQTT berjalan di background thread, untuk update UI, kita harus pindah ke Main Thread
        runOnUiThread(() -> {
            statusTextView.setText("Respons Diterima: " + payload);
            // Di sini Anda akan mem-parsing JSON dan menampilkan informasi yang lebih relevan
            // Contoh: "Ambulans dengan plat B 1234 XYZ sedang menuju lokasi Anda."
        });
    }

    @Override
    protected void onDestroy() {
        // Sangat penting untuk memutus koneksi saat activity dihancurkan
        // untuk mencegah kebocoran memori (memory leak) dan menghemat baterai.
        mqttManager.disconnect();
        super.onDestroy();
    }
}