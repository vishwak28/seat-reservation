package com.seatreservation.service;

import com.seatreservation.dto.CreateShowRequest;
import com.seatreservation.dto.ReservationResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class IdempotencyTest {

    @Autowired
    private ShowService showService;

    @Autowired
    private ReservationService reservationService;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void retryWithSameKeyReturnsOriginalReservation() {
        UUID showId = createShow(List.of("A1", "A2"));
        String key = uniqueKey();

        ReservationResponse first = reservationService.reserve(showId, "alice", List.of("A1"), key);
        ReservationResponse retry = reservationService.reserve(showId, "alice", List.of("A1"), key);

        assertEquals(first.getReservationId(), retry.getReservationId());
        assertEquals(1, countReservations(showId));
        assertEquals(1, showService.getShow(showId).getConfirmed());
    }

    @Test
    void sameKeyWithDifferentSeatsIsRejectedAndMovesNothing() {
        UUID showId = createShow(List.of("A1", "A2"));
        String key = uniqueKey();
        reservationService.reserve(showId, "alice", List.of("A1"), key);

        ResponseStatusException e = assertThrows(ResponseStatusException.class,
                () -> reservationService.reserve(showId, "alice", List.of("A2"), key));

        assertEquals(409, e.getStatusCode().value());
        assertEquals(1, showService.getShow(showId).getAvailable());
        assertEquals(1, countReservations(showId));
    }

    @Test
    void sameKeyFromDifferentUsersIsIndependent() {
        UUID showId = createShow(List.of("A1", "A2"));
        String key = uniqueKey();

        ReservationResponse alice = reservationService.reserve(showId, "alice", List.of("A1"), key);
        ReservationResponse bob = reservationService.reserve(showId, "bob", List.of("A2"), key);

        assertNotEquals(alice.getReservationId(), bob.getReservationId());
        assertEquals(2, showService.getShow(showId).getConfirmed());
    }

    @Test
    void concurrentRetriesWithSameKeyCreateExactlyOneReservation() throws Exception {
        UUID showId = createShow(List.of("A1", "A2"));
        String key = uniqueKey();
        int threads = 50;

        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        Set<UUID> reservationIds = ConcurrentHashMap.newKeySet();
        ConcurrentLinkedQueue<String> errors = new ConcurrentLinkedQueue<>();

        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                ready.countDown();
                try {
                    start.await();
                    ReservationResponse r =
                            reservationService.reserve(showId, "alice", List.of("A1"), key);
                    reservationIds.add(r.getReservationId());
                } catch (Exception e) {
                    errors.add(e.toString());
                }
            });
        }

        ready.await();
        start.countDown();
        pool.shutdown();
        assertTrue(pool.awaitTermination(60, TimeUnit.SECONDS));

        assertEquals(0, errors.size(), "no failures, but got: " + errors);
        assertEquals(1, reservationIds.size(), "every retry sees the same reservation");
        assertEquals(1, countReservations(showId));
        assertEquals(1, showService.getShow(showId).getConfirmed());
    }

    private int countReservations(UUID showId) {
        Integer count = jdbc.queryForObject(
                "SELECT count(*) FROM reservations WHERE show_id = ?", Integer.class, showId);
        return count == null ? 0 : count;
    }

    private UUID createShow(List<String> seats) {
        CreateShowRequest request = new CreateShowRequest();
        request.setName("idem-test-" + UUID.randomUUID());
        request.setSeats(seats);
        request.setPricePaise(25000L);
        return showService.createShow(request).getId();
    }

    private String uniqueKey() {
        return "key-" + UUID.randomUUID();
    }
}