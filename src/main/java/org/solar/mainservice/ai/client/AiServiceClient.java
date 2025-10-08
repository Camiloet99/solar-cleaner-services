package org.solar.mainservice.ai.client;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.solar.mainservice.ai.dto.AiPointDTO;
import org.solar.mainservice.ai.dto.AiPredictRequest;
import org.solar.mainservice.ai.dto.AiPredictResponse;
import org.solar.mainservice.ai.mapper.AiPointMapper;
import org.solar.mainservice.model.TelemetryReading;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiServiceClient {

    private final WebClient aiWebClient;
    private final AiPointMapper mapper;

    @Value("${ai.enabled:true}") boolean enabled;

    public Mono<AiPredictResponse> sendToAi(List<TelemetryReading> last10) {
        if (!enabled) return Mono.empty();

        List<AiPointDTO> points = last10.stream()
                .map(mapper::toAiPoint)
                .collect(Collectors.toList());

        AiPredictRequest body = new AiPredictRequest();
        body.setPoints(points);

        return aiWebClient.post()
                .uri("/predict")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(AiPredictResponse.class) // o PredictionResult.class si prefieres
                .timeout(Duration.ofSeconds(2))
                .doOnError(e -> log.warn("AI offline: {}", e.toString()))
                .onErrorResume(e -> Mono.empty()); // ← NO propagar, mantén tu comportamiento
    }
}