package com.seatreservation.controller;

import com.seatreservation.dto.ReservationResponse;
import com.seatreservation.dto.ReserveRequest;
import com.seatreservation.security.AuthenticatedUser;
import com.seatreservation.service.ReservationService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@RestController
@RequestMapping("/shows")
public class ReservationController {

    private final ReservationService service;

    public ReservationController(ReservationService service) {
        this.service = service;
    }

    @PostMapping("/{showId}/reserve")
    @ResponseStatus(HttpStatus.CREATED)
    public ReservationResponse reserve(
            @PathVariable UUID showId,
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestHeader(name = "Idempotency-Key", required = false) String headerKey,
            @Valid @RequestBody ReserveRequest request) {

        String key = resolveKey(headerKey, request.getIdempotencyKey());
        return service.reserve(showId, user.getUserId(), request.getSeats(), key);
    }

    private String resolveKey(String headerKey, String bodyKey) {
        boolean hasHeader = headerKey != null && !headerKey.isBlank();
        boolean hasBody = bodyKey != null && !bodyKey.isBlank();

        if (!hasHeader && !hasBody) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Idempotency key is required");
        }
        if (hasHeader && hasBody && !headerKey.trim().equals(bodyKey.trim())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Idempotency key in header and body differ");
        }
        return (hasHeader ? headerKey : bodyKey).trim();
    }
}