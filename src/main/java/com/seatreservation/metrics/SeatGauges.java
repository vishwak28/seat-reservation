package com.seatreservation.metrics;

import com.seatreservation.repository.ShowRepository;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import org.springframework.stereotype.Component;

@Component
public class SeatGauges implements MeterBinder {

    private final ShowRepository showRepository;

    public SeatGauges(ShowRepository showRepository) {
        this.showRepository = showRepository;
    }

    @Override
    public void bindTo(MeterRegistry registry) {
        Gauge.builder("seats.available", () -> showRepository.countSeatsWithStatus("AVAILABLE"))
                .description("Seats currently available, across all shows")
                .register(registry);

        Gauge.builder("seats.held", () -> showRepository.countSeatsWithStatus("HELD"))
                .description("Seats currently held, across all shows")
                .register(registry);

        Gauge.builder("seats.confirmed", () -> showRepository.countSeatsWithStatus("CONFIRMED"))
                .description("Seats currently confirmed, across all shows")
                .register(registry);
    }
}