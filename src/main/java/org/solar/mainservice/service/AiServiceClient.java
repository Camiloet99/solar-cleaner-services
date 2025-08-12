package org.solar.mainservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.solar.mainservice.model.PredictionResult;
import org.solar.mainservice.model.TelemetryReading;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiServiceClient {
    private final WebClient aiWebClient;
    @Value("${ai.enabled:true}") boolean enabled;

    public Mono<PredictionResult> sendToAi(List<TelemetryReading> last10) {
        if (!enabled) return Mono.empty();

        var body = Map.of("points", last10); // ajusta al contrato real
        return aiWebClient.post().uri("/predict").bodyValue(body)
                .retrieve()
                .bodyToMono(PredictionResult.class)
                .timeout(Duration.ofSeconds(2))
                .doOnError(e -> log.warn("AI offline: {}", e.toString()))
                .onErrorResume(e -> Mono.empty()); // ← clave: NO propagar
    }
}