package org.solar.mainservice.service;

import lombok.RequiredArgsConstructor;
import org.solar.mainservice.model.TelemetryReading;
import org.solar.mainservice.repository.TelemetryRepository;
import org.solar.mainservice.websocket.WebSocketNotifier;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class TelemetryService {

    private final TelemetryRepository repository;
    private final AiServiceClient aiService;
    private final WebSocketNotifier notifier;

    public Mono<Void> processReading(TelemetryReading reading) {
        return repository.save(reading)
                .flatMap(saved -> repository.findTop10BySessionIdOrderByTimestampDesc(saved.getSessionId())
                        .collectList()
                        .filter(list -> list.size() == 10)
                        .flatMap(aiService::sendToAi)
                        .flatMap(notifier::notifyFrontend)
                        .then())
                .then();
    }
}
