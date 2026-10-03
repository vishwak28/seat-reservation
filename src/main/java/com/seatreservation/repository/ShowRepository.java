package com.seatreservation.repository;

import com.seatreservation.model.Seat;
import com.seatreservation.model.Show;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.util.Optional;

import java.util.List;
import java.util.UUID;

@Repository
public class ShowRepository {

    private final JdbcTemplate jdbc;

    public ShowRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public UUID insertShow(String name, long pricePaise, int perUserLimit, int totalSeats) {
        return jdbc.queryForObject("""
                INSERT INTO shows (name, price_paise, per_user_limit, total_seats)
                VALUES (?, ?, ?, ?)
                RETURNING id
                """, UUID.class, name, pricePaise, perUserLimit, totalSeats);
    }

    public void insertSeats(UUID showId, List<String> seatNos) {
        jdbc.batchUpdate(
                "INSERT INTO seats (show_id, seat_no) VALUES (?, ?)",
                seatNos,
                1000,
                (ps, seatNo) -> {
                    ps.setObject(1, showId);
                    ps.setString(2, seatNo);
                });
    }

    public Optional<Show> findById(UUID id) {
        List<Show> rows = jdbc.query("""
                SELECT id, name, price_paise, per_user_limit, total_seats
                FROM shows
                WHERE id = ?
                """,
                (rs, rowNum) -> new Show(
                        rs.getObject("id", UUID.class),
                        rs.getString("name"),
                        rs.getLong("price_paise"),
                        rs.getInt("per_user_limit"),
                        rs.getInt("total_seats")),
                id);
        return rows.stream().findFirst();
    }

    public List<Seat> findSeats(UUID showId) {
        return jdbc.query("""
                SELECT seat_no, status
                FROM seats
                WHERE show_id = ?
                ORDER BY seat_no
                """,
                (rs, rowNum) -> new Seat(rs.getString("seat_no"), rs.getString("status")),
                showId);
    }
}