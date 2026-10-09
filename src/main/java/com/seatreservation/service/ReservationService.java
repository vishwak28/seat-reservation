package com.seatreservation.service;

import com.seatreservation.dto.CancelResponse;
import com.seatreservation.dto.ReservationResponse;
import com.seatreservation.exception.DeclineReason;
import com.seatreservation.exception.ReservationDeclinedException;
import com.seatreservation.model.Reservation;
import com.seatreservation.model.Show;
import com.seatreservation.repository.ReservationRepository;
import com.seatreservation.repository.ShowRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

@Service
public class ReservationService {

    private static final Logger log = LoggerFactory.getLogger(ReservationService.class);

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

        int limit = show.getPerUserLimit();
        if (seats.size() > limit) {
            throw new ReservationDeclinedException(DeclineReason.PER_USER_LIMIT,
                    "A single request cannot exceed the limit of " + limit + " seats per show");
        }

        String requestHash = hash(showId, seats);

        // Lock order for everything below: new reservation row, then the user's
        // counter row, then seats in ascending order. Cancel uses the same order.
        Optional<UUID> inserted = reservationRepository.insertReservationIfAbsent(
                showId, userId, amountPaise, idempotencyKey, requestHash);

        if (inserted.isEmpty()) {
            return replayOrReject(showId, userId, idempotencyKey, requestHash, seats);
        }

        UUID reservationId = inserted.get();

        if (!reservationRepository.tryIncreaseUserHolds(showId, userId, seats.size(), limit)) {
            throw new ReservationDeclinedException(DeclineReason.PER_USER_LIMIT,
                    "Per-user limit of " + limit + " seats reached for this show");
        }

        for (String seatNo : seats) {
            if (!reservationRepository.claimSeat(showId, seatNo, reservationId)) {
                if (!reservationRepository.seatExists(showId, seatNo)) {
                    throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                            "Seat " + seatNo + " does not exist in this show");
                }
                throw new ReservationDeclinedException(DeclineReason.SEAT_TAKEN,
                        "Seat " + seatNo + " is not available");
            }
        }

        return new ReservationResponse(reservationId, showId, userId, seats, amountPaise, "confirmed");
    }

    @Transactional
    public CancelResponse cancel(UUID reservationId, String userId) {
        Optional<UUID> cancelledShow = reservationRepository.markCancelled(reservationId, userId);

        if (cancelledShow.isEmpty()) {
            Reservation existing = reservationRepository.findById(reservationId)
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.NOT_FOUND, "Reservation not found"));
            if (!existing.getUserId().equals(userId)) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not your reservation");
            }
            // Owner, and it was already cancelled: cancel is idempotent.
            return new CancelResponse(existing.getId(), existing.getShowId(), "cancelled", 0);
        }

        UUID showId = cancelledShow.get();
        int seatCount = reservationRepository.countSeats(reservationId);
        reservationRepository.decreaseUserHolds(showId, userId, seatCount);
        int released = reservationRepository.releaseSeats(reservationId);

        if (released != seatCount) {
            throw new IllegalStateException("Released " + released + " seats but expected " + seatCount);
        }

        return new CancelResponse(reservationId, showId, "cancelled", released);
    }

    private ReservationResponse replayOrReject(UUID showId, String userId, String idempotencyKey,
                                               String requestHash, List<String> seats) {
        Reservation existing = reservationRepository.findByUserAndKey(userId, idempotencyKey)
                .orElseThrow(() -> new IllegalStateException("Idempotency record vanished"));

        if (!existing.getRequestHash().equals(requestHash)) {
            log.info("Idempotency key reused with a different request stored_show_id={} "
                            + "requested_show_id={} stored_hash={} request_hash={}",
                    existing.getShowId(), showId,
                    shortHash(existing.getRequestHash()), shortHash(requestHash));
            throw new ReservationDeclinedException(DeclineReason.KEY_REUSED,
                    "Idempotency key was already used with a different request");
        }

        return new ReservationResponse(
                existing.getId(), existing.getShowId(), existing.getUserId(),
                seats, existing.getAmountPaise(), existing.getStatus().toLowerCase(Locale.ROOT),
                true);
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

    private String shortHash(String hash) {
        return hash.substring(0, 8);
    }
}