package com.example.patientapp;

import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.example.patientapp.adapter.HistoryAdapter;
import com.example.patientapp.api.RetrofitClient;
import com.example.patientapp.api.ApiService; // Pastikan import ApiService benar
import com.example.patientapp.model.HistoryModel;
import com.example.patientapp.utils.ToastHelper;

import java.util.ArrayList;
import java.util.List;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class HistoryActivity extends AppCompatActivity {

    private RecyclerView rvHistory;
    private HistoryAdapter adapter;
    private List<HistoryModel> listHistory = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_history);

        // 1. Setup RecyclerView
        rvHistory = findViewById(R.id.rvHistory);
        rvHistory.setLayoutManager(new LinearLayoutManager(this));

        // 2. Ambil ID User dari Sesi
        SessionManager session = new SessionManager(this);
        String idPasien = session.getUserDetails().get(SessionManager.KEY_ID);

        // 3. Panggil API
        loadHistory(idPasien);
    }

    private void loadHistory(String idPasien) {
        // Panggil endpoint getRiwayat
        RetrofitClient.getInstance().getRiwayat(idPasien).enqueue(new Callback<ApiService.HistoryResponse>() {
            @Override
            public void onResponse(Call<ApiService.HistoryResponse> call, Response<ApiService.HistoryResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    listHistory = response.body().getData();

                    // Pasang Adapter
                    adapter = new HistoryAdapter(HistoryActivity.this, listHistory);
                    rvHistory.setAdapter(adapter);

                    if (listHistory.isEmpty()) {
                        ToastHelper.showToast(HistoryActivity.this, "Belum ada riwayat panggilan.");
                    }
                } else {
                    ToastHelper.showToast(HistoryActivity.this, "Gagal memuat data.");
                }
            }

            @Override
            public void onFailure(Call<ApiService.HistoryResponse> call, Throwable t) {
                ToastHelper.showToast(HistoryActivity.this, "Koneksi Error: " + t.getMessage());
            }
        });
    }
}