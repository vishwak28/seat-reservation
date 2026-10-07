package com.seatreservation.controller;

import com.seatreservation.dto.CancelResponse;
import com.seatreservation.security.AuthenticatedUser;
import com.seatreservation.service.ReservationService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/reservations")
public class CancelController {

    private final ReservationService service;

    public CancelController(ReservationService service) {
        this.service = service;
    }

    @PostMapping("/{reservationId}/cancel")
    public CancelResponse cancel(@PathVariable UUID reservationId,
                                 @AuthenticationPrincipal AuthenticatedUser user) {
        return service.cancel(reservationId, user.getUserId());
    }
}