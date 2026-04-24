package com.example.patientapp.model;

public class HospitalModel {
    private String name;
    private String address;
    private double rating;
    private String placeId;

    public HospitalModel(String name, String address, double rating, String placeId) {
        this.name = name;
        this.address = address;
        this.rating = rating;
        this.placeId = placeId;
    }

    public String getName() { return name; }
    public String getAddress() { return address; }
    public double getRating() { return rating; }
    public String getPlaceId() { return placeId; }
}