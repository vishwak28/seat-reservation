package com.seatreservation.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.UUID;

public class ReservationResponse {

    @JsonProperty("reservation_id")
    private final UUID reservationId;

    @JsonProperty("show_id")
    private final UUID showId;

    @JsonProperty("user_id")
    private final String userId;

    private final List<String> seats;

    @JsonProperty("amount_paise")
    private final long amountPaise;

    private final String status;

    @JsonIgnore
    private final boolean replayed;

    public ReservationResponse(UUID reservationId, UUID showId, String userId,
                               List<String> seats, long amountPaise, String status) {
        this(reservationId, showId, userId, seats, amountPaise, status, false);
    }

    public ReservationResponse(UUID reservationId, UUID showId, String userId,
                               List<String> seats, long amountPaise, String status,
                               boolean replayed) {
        this.reservationId = reservationId;
        this.showId = showId;
        this.userId = userId;
        this.seats = seats;
        this.amountPaise = amountPaise;
        this.status = status;
        this.replayed = replayed;
    }

    public UUID getReservationId() {
        return reservationId;
    }

    public UUID getShowId() {
        return showId;
    }

    public String getUserId() {
        return userId;
    }

    public List<String> getSeats() {
        return seats;
    }

    public long getAmountPaise() {
        return amountPaise;
    }

    public String getStatus() {
        return status;
    }

    public boolean isReplayed() {
        return replayed;
    }
}