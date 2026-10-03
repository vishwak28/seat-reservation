package com.seatreservation.model;

public class Seat {

    private final String seatNo;
    private final String status;

    public Seat(String seatNo, String status) {
        this.seatNo = seatNo;
        this.status = status;
    }

    public String getSeatNo() {
        return seatNo;
    }

    public String getStatus() {
        return status;
    }
}