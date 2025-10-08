package org.solar.mainservice.ai.dto;

import lombok.Data;
import java.util.List;

@Data
public class AiPredictRequest {
    private List<AiPointDTO> points;
}