package com.seatreservation.metrics;

import com.seatreservation.exception.DeclineReason;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Component
public class ReservationMetrics {

    public static final String IDEMPOTENT_REPLAY = "idempotent_replay";

    private final MeterRegistry registry;
    private final Counter confirmed;
    private final Counter cancelled;
    private final ConcurrentMap<String, Counter> declined = new ConcurrentHashMap<>();

    public ReservationMetrics(MeterRegistry registry) {
        this.registry = registry;

        this.confirmed = Counter.builder("reservations.confirmed")
                .description("New reservations confirmed (idempotent replays are not included)")
                .register(registry);

        this.cancelled = Counter.builder("reservations.cancelled")
                .description("Reservations cancelled")
                .register(registry);

        // Create every series now so each one exists at 0 from startup.
        for (DeclineReason reason : DeclineReason.values()) {
            declinedCounter(reason.name().toLowerCase(Locale.ROOT));
        }
        declinedCounter(IDEMPOTENT_REPLAY);
    }

    public void confirmed() {
        confirmed.increment();
    }

    public void cancelled() {
        cancelled.increment();
    }

    public void declined(String reason) {
        declinedCounter(reason).increment();
    }

    private Counter declinedCounter(String reason) {
        return declined.computeIfAbsent(reason, r -> Counter.builder("reservations.declined")
                .description("Reservation requests declined or replayed, by reason")
                .tag("reason", r)
                .register(registry));
    }
}