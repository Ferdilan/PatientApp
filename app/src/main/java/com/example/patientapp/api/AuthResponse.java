package com.example.patientapp.api;

// Model Penerima Data
public class AuthResponse {
    boolean error;
    String message;
    User data; // Objek user

    public boolean isError() { return error; }
    public String getMessage() { return message; }
    public User getData() { return data; }

    public class User {
        String id, nik, nama;
        public String getId() { return id; }
        public String getNik() { return nik; }
        public String getNama() { return nama; }
    }
}