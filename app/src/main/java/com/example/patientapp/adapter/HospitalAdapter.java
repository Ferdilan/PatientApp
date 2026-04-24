package com.example.patientapp.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.example.patientapp.R;
import com.example.patientapp.model.HospitalModel;
import java.util.List;

public class HospitalAdapter extends RecyclerView.Adapter<HospitalAdapter.ViewHolder> {

    private List<HospitalModel> hospitalList;

    public HospitalAdapter(List<HospitalModel> hospitalList) {
        this.hospitalList = hospitalList;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_hospital, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        HospitalModel hospital = hospitalList.get(position);
        holder.tvName.setText(hospital.getName());
        holder.tvAddress.setText(hospital.getAddress());
        holder.tvRating.setText(String.valueOf(hospital.getRating()));
    }

    @Override
    public int getItemCount() {
        return hospitalList.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvName, tvAddress, tvRating;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvName = itemView.findViewById(R.id.tvHospitalName);
            tvAddress = itemView.findViewById(R.id.tvHospitalAddress);
            tvRating = itemView.findViewById(R.id.tvHospitalRating);
        }
    }
}