package org.solar.mainservice.model;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class PredictionResult {
    private String sessionId;
    private List<String> readingIds;
    private LocalDateTime timestamp;
    private String recommendedCleaningFrequency;
    private double predictedEfficiencyLoss;
    private String cleaningRouteAdjustment;
    private List<String> alerts;
}