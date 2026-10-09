package com.seatreservation.controller;

import com.seatreservation.dto.TokenRequest;
import com.seatreservation.dto.TokenResponse;
import com.seatreservation.dto.UserResponse;
import com.seatreservation.security.AuthenticatedUser;
import com.seatreservation.service.AuthService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthService authService;
    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/token")
    public TokenResponse token(@Valid @RequestBody TokenRequest request) {
        TokenResponse response = authService.issueToken(request);
        log.info("Token issued for user_id={} role={}", response.getUserId(), response.getRole());
        return response;
    }

    @GetMapping("/me")
    public UserResponse me(@AuthenticationPrincipal AuthenticatedUser user) {
        return new UserResponse(user.getUserId(), user.getRole());
    }
}