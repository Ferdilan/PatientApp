package com.example.patientapp;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.patientapp.adapter.HistoryAdapter;
import com.example.patientapp.api.RetrofitClient;
import com.example.patientapp.api.ApiService;
import com.example.patientapp.model.HistoryModel;
import com.example.patientapp.utils.ToastHelper;

import java.util.ArrayList;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class HistoryFragment extends Fragment {

    private RecyclerView rvHistory;
    private ProgressBar pbHistory;
    private TextView tvEmptyState;
    private HistoryAdapter adapter;
    private final List<HistoryModel> listHistory = new ArrayList<>();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_history, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // 1. Inisialisasi View
        rvHistory = view.findViewById(R.id.rvHistory);
        pbHistory = view.findViewById(R.id.pbHistory);
        tvEmptyState = view.findViewById(R.id.tvEmptyState);

        // 2. Setup RecyclerView & Adapter (Hanya Sekali)
        rvHistory.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new HistoryAdapter(requireContext(), listHistory);
        rvHistory.setAdapter(adapter);

        // 3. Ambil Data
        SessionManager session = new SessionManager(requireContext());
        String idPasien = session.getUserDetails().get(SessionManager.KEY_ID);
        
        loadHistory(idPasien);
    }

    private void loadHistory(String idPasien) {
        // Latency Blindness: Tampilkan Loading
        pbHistory.setVisibility(View.VISIBLE);
        tvEmptyState.setVisibility(View.GONE);

        RetrofitClient.getInstance().getRiwayat(idPasien).enqueue(new Callback<ApiService.HistoryResponse>() {
            @Override
            public void onResponse(@NonNull Call<ApiService.HistoryResponse> call, @NonNull Response<ApiService.HistoryResponse> response) {
                if (!isAdded()) return;

                // Matikan Loading di baris pertama
                pbHistory.setVisibility(View.GONE);

                if (response.isSuccessful() && response.body() != null) {
                    List<HistoryModel> dataIncoming = response.body().getData();
                    
                    // Memory Handling: Update list yang sudah ada
                    listHistory.clear();
                    if (dataIncoming != null) {
                        listHistory.addAll(dataIncoming);
                    }
                    adapter.notifyDataSetChanged();

                    // Empty State Logic
                    if (listHistory.isEmpty()) {
                        tvEmptyState.setVisibility(View.VISIBLE);
                        rvHistory.setVisibility(View.GONE);
                    } else {
                        tvEmptyState.setVisibility(View.GONE);
                        rvHistory.setVisibility(View.VISIBLE);
                    }
                } else {
                    ToastHelper.showToast(requireContext(), "Gagal mengambil data riwayat");
                }
            }

            @Override
            public void onFailure(@NonNull Call<ApiService.HistoryResponse> call, @NonNull Throwable t) {
                if (!isAdded()) return;
                
                // Matikan Loading
                pbHistory.setVisibility(View.GONE);
                ToastHelper.showToast(requireContext(), "Masalah koneksi: " + t.getMessage());
            }
        });
    }
}