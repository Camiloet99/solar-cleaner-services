package org.solar.mainservice.telemetry;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.solar.mainservice.DecisionOrchestrator;
import org.solar.mainservice.ai.client.AiServiceClient;
import org.solar.mainservice.ai.dto.AiPredictResponse;
import org.solar.mainservice.model.TelemetryReading;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class TelemetryWindowProcessor {

    private final AiServiceClient aiClient;
    private final DecisionOrchestrator orchestrator;

    public Mono<Void> processWindow(String sessionId, String panelId, List<TelemetryReading> last10, boolean applyControl) {
        log.info("[WIN] session={} panel={} size={}", sessionId, panelId, last10.size());

        // Último frame (tu query viene DESC: índice 0 es el más reciente)
        TelemetryReading latest = last10.get(0);

        // 1) prevParams: snapshot de parámetros vigentes
        Map<String, Object> prevParams = buildPrevParams(latest);

        // 2) prevMode: modo actual del robot/panel si viene en la lectura
        String prevMode;
        if (latest.getState() != null) {
            prevMode = latest.getState().getMode();
        } else {
            prevMode = null;
        }

        return aiClient.sendToAi(last10)
                .doOnSubscribe(s -> log.info("[AI] request -> /predict session={} panel={}", sessionId, panelId))
                .doOnNext(pr -> log.info("[AI] response <- {}", pr))
                .switchIfEmpty(Mono.fromRunnable(() ->
                        log.warn("[AI] EMPTY response (disabled/timeout/mapping). session={} panel={}", sessionId, panelId)))
                .flatMap((AiPredictResponse pr) ->
                        orchestrator.handlePrediction(sessionId, panelId, prevMode, prevParams, pr, applyControl))
                .doOnTerminate(() -> log.info("[WIN] completed session={} panel={}", sessionId, panelId))
                .onErrorResume(e -> { log.warn("[WIN] error {}", e.toString()); return Mono.empty(); });
    }

    private Map<String, Object> buildPrevParams(TelemetryReading latest) {
        Map<String, Object> prev = new HashMap<>();
        if (latest.getParams() != null) {
            var prm = latest.getParams();
            putIfNotNull(prev, "brushRpm", prm.getBrushRpm());
            putIfNotNull(prev, "waterPressure", prm.getWaterPressure());
            putIfNotNull(prev, "waterFlow", prm.getMaxWaterPerMin());
            putIfNotNull(prev, "detergentFlowRate", prm.getDetergentFlowRate());
            putIfNotNull(prev, "robotSpeed", prm.getRobotSpeed());
            putIfNotNull(prev, "passOverlap", prm.getPassOverlap());
            putIfNotNull(prev, "dwellTime", prm.getDwellTime());
        }
        return prev;
    }

    private void putIfNotNull(Map<String, Object> m, String k, Object v) {
        if (v != null) m.put(k, v);
    }
}