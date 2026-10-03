package com.seatreservation.model;

import java.util.UUID;

public class Show {

    private final UUID id;
    private final String name;
    private final long pricePaise;
    private final int perUserLimit;
    private final int totalSeats;

    public Show(UUID id, String name, long pricePaise, int perUserLimit, int totalSeats) {
        this.id = id;
        this.name = name;
        this.pricePaise = pricePaise;
        this.perUserLimit = perUserLimit;
        this.totalSeats = totalSeats;
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public long getPricePaise() {
        return pricePaise;
    }

    public int getPerUserLimit() {
        return perUserLimit;
    }

    public int getTotalSeats() {
        return totalSeats;
    }
}