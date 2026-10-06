package com.seatreservation.model;

import java.util.UUID;

public class Reservation {

    private final UUID id;
    private final UUID showId;
    private final String userId;
    private final long amountPaise;
    private final String status;
    private final String requestHash;

    public Reservation(UUID id, UUID showId, String userId, long amountPaise,
                       String status, String requestHash) {
        this.id = id;
        this.showId = showId;
        this.userId = userId;
        this.amountPaise = amountPaise;
        this.status = status;
        this.requestHash = requestHash;
    }

    public UUID getId() {
        return id;
    }

    public UUID getShowId() {
        return showId;
    }

    public String getUserId() {
        return userId;
    }

    public long getAmountPaise() {
        return amountPaise;
    }

    public String getStatus() {
        return status;
    }

    public String getRequestHash() {
        return requestHash;
    }
}