package org.solar.mainservice.ai.dto;

import lombok.Data;
import java.util.List;
import java.util.Map;

@Data
public class AiPredictResponse {
    private String sessionId;
    private String timestamp;

    private double predictedEfficiencyLoss;
    private String recommendedCleaningFrequency; // now | hold_20s | after_2_windows
    private String cleaningRouteAdjustment;

    private List<String> alerts;
    private Map<String, Object> proposedCommands; // acciones 0..1 + route softmax
    private String explain;
}