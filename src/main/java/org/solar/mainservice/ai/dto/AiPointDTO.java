package org.solar.mainservice.ai.dto;

import lombok.Data;
import java.util.Map;

@Data
public class AiPointDTO {
    private String sessionId;
    private String panelId;
    private String timestamp;

    private double temperature;
    private double humidity;
    private double dustIndex;
    private double powerOutput;

    private Double vibration;
    private Double microFractureRisk;
    private Map<String, Double> location;

    private AiParamsDTO params; // opcional
}