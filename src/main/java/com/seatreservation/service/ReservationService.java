package com.seatreservation.service;

import com.seatreservation.dto.ReservationResponse;
import com.seatreservation.model.Show;
import com.seatreservation.repository.ReservationRepository;
import com.seatreservation.repository.ShowRepository;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

@Service
public class ReservationService {

    private final ShowRepository showRepository;
    private final ReservationRepository reservationRepository;

    public ReservationService(ShowRepository showRepository,
                              ReservationRepository reservationRepository) {
        this.showRepository = showRepository;
        this.reservationRepository = reservationRepository;
    }

    @Transactional
    public ReservationResponse reserve(UUID showId, String userId,
                                       List<String> requestedSeats, String idempotencyKey) {

        List<String> seats = normalise(requestedSeats);

        Show show = showRepository.findById(showId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Show not found"));

        long amountPaise = Math.multiplyExact(show.getPricePaise(), (long) seats.size());
        String requestHash = hash(showId, seats);

        UUID reservationId;
        try {
            reservationId = reservationRepository.insertReservation(
                    showId, userId, amountPaise, idempotencyKey, requestHash);
        } catch (DuplicateKeyException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Idempotency key already used");
        }

        for (String seatNo : seats) {
            if (!reservationRepository.claimSeat(showId, seatNo, reservationId)) {
                if (!reservationRepository.seatExists(showId, seatNo)) {
                    throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                            "Seat " + seatNo + " does not exist in this show");
                }
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Seat " + seatNo + " is not available");
            }
        }

        return new ReservationResponse(reservationId, showId, userId, seats, amountPaise, "confirmed");
    }

    private List<String> normalise(List<String> requestedSeats) {
        List<String> seats = new ArrayList<>(requestedSeats.size());
        for (String seat : requestedSeats) {
            seats.add(seat.trim());
        }
        if (new HashSet<>(seats).size() != seats.size()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Duplicate seats in request");
        }
        Collections.sort(seats);
        return seats;
    }

    private String hash(UUID showId, List<String> sortedSeats) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String canonical = showId + "\n" + String.join("\n", sortedSeats);
            return HexFormat.of().formatHex(digest.digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}