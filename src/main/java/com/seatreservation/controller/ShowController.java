package com.seatreservation.controller;

import com.seatreservation.dto.CreateShowRequest;
import com.seatreservation.dto.ShowResponse;
import com.seatreservation.dto.ShowStateResponse;
import com.seatreservation.service.ShowService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/shows")
public class ShowController {

    private final ShowService service;
    private static final Logger log = LoggerFactory.getLogger(ShowController.class);

    public ShowController(ShowService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ShowResponse create(@Valid @RequestBody CreateShowRequest request) {
        ShowResponse created = service.createShow(request);
        log.info("Show created show_id={} total_seats={} price_paise={}",
                created.getId(), created.getTotalSeats(), created.getPricePaise());
        return created;
    }

    @GetMapping("/{id}")
    public ShowStateResponse get(@PathVariable UUID id) {
        return service.getShow(id);
    }
}