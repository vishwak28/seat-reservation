package com.seatreservation.service;

import com.seatreservation.dto.CreateShowRequest;
import com.seatreservation.dto.ShowStateResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
class ReservationConcurrencyTest {

    @Autowired
    private ShowService showService;

    @Autowired
    private ReservationService reservationService;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void hotSeatHasExactlyOneWinner() throws Exception {
        UUID showId = createShow(List.of("A12", "A13"));
        int contenders = 100;

        ExecutorService pool = Executors.newFixedThreadPool(contenders);
        CountDownLatch ready = new CountDownLatch(contenders);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger winners = new AtomicInteger();
        AtomicInteger conflicts = new AtomicInteger();
        AtomicInteger others = new AtomicInteger();

        for (int i = 0; i < contenders; i++) {
            final int n = i;
            pool.submit(() -> {
                ready.countDown();
                try {
                    start.await();
                    reservationService.reserve(showId, "user-" + n, List.of("A12"), "key-" + n);
                    winners.incrementAndGet();
                } catch (ResponseStatusException e) {
                    if (e.getStatusCode().value() == 409) {
                        conflicts.incrementAndGet();
                    } else {
                        others.incrementAndGet();
                    }
                } catch (Exception e) {
                    others.incrementAndGet();
                }
            });
        }

        ready.await();
        start.countDown();
        pool.shutdown();
        pool.awaitTermination(60, TimeUnit.SECONDS);

        assertEquals(1, winners.get(), "exactly one winner");
        assertEquals(contenders - 1, conflicts.get(), "everyone else gets a clean 409");
        assertEquals(0, others.get(), "no other outcomes (no 5xx-style failures)");

        Integer reservationRows = jdbc.queryForObject(
                "SELECT count(*) FROM reservations WHERE show_id = ?", Integer.class, showId);
        assertEquals(1, reservationRows, "losers' reservation rows were rolled back");

        ShowStateResponse state = showService.getShow(showId);
        assertEquals(1, state.getConfirmed());
        assertEquals(1, state.getAvailable());
        assertEquals(0, state.getHeld());
        assertEquals(state.getTotalSeats(),
                state.getAvailable() + state.getHeld() + state.getConfirmed());
    }

    private UUID createShow(List<String> seats) {
        CreateShowRequest request = new CreateShowRequest();
        request.setName("race-test-" + UUID.randomUUID());
        request.setSeats(seats);
        request.setPricePaise(25000L);
        return showService.createShow(request).getId();
    }
}