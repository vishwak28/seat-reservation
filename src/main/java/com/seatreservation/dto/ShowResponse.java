package com.seatreservation.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.UUID;

public class ShowResponse {

    private final UUID id;
    private final String name;

    @JsonProperty("price_paise")
    private final long pricePaise;

    @JsonProperty("per_user_limit")
    private final int perUserLimit;

    @JsonProperty("total_seats")
    private final int totalSeats;

    private final List<SeatView> seats;

    public ShowResponse(UUID id, String name, long pricePaise, int perUserLimit,
                        int totalSeats, List<SeatView> seats) {
        this.id = id;
        this.name = name;
        this.pricePaise = pricePaise;
        this.perUserLimit = perUserLimit;
        this.totalSeats = totalSeats;
        this.seats = seats;
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

    public List<SeatView> getSeats() {
        return seats;
    }
}