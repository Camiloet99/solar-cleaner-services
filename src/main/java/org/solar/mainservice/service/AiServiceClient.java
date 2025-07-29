package org.solar.mainservice.service;

import org.solar.mainservice.model.PredictionResult;
import org.solar.mainservice.model.TelemetryReading;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.List;

@Component
public class AiServiceClient {

    private final WebClient webClient = WebClient.create("http://localhost:8000"); // ajustar

    public Mono<PredictionResult> sendToAi(List<TelemetryReading> readings) {
        return webClient.post()
                .uri("/predict")
                .bodyValue(readings)
                .retrieve()
                .bodyToMono(PredictionResult.class);
    }
}
