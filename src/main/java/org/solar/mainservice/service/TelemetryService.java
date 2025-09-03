package org.solar.mainservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.solar.mainservice.model.TelemetryReading;
import org.solar.mainservice.repository.TelemetryRepository;
import org.solar.mainservice.websocket.WebSocketNotifier;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Slf4j
@Service
@RequiredArgsConstructor
public class TelemetryService {
    private final TelemetryRepository repo;
    private final AiServiceClient ai;
    private final WebSocketNotifier ws;

    public Mono<Void> processReading(TelemetryReading reading) {
        return repo.save(reading)
                .doOnNext(saved -> log.info("Saved {}", saved))
                .doOnNext(ws::sendTelemetry)
                .doOnNext(saved -> triggerPrediction(saved.getSessionId()))
                .then();
    }

    private void triggerPrediction(String sessionId) {
        repo.findTop10BySessionIdOrderByTimestampDesc(sessionId)
                .collectList()
                .filter(list -> list.size() == 10)
                .flatMap(ai::sendToAi)
                .doOnNext(ws::sendPrediction)
                .doOnError(e -> log.warn("Prediction skipped: {}", e.toString()))
                .onErrorResume(e -> Mono.empty())
                .subscribe();
    }
}

