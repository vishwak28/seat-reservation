package com.seatreservation.metrics;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ReservationMetricsTest {

    @Test
    void countsConfirmedCancelledAndDeclinesByReason() {
        MeterRegistry registry = new SimpleMeterRegistry();
        ReservationMetrics metrics = new ReservationMetrics(registry);

        metrics.confirmed();
        metrics.confirmed();
        metrics.cancelled();
        metrics.declined("seat_taken");
        metrics.declined("seat_taken");
        metrics.declined(ReservationMetrics.IDEMPOTENT_REPLAY);

        assertEquals(2.0, registry.get("reservations.confirmed").counter().count());
        assertEquals(1.0, registry.get("reservations.cancelled").counter().count());
        assertEquals(2.0, registry.get("reservations.declined").tag("reason", "seat_taken").counter().count());
        assertEquals(1.0, registry.get("reservations.declined").tag("reason", "idempotent_replay").counter().count());
        assertEquals(0.0, registry.get("reservations.declined").tag("reason", "per_user_limit").counter().count());
    }

    @Test
    void everyDeclineSeriesExistsFromStartup() {
        MeterRegistry registry = new SimpleMeterRegistry();
        new ReservationMetrics(registry);

        assertEquals(4, registry.find("reservations.declined").counters().size());
    }
}