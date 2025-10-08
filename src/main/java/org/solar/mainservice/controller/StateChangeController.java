package org.solar.mainservice.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.solar.mainservice.dto.StateChangeEventDTO;
import org.solar.mainservice.service.SimulatorRelay;
import org.solar.mainservice.websocket.WebSocketNotifier;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.Set;

@Slf4j
@RestController
@RequestMapping("/api/telemetry/state-change")
@RequiredArgsConstructor
public class StateChangeController {

    private final WebSocketNotifier ws;
    private final SimulatorRelay relay;

    @PostMapping
    public Mono<ResponseEntity<?>> ingest(@RequestBody StateChangeEventDTO evt) {
        if (evt.getType() == null || evt.getSessionId() == null || evt.getPanelId() == null || evt.getVersion() == null) {
            return Mono.just(ResponseEntity.unprocessableEntity()
                    .body(Map.of("ok", false, "error", "type, sessionId, panelId, version are required")));
        }
        if (!Set.of("state_change", "param_change", "param_change_bulk").contains(evt.getType())) {
            return Mono.just(ResponseEntity.unprocessableEntity()
                    .body(Map.of("ok", false, "error", "invalid type")));
        }

        // Notificar a los clientes (dashboard)
        ws.sendRuntimeEvent(evt.getType(), evt, evt.getSessionId(), evt.getPanelId());

        // Reenviar al simulador
        return relay.relay(evt)
                .doOnNext(r -> log.info("Relayed to simulator: {}", r))
                .map(r -> ResponseEntity.ok(Map.of("ok", true, "relay", r)));
    }
}

