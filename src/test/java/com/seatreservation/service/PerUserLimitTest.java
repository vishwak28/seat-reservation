package com.seatreservation.service;

import com.seatreservation.dto.CreateShowRequest;
import com.seatreservation.exception.DeclineReason;
import com.seatreservation.exception.ReservationDeclinedException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class PerUserLimitTest {

    @Autowired
    private ShowService showService;

    @Autowired
    private ReservationService reservationService;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void tenParallelSingleSeatRequestsEndWithExactlyFourHeld() throws Exception {
        List<String> seats = seatNames(10);
        UUID showId = createShow(seats, 4);
        List<List<String>> requests = seats.stream().map(s -> List.of(s)).toList();

        Outcomes outcomes = fire(showId, "alice", requests);

        assertEquals(0, outcomes.errors.size(), "unexpected errors: " + outcomes.errors);
        assertEquals(4, outcomes.successes.get());
        assertEquals(6, outcomes.limitDeclines.get());
        assertEquals(0, outcomes.otherDeclines.get());
        assertEquals(4, showService.getShow(showId).getConfirmed());
        assertEquals(4, holds(showId, "alice"));
    }

    @Test
    void fiveParallelTwoSeatRequestsAllowExactlyTwo() throws Exception {
        List<String> seats = seatNames(10);
        UUID showId = createShow(seats, 4);
        List<List<String>> requests = IntStream.range(0, 5)
                .mapToObj(i -> List.of(seats.get(2 * i), seats.get(2 * i + 1)))
                .toList();

        Outcomes outcomes = fire(showId, "alice", requests);

        assertEquals(0, outcomes.errors.size(), "unexpected errors: " + outcomes.errors);
        assertEquals(2, outcomes.successes.get());
        assertEquals(3, outcomes.limitDeclines.get());
        assertEquals(4, showService.getShow(showId).getConfirmed());
        assertEquals(4, holds(showId, "alice"));
    }

    @Test
    void singleRequestOverTheLimitIsDeclinedAndMovesNothing() {
        List<String> seats = seatNames(6);
        UUID showId = createShow(seats, 4);

        ReservationDeclinedException e = assertThrows(ReservationDeclinedException.class,
                () -> reservationService.reserve(showId, "alice", seats.subList(0, 5), "key-" + UUID.randomUUID()));

        assertEquals(DeclineReason.PER_USER_LIMIT, e.getDeclineReason());
        assertEquals(6, showService.getShow(showId).getAvailable());
        assertEquals(0, holds(showId, "alice"));
        assertEquals(0, countReservations(showId));
    }

    private Outcomes fire(UUID showId, String userId, List<List<String>> seatRequests) throws Exception {
        int n = seatRequests.size();
        ExecutorService pool = Executors.newFixedThreadPool(n);
        CountDownLatch ready = new CountDownLatch(n);
        CountDownLatch start = new CountDownLatch(1);
        Outcomes outcomes = new Outcomes();

        for (List<String> seats : seatRequests) {
            pool.submit(() -> {
                ready.countDown();
                try {
                    start.await();
                    reservationService.reserve(showId, userId, seats, "key-" + UUID.randomUUID());
                    outcomes.successes.incrementAndGet();
                } catch (ReservationDeclinedException e) {
                    if (e.getDeclineReason() == DeclineReason.PER_USER_LIMIT) {
                        outcomes.limitDeclines.incrementAndGet();
                    } else {
                        outcomes.otherDeclines.incrementAndGet();
                    }
                } catch (Exception e) {
                    outcomes.errors.add(e.toString());
                }
            });
        }

        ready.await();
        start.countDown();
        pool.shutdown();
        assertTrue(pool.awaitTermination(60, TimeUnit.SECONDS));
        return outcomes;
    }

    private int holds(UUID showId, String userId) {
        List<Integer> rows = jdbc.queryForList(
                "SELECT seat_count FROM user_show_holds WHERE show_id = ? AND user_id = ?",
                Integer.class, showId, userId);
        return rows.isEmpty() ? 0 : rows.get(0);
    }

    private int countReservations(UUID showId) {
        Integer count = jdbc.queryForObject(
                "SELECT count(*) FROM reservations WHERE show_id = ?", Integer.class, showId);
        return count == null ? 0 : count;
    }

    private List<String> seatNames(int count) {
        return IntStream.rangeClosed(1, count).mapToObj(i -> "A" + i).toList();
    }

    private UUID createShow(List<String> seats, int perUserLimit) {
        CreateShowRequest request = new CreateShowRequest();
        request.setName("limit-test-" + UUID.randomUUID());
        request.setSeats(seats);
        request.setPricePaise(25000L);
        request.setPerUserLimit(perUserLimit);
        return showService.createShow(request).getId();
    }

    private static class Outcomes {
        final AtomicInteger successes = new AtomicInteger();
        final AtomicInteger limitDeclines = new AtomicInteger();
        final AtomicInteger otherDeclines = new AtomicInteger();
        final ConcurrentLinkedQueue<String> errors = new ConcurrentLinkedQueue<>();
    }
}