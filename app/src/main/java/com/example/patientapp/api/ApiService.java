package com.example.patientapp.api;

import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import retrofit2.Call;
import retrofit2.http.Field;
import retrofit2.http.FormUrlEncoded;
import retrofit2.http.Multipart;
import retrofit2.http.POST;
import retrofit2.http.Part;

public interface ApiService {

    @FormUrlEncoded
    @POST("pasien/login")
    Call<com.example.patientapp.api.AuthResponse> login(
            @Field("nik") String nik,
            @Field("password") String password
    );

    @Multipart
    @POST("pasien/register")
    Call<com.example.patientapp.api.AuthResponse> register(
            @Part("nik") RequestBody nik,
            @Part("nama") RequestBody nama,
            @Part("tgl_lahir") RequestBody tglLahir,
            @Part("jenis_kelamin") RequestBody jk,
            @Part("alamat") RequestBody alamat,
            @Part("password") RequestBody password,
            @Part MultipartBody.Part fotoKtp // File Gambar
    );
}