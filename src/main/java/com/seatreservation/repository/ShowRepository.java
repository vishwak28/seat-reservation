package com.seatreservation.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

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
}