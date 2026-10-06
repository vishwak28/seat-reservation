package com.seatreservation.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public class ReservationRepository {

    private final JdbcTemplate jdbc;

    public ReservationRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public UUID insertReservation(UUID showId, String userId, long amountPaise,
                                  String idempotencyKey, String requestHash) {
        return jdbc.queryForObject("""
                INSERT INTO reservations
                    (show_id, user_id, amount_paise, status, idempotency_key, request_hash)
                VALUES (?, ?, ?, 'CONFIRMED', ?, ?)
                RETURNING id
                """, UUID.class, showId, userId, amountPaise, idempotencyKey, requestHash);
    }

    /** The atomic decision: returns true only if this call changed the seat from AVAILABLE. */
    public boolean claimSeat(UUID showId, String seatNo, UUID reservationId) {
        int rows = jdbc.update("""
                UPDATE seats
                   SET status = 'CONFIRMED', reservation_id = ?
                 WHERE show_id = ? AND seat_no = ? AND status = 'AVAILABLE'
                """, reservationId, showId, seatNo);
        return rows == 1;
    }

    public boolean seatExists(UUID showId, String seatNo) {
        Integer count = jdbc.queryForObject(
                "SELECT count(*) FROM seats WHERE show_id = ? AND seat_no = ?",
                Integer.class, showId, seatNo);
        return count != null && count > 0;
    }
}