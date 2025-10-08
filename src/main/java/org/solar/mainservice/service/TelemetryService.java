package org.solar.mainservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.solar.mainservice.ai.client.AiServiceClient;
import org.solar.mainservice.model.TelemetryReading;
import org.solar.mainservice.repository.TelemetryRepository;
import org.solar.mainservice.telemetry.TelemetryWindowProcessor;
import org.solar.mainservice.websocket.WebSocketNotifier;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class TelemetryService {
    private final TelemetryRepository repo;
    private final TelemetryWindowProcessor windowProcessor;
    private final WebSocketNotifier ws;

    public Mono<Void> processReading(TelemetryReading reading) {
        return repo.save(reading)
                .doOnNext(saved -> log.info("Saved {}", saved))
                .doOnError(error -> log.error("Error saving reading: {}", error.toString()))
                .doOnNext(ws::sendTelemetry)
                .doOnNext(saved -> triggerPrediction(saved.getSessionId()))
                .then();
    }

    private void triggerPrediction(String sessionId) {
        repo.findTop10BySessionIdOrderByTimestampDesc(sessionId)
                .collectList()
                .filter(list -> list.size() == 10)
                .flatMap(last10 -> {
                    String panelId = safePanelId(last10);
                    return windowProcessor.processWindow(sessionId, panelId, last10, /*applyControl*/ true);
                })
                .doOnError(e -> log.warn("Prediction skipped: {}", e.toString()))
                .onErrorResume(e -> Mono.empty())
                .subscribe();
    }

    private String safePanelId(List<TelemetryReading> last10) {
        if (last10.isEmpty()) return null;
        String p = last10.get(0).getPanelId();
        if (p == null && last10.size() > 1) p = last10.get(1).getPanelId();
        return p;
    }
}
