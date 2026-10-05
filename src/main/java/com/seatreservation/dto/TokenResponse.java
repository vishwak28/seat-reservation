package com.seatreservation.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public class TokenResponse {

    private final String token;

    @JsonProperty("user_id")
    private final String userId;

    private final String role;

    @JsonProperty("expires_in_seconds")
    private final long expiresInSeconds;

    public TokenResponse(String token, String userId, String role, long expiresInSeconds) {
        this.token = token;
        this.userId = userId;
        this.role = role;
        this.expiresInSeconds = expiresInSeconds;
    }

    public String getToken() {
        return token;
    }

    public String getUserId() {
        return userId;
    }

    public String getRole() {
        return role;
    }

    public long getExpiresInSeconds() {
        return expiresInSeconds;
    }
}