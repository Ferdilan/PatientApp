package com.example.patientapp.adapter;

import android.content.Context;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.example.patientapp.R;
import com.example.patientapp.model.HistoryModel;
import java.util.List;

public class HistoryAdapter extends RecyclerView.Adapter<HistoryAdapter.ViewHolder> {

    private Context context;
    private List<HistoryModel> listHistory;

    public HistoryAdapter(Context context, List<HistoryModel> listHistory) {
        this.context = context;
        this.listHistory = listHistory;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_history, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        HistoryModel history = listHistory.get(position);

        holder.tvType.setText(history.getJenisLayanan());
        holder.tvStatus.setText("Status: " + history.getStatus());

        // Format Tanggal Sederhana (Bisa dipercantik nanti)
        holder.tvDate.setText(history.getTanggal().substring(0, 10));

        holder.tvDriver.setText("Driver: " + history.getNamaDriver());

        // Logika Warna Status
        if ("COMPLETED".equals(history.getStatus())) {
            holder.tvStatus.setTextColor(Color.parseColor("#388E3C")); // Hijau
        } else if ("CANCELLED".equals(history.getStatus())) {
            holder.tvStatus.setTextColor(Color.RED);
        } else {
            holder.tvStatus.setTextColor(Color.BLUE); // Pending/Proses
        }
    }

    @Override
    public int getItemCount() {
        return listHistory.size();
    }

    public class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvType, tvStatus, tvDate, tvDriver;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvType = itemView.findViewById(R.id.tvHistoryType);
            tvStatus = itemView.findViewById(R.id.tvHistoryStatus);
            tvDate = itemView.findViewById(R.id.tvHistoryDate);
            tvDriver = itemView.findViewById(R.id.tvHistoryDriver);
        }
    }
}