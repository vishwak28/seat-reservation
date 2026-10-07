package com.seatreservation.repository;

import com.seatreservation.model.Reservation;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class ReservationRepository {

    private final JdbcTemplate jdbc;

    public ReservationRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Returns the new id, or empty if this user already used this idempotency key.
     */
    public Optional<UUID> insertReservationIfAbsent(UUID showId, String userId, long amountPaise,
                                                    String idempotencyKey, String requestHash) {
        List<UUID> ids = jdbc.query("""
                        INSERT INTO reservations
                            (show_id, user_id, amount_paise, status, idempotency_key, request_hash)
                        VALUES (?, ?, ?, 'CONFIRMED', ?, ?)
                        ON CONFLICT (user_id, idempotency_key) DO NOTHING
                        RETURNING id
                        """,
                (rs, rowNum) -> rs.getObject("id", UUID.class),
                showId, userId, amountPaise, idempotencyKey, requestHash);
        return ids.stream().findFirst();
    }

    public Optional<Reservation> findByUserAndKey(String userId, String idempotencyKey) {
        List<Reservation> rows = jdbc.query("""
                        SELECT id, show_id, user_id, amount_paise, status, request_hash
                        FROM reservations
                        WHERE user_id = ? AND idempotency_key = ?
                        """,
                (rs, rowNum) -> new Reservation(
                        rs.getObject("id", UUID.class),
                        rs.getObject("show_id", UUID.class),
                        rs.getString("user_id"),
                        rs.getLong("amount_paise"),
                        rs.getString("status"),
                        rs.getString("request_hash")),
                userId, idempotencyKey);
        return rows.stream().findFirst();
    }

    /**
     * The atomic decision: returns true only if this call changed the seat from AVAILABLE.
     */
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

    /**
     * Atomically adds seatCount to the user's total for this show,
     * but only if the new total stays within the limit.
     */
    public boolean tryIncreaseUserHolds(UUID showId, String userId, int seatCount, int limit) {
        List<Integer> rows = jdbc.query("""
                        INSERT INTO user_show_holds (show_id, user_id, seat_count)
                        VALUES (?, ?, ?)
                        ON CONFLICT (show_id, user_id)
                        DO UPDATE SET seat_count = user_show_holds.seat_count + EXCLUDED.seat_count
                        WHERE user_show_holds.seat_count + EXCLUDED.seat_count <= ?
                        RETURNING seat_count
                        """,
                (rs, rowNum) -> rs.getInt("seat_count"),
                showId, userId, seatCount, limit);
        return !rows.isEmpty();
    }
}