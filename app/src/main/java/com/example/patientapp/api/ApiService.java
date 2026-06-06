package com.example.patientapp.api;

import com.example.patientapp.model.HistoryModel;
import com.google.gson.annotations.SerializedName;

import java.util.List;

import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import retrofit2.Call;
import retrofit2.http.Field;
import retrofit2.http.FormUrlEncoded;
import retrofit2.http.GET;
import retrofit2.http.Multipart;
import retrofit2.http.POST;
import retrofit2.http.Part;
import retrofit2.http.Path;

public interface ApiService {

    @FormUrlEncoded
    @POST("pasien/login")
    Call<AuthResponse> login(
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
            @Part MultipartBody.Part fotoKtp
    );

    public class HistoryResponse {
        @SerializedName("error")
        boolean error;
        @SerializedName("data")
        List<HistoryModel> data;
        public List<HistoryModel> getData() { return data; }
    }

    @GET("transaksi/riwayat/{id}")
    Call<HistoryResponse> getRiwayat(@Path("id") String idPasien);

    // Update endpoint ini: Sesuaikan dengan struktur yang mungkin ada di backend
    @GET("ambulans/detail/{id}")
    Call<AmbulansResponse> getAmbulansDetail(@Path("id") String idAmbulans);

    public class AmbulansResponse {
        @SerializedName("error")
        public boolean error;
        @SerializedName("data")
        public AmbulansData data;
        @SerializedName("message")
        public String message;
    }

    public class AmbulansData {
        @SerializedName("nama_driver")
        public String namaDriver;
        @SerializedName("nomor_polisi")
        public String nomorPolisi;
        @SerializedName("no_polisi")
        public String noPolisi;
    }
}