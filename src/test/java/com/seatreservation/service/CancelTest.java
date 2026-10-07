package com.seatreservation.service;

import com.seatreservation.dto.CancelResponse;
import com.seatreservation.dto.CreateShowRequest;
import com.seatreservation.dto.ReservationResponse;
import com.seatreservation.dto.ShowStateResponse;
import com.seatreservation.exception.DeclineReason;
import com.seatreservation.exception.ReservationDeclinedException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class CancelTest {

    @Autowired
    private ShowService showService;

    @Autowired
    private ReservationService reservationService;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void cancelReleasesSeatsAndCounterAndSeatsAreRebookable() {
        UUID showId = createShow(List.of("A1", "A2", "A3"));
        ReservationResponse r = reservationService.reserve(showId, "alice", List.of("A1", "A2"), uniqueKey());

        CancelResponse cancelled = reservationService.cancel(r.getReservationId(), "alice");

        assertEquals(2, cancelled.getSeatsReleased());
        assertEquals(3, showService.getShow(showId).getAvailable());
        assertEquals(0, holds(showId, "alice"));

        reservationService.reserve(showId, "bob", List.of("A1", "A2"), uniqueKey());
        assertEquals(2, showService.getShow(showId).getConfirmed());
    }

    @Test
    void nonOwnerCannotCancel() {
        UUID showId = createShow(List.of("A1"));
        ReservationResponse r = reservationService.reserve(showId, "alice", List.of("A1"), uniqueKey());

        ResponseStatusException e = assertThrows(ResponseStatusException.class,
                () -> reservationService.cancel(r.getReservationId(), "bob"));

        assertEquals(403, e.getStatusCode().value());
        assertEquals(1, showService.getShow(showId).getConfirmed());
        assertEquals(1, holds(showId, "alice"));
    }

    @Test
    void cancelNeverTakesBackASeatAnotherUserNowOwns() {
        UUID showId = createShow(List.of("A1"));
        ReservationResponse alices = reservationService.reserve(showId, "alice", List.of("A1"), uniqueKey());
        reservationService.cancel(alices.getReservationId(), "alice");
        ReservationResponse bobs = reservationService.reserve(showId, "bob", List.of("A1"), uniqueKey());

        CancelResponse again = reservationService.cancel(alices.getReservationId(), "alice");

        assertEquals(0, again.getSeatsReleased());
        assertEquals(1, showService.getShow(showId).getConfirmed());
        UUID owner = jdbc.queryForObject(
                "SELECT reservation_id FROM seats WHERE show_id = ? AND seat_no = 'A1'",
                UUID.class, showId);
        assertEquals(bobs.getReservationId(), owner);
        assertEquals(1, holds(showId, "bob"));
    }

    @Test
    void concurrentCancelsOfOneReservationReleaseExactlyOnce() throws Exception {
        UUID showId = createShow(List.of("A1", "A2"));
        ReservationResponse r = reservationService.reserve(showId, "alice", List.of("A1", "A2"), uniqueKey());
        int threads = 20;

        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger totalReleased = new AtomicInteger();
        ConcurrentLinkedQueue<String> errors = new ConcurrentLinkedQueue<>();

        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                ready.countDown();
                try {
                    start.await();
                    totalReleased.addAndGet(
                            reservationService.cancel(r.getReservationId(), "alice").getSeatsReleased());
                } catch (Exception e) {
                    errors.add(e.toString());
                }
            });
        }

        ready.await();
        start.countDown();
        pool.shutdown();
        assertTrue(pool.awaitTermination(60, TimeUnit.SECONDS));

        assertEquals(0, errors.size(), "unexpected errors: " + errors);
        assertEquals(2, totalReleased.get(), "the seats are released exactly once in total");
        assertEquals(2, showService.getShow(showId).getAvailable());
        assertEquals(0, holds(showId, "alice"));
    }

    @Test
    void cancelRacingWithReReservationNeverDeadlocks() throws Exception {
        for (int i = 0; i < 30; i++) {
            UUID showId = createShow(List.of("A1"));
            ReservationResponse first = reservationService.reserve(showId, "alice", List.of("A1"), uniqueKey());

            ExecutorService pool = Executors.newFixedThreadPool(2);
            CountDownLatch start = new CountDownLatch(1);

            Callable<Object> cancelTask = () -> {
                start.await();
                return reservationService.cancel(first.getReservationId(), "alice");
            };
            Callable<Object> reserveTask = () -> {
                start.await();
                try {
                    return reservationService.reserve(showId, "alice", List.of("A1"), uniqueKey());
                } catch (ReservationDeclinedException e) {
                    if (e.getDeclineReason() != DeclineReason.SEAT_TAKEN) {
                        throw e;
                    }
                    return null;
                }
            };

            Future<Object> cancel = pool.submit(cancelTask);
            Future<Object> reserve = pool.submit(reserveTask);
            start.countDown();
            cancel.get(30, TimeUnit.SECONDS);
            reserve.get(30, TimeUnit.SECONDS);
            pool.shutdown();

            ShowStateResponse state = showService.getShow(showId);
            assertEquals(1, state.getConfirmed() + state.getAvailable());
            assertEquals(state.getConfirmed(), holds(showId, "alice"),
                    "the counter must match the seats actually held");
        }
    }

    private int holds(UUID showId, String userId) {
        List<Integer> rows = jdbc.queryForList(
                "SELECT seat_count FROM user_show_holds WHERE show_id = ? AND user_id = ?",
                Integer.class, showId, userId);
        return rows.isEmpty() ? 0 : rows.get(0);
    }

    private String uniqueKey() {
        return "key-" + UUID.randomUUID();
    }

    private UUID createShow(List<String> seats) {
        CreateShowRequest request = new CreateShowRequest();
        request.setName("cancel-test-" + UUID.randomUUID());
        request.setSeats(seats);
        request.setPricePaise(25000L);
        return showService.createShow(request).getId();
    }
}