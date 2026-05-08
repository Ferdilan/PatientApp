package com.example.patientapp;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import java.util.HashMap;
import com.bumptech.glide.Glide;

public class ProfileFragment extends Fragment {

    private SessionManager session;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_profile, container, false);

        TextView tvName = view.findViewById(R.id.tvProfileName);
        TextView tvInfoName = view.findViewById(R.id.tvInfoName);
        TextView tvNik = view.findViewById(R.id.tvProfileNik);
        TextView tvAlamat = view.findViewById(R.id.tvProfileAlamat);
        TextView tvTglLahir = view.findViewById(R.id.tvProfileTglLahir);
        TextView tvJk = view.findViewById(R.id.tvProfileJenisKelamin);
        ImageView imgKtp = view.findViewById(R.id.imgProfileKtp);
        Button btnLogout = view.findViewById(R.id.btnLogout);

        session = new SessionManager(requireContext());
        HashMap<String, String> user = session.getUserDetails();

        String name = user.get(SessionManager.KEY_NAMA);
        String nik = user.get(SessionManager.KEY_NIK);
        String alamat = user.get(SessionManager.KEY_ALAMAT);
        String tgl = user.get(SessionManager.KEY_TGL);
        String jk = user.get(SessionManager.KEY_JK);
        String fotoKTP = user.get(SessionManager.KEY_FOTO);

        tvName.setText(name);
        tvInfoName.setText(name);
        tvNik.setText(nik != null ? nik : "-");
        tvAlamat.setText(alamat != null ? alamat : "-");
        tvTglLahir.setText(tgl != null ? tgl : "-");
        tvJk.setText(jk != null ? jk : "-");

        String baseUrl = "https://scared-prewashed-garden.ngrok-free.dev";
        String urlLengkap = baseUrl + fotoKTP;

        Glide.with(this)
                .load(urlLengkap)
                .placeholder(android.R.drawable.ic_menu_gallery)
                .error(android.R.drawable.ic_menu_report_image)
                .into(imgKtp);

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