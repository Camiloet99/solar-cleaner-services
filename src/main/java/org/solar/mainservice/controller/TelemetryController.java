package org.solar.mainservice.controller;

import lombok.RequiredArgsConstructor;
import org.solar.mainservice.model.TelemetryReading;
import org.solar.mainservice.service.TelemetryService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/telemetry")
@RequiredArgsConstructor
public class TelemetryController {

    private final TelemetryService telemetryService;

    @PostMapping
    public Mono<ResponseEntity<String>> receiveTelemetry(@RequestBody TelemetryReading reading) {
        return telemetryService.processReading(reading)
                .thenReturn(ResponseEntity.ok("Received"));
    }
}
