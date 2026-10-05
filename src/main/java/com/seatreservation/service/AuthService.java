package com.seatreservation.service;

import com.seatreservation.dto.TokenRequest;
import com.seatreservation.dto.TokenResponse;
import com.seatreservation.security.AuthenticatedUser;
import com.seatreservation.security.JwtService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Service
public class AuthService {

    private final JwtService jwtService;
    private final byte[] adminSecret;

    public AuthService(JwtService jwtService,
                       @Value("${app.security.admin-secret}") String adminSecret) {
        if (adminSecret == null || adminSecret.isBlank()) {
            throw new IllegalStateException("app.security.admin-secret must not be blank");
        }
        this.jwtService = jwtService;
        this.adminSecret = adminSecret.getBytes(StandardCharsets.UTF_8);
    }

    public TokenResponse issueToken(TokenRequest request) {
        String userId = request.getUserId().trim();
        String role = AuthenticatedUser.ROLE_USER;

        if (request.getAdminSecret() != null) {
            byte[] supplied = request.getAdminSecret().getBytes(StandardCharsets.UTF_8);
            if (!MessageDigest.isEqual(adminSecret, supplied)) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Invalid admin secret");
            }
            role = AuthenticatedUser.ROLE_ADMIN;
        }

        String token = jwtService.createToken(userId, role);
        return new TokenResponse(token, userId, role, jwtService.getTtlSeconds());
    }
}