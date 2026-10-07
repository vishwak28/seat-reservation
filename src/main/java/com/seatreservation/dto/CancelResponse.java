package com.seatreservation.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.UUID;

public class CancelResponse {

    @JsonProperty("reservation_id")
    private final UUID reservationId;

    @JsonProperty("show_id")
    private final UUID showId;

    private final String status;

    @JsonProperty("seats_released")
    private final int seatsReleased;

    public CancelResponse(UUID reservationId, UUID showId, String status, int seatsReleased) {
        this.reservationId = reservationId;
        this.showId = showId;
        this.status = status;
        this.seatsReleased = seatsReleased;
    }

    public UUID getReservationId() {
        return reservationId;
    }

    public UUID getShowId() {
        return showId;
    }

    public String getStatus() {
        return status;
    }

    public int getSeatsReleased() {
        return seatsReleased;
    }
}