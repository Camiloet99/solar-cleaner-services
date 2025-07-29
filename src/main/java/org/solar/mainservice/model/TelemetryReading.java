package org.solar.mainservice.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Data
@Document(collection = "readings")
public class TelemetryReading {
    @Id
    private String id;
    private String sessionId;
    private String panelId;
    private LocalDateTime timestamp;
    private double temperature;
    private double humidity;
    private double dustLevel;
    private double powerOutput;
    private double vibration;
    private double microFractureRisk;
    private GeoLocation location;

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class GeoLocation {
        private double lat;
        private double lng;
    }
}


