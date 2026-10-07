package com.seatreservation.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public class ReservationDeclinedException extends ResponseStatusException {

    private final DeclineReason declineReason;

    public ReservationDeclinedException(DeclineReason declineReason, String message) {
        super(HttpStatus.CONFLICT, message);
        this.declineReason = declineReason;
    }

    public DeclineReason getDeclineReason() {
        return declineReason;
    }
}