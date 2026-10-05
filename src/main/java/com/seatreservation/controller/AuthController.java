package com.seatreservation.controller;

import com.seatreservation.dto.TokenRequest;
import com.seatreservation.dto.TokenResponse;
import com.seatreservation.dto.UserResponse;
import com.seatreservation.security.AuthenticatedUser;
import com.seatreservation.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/token")
    public TokenResponse token(@Valid @RequestBody TokenRequest request) {
        return authService.issueToken(request);
    }

    @GetMapping("/me")
    public UserResponse me(@AuthenticationPrincipal AuthenticatedUser user) {
        return new UserResponse(user.getUserId(), user.getRole());
    }
}