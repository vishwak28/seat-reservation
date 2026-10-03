package com.seatreservation.service;

import com.seatreservation.dto.CreateShowRequest;
import com.seatreservation.dto.SeatView;
import com.seatreservation.dto.ShowResponse;
import com.seatreservation.dto.ShowStateResponse;
import com.seatreservation.model.Seat;
import com.seatreservation.model.Show;
import com.seatreservation.repository.ShowRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;

@Service
public class ShowService {

    private static final int DEFAULT_PER_USER_LIMIT = 4;

    private final ShowRepository repository;

    public ShowService(ShowRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public ShowResponse createShow(CreateShowRequest request) {
        List<String> seatNos = request.getSeats().stream().map(String::trim).toList();

        if (new HashSet<>(seatNos).size() != seatNos.size()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Duplicate seat numbers");
        }

        int limit = request.getPerUserLimit() != null
                ? request.getPerUserLimit()
                : DEFAULT_PER_USER_LIMIT;

        UUID showId = repository.insertShow(
                request.getName(), request.getPricePaise(), limit, seatNos.size());
        repository.insertSeats(showId, seatNos);

        List<SeatView> seatViews = seatNos.stream()
                .map(seatNo -> new SeatView(seatNo, "available"))
                .toList();

        return new ShowResponse(
                showId, request.getName(), request.getPricePaise(),
                limit, seatNos.size(), seatViews);
    }

    @Transactional(readOnly = true)
    public ShowStateResponse getShow(UUID id) {
        Show show = repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Show not found"));

        List<Seat> seats = repository.findSeats(id);

        int available = 0;
        int held = 0;
        int confirmed = 0;
        List<SeatView> views = new ArrayList<>(seats.size());

        for (Seat seat : seats) {
            String status = seat.getStatus();
            if ("AVAILABLE".equals(status)) {
                available++;
            } else if ("HELD".equals(status)) {
                held++;
            } else if ("CONFIRMED".equals(status)) {
                confirmed++;
            } else {
                throw new IllegalStateException("Unknown seat status: " + status);
            }
            views.add(new SeatView(seat.getSeatNo(), status.toLowerCase(Locale.ROOT)));
        }

        return new ShowStateResponse(
                show.getId(), show.getName(), show.getPricePaise(), show.getPerUserLimit(),
                show.getTotalSeats(), available, held, confirmed, views);
    }
}