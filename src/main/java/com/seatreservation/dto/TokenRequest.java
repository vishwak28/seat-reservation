package com.seatreservation.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class TokenRequest {

    @JsonProperty("user_id")
    @NotBlank
    @Size(max = 100)
    private String userId;

    @JsonProperty("admin_secret")
    private String adminSecret;

    public TokenRequest() {
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getAdminSecret() {
        return adminSecret;
    }

    public void setAdminSecret(String adminSecret) {
        this.adminSecret = adminSecret;
    }
}