package org.solar.mainservice.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.solar.mainservice.model.TelemetryReading;
import org.solar.mainservice.service.TelemetryService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

@Slf4j
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class TelemetryController {
    private final TelemetryService telemetryService;

    @PostMapping("/telemetry")
    @ResponseStatus(HttpStatus.ACCEPTED) // 202
    public Mono<Void> receive(@RequestBody TelemetryReading reading) {
        return telemetryService.processReading(reading)
                .onErrorResume(e -> {
                    log.error("Error saving telemetry", e);
                    return Mono.empty();
                });
    }
}

