package com.karhoo.demo.model;

import com.google.gson.annotations.SerializedName;

public class PassengerDetails {
    public String first_name;
    public String last_name;
    public String email;
    public String phone_number;
    public String locale;

    public PassengerDetails(String first_name, String last_name, String email, String phone_number, String locale) {
        this.first_name = first_name;
        this.last_name = last_name;
        this.email = email;
        this.phone_number = phone_number;
        this.locale = locale;
    }
}
