package org.solar.mainservice.ai.dto;

import lombok.Data;

@Data
public class AiParamsDTO {
    private Double brushRpm;
    private Double waterPressure;
    private Double detergentFlowRate;
    private Double robotSpeed;
    private Double passOverlap;
    private Double dwellTime;
}