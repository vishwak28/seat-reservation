package com.seatreservation.controller;

import com.seatreservation.dto.CancelResponse;
import com.seatreservation.metrics.ReservationMetrics;
import com.seatreservation.security.AuthenticatedUser;
import com.seatreservation.service.ReservationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/reservations")
public class CancelController {

    private static final Logger log = LoggerFactory.getLogger(CancelController.class);

    private final ReservationService service;
    private final ReservationMetrics metrics;

    public CancelController(ReservationService service, ReservationMetrics metrics) {
        this.service = service;
        this.metrics = metrics;
    }

    @PostMapping("/{reservationId}/cancel")
    public CancelResponse cancel(@PathVariable UUID reservationId,
                                 @AuthenticationPrincipal AuthenticatedUser user) {
        CancelResponse response = service.cancel(reservationId, user.getUserId());
        if (response.getSeatsReleased() > 0) {
            metrics.cancelled();
            log.info("Reservation cancelled reservation_id={} show_id={} seats_released={}",
                    reservationId, response.getShowId(), response.getSeatsReleased());
        } else {
            log.info("Cancel was a no-op, reservation already cancelled reservation_id={}", reservationId);
        }
        return response;
    }
}