package com.example.patientapp;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import java.util.HashMap;

public class ProfileFragment extends Fragment {

    private SessionManager session;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_profile, container, false);

        // UI Components yang tetap ditampilkan
        TextView tvName = view.findViewById(R.id.tvProfileName);
        TextView tvInfoName = view.findViewById(R.id.tvInfoName);
        TextView tvNik = view.findViewById(R.id.tvProfileNik);
        Button btnLogout = view.findViewById(R.id.btnLogout);

        // Inisialisasi Session
        session = new SessionManager(requireContext());
        HashMap<String, String> user = session.getUserDetails();

        // Ambil data Nama dan NIK yang stabil
        String name = user.get(SessionManager.KEY_NAMA);
        String nik = user.get(SessionManager.KEY_NIK);

        // Set Data ke UI dengan fallback placeholder yang rapi
        tvName.setText(name != null && !name.isEmpty() ? name : "Pasien");
        tvInfoName.setText(name != null && !name.isEmpty() ? name : "-");
        tvNik.setText(nik != null && !nik.isEmpty() ? nik : "-");

        // Logika Logout
        btnLogout.setOnClickListener(v -> {
            session.logoutUser();
            Intent i = new Intent(getActivity(), WelcomeActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
            if (getActivity() != null) getActivity().finish();
        });

        return view;
    }
}
