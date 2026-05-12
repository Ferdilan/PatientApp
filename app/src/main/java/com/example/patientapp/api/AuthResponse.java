package com.example.patientapp.api;

import com.google.gson.annotations.SerializedName;

// Model Penerima Data
public class AuthResponse {
    @SerializedName("error")
    boolean error;
    @SerializedName("message")
    String message;
    @SerializedName("data")
    User data;

    public boolean isError() { return error; }
    public String getMessage() { return message; }
    public User getData() { return data; }

    public static class User {
        @SerializedName("id")
        String id;
        @SerializedName("nik")
        String nik;
        @SerializedName("nama")
        String nama;
        @SerializedName("alamat")
        String alamat;
        @SerializedName("tgl_lahir")
        String tgl;
        @SerializedName("jenis_kelamin")
        String jk;
        @SerializedName("foto_ktp")
        String foto;

        public String getId() { return id; }
        public String getNik() { return nik; }
        public String getNama() { return nama; }
        public String getAlamat() { return alamat; }
        public String getTgl() { return tgl; }
        public String getJk() { return jk; }
        public String getFoto() { return foto; }
    }
}
