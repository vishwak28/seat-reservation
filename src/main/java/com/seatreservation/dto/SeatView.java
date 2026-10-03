package com.seatreservation.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public class SeatView {

    @JsonProperty("seat_no")
    private final String seatNo;

    private final String status;

    public SeatView(String seatNo, String status) {
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