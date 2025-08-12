package org.solar.mainservice.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.solar.mainservice.dto.SessionStartRequest;
import org.solar.mainservice.dto.SessionStartResponse;
import org.solar.mainservice.model.Session;
import org.solar.mainservice.service.SessionService;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Slf4j
@RestController
@RequestMapping("/api/sessions")
@RequiredArgsConstructor
@CrossOrigin(origins = {"http://localhost:5173","http://127.0.0.1:5173","http://localhost:3000"})
public class SessionController {
    private final SessionService service;

    @PostMapping("/start")
    public Mono<SessionStartResponse> start(@RequestBody SessionStartRequest req) {
        log.info("starting "+req);
        return service.start(req);
    }

    @PostMapping("/{id}/stop")
    public Mono<Session> stop(@PathVariable String id) { return service.stop(id); }

    @GetMapping("/active")
    public Flux<Session> active() { return service.active(); }
}