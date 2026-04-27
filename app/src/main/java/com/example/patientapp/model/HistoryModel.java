package com.example.patientapp.model;

import com.google.gson.annotations.SerializedName;

public class HistoryModel {
    @SerializedName("id_panggilan")
    private int id;

    @SerializedName("jenis_layanan")
    private String jenisLayanan;

    @SerializedName("status_panggilan")
    private String status;

    @SerializedName("createdat") // Huruf kecil semua sesuai JSON response
    private String tanggal;

    // Relasi ke Ambulans (Nested Object)
    @SerializedName("Ambulan") // Perhatikan huruf besar/kecil sesuai response JSON server
    private Ambulan ambulan;

    // Getter
    public String getJenisLayanan() { return jenisLayanan; }
    public String getStatus() { return status; }
    public String getTanggal() { return tanggal; }

    public String getNamaDriver() {
        if (ambulan != null) return ambulan.namaDriver;
        return "Belum ada driver";
    }

    // Inner Class untuk Ambulans
    public class Ambulan {
        @SerializedName("nama_driver")
        private String namaDriver;

        @SerializedName("nomor_polisi")
        private String nomorPolisi;
    }
}