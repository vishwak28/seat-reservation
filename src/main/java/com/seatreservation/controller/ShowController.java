package com.seatreservation.controller;

import com.seatreservation.dto.CreateShowRequest;
import com.seatreservation.dto.ShowResponse;
import com.seatreservation.dto.ShowStateResponse;
import com.seatreservation.service.ShowService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/shows")
public class ShowController {

    private final ShowService service;

    public ShowController(ShowService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ShowResponse create(@Valid @RequestBody CreateShowRequest request) {
        return service.createShow(request);
    }

    @GetMapping("/{id}")
    public ShowStateResponse get(@PathVariable UUID id) {
        return service.getShow(id);
    }
}