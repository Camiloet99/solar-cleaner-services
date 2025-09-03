package org.solar.mainservice.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonFormat;

@Data @NoArgsConstructor @AllArgsConstructor
@Document("telemetry_readings")
public class TelemetryReading {
    @Id
    private String id;

    private String sessionId;
    private String panelId;

    private State state;
    private Params params;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime timestamp;

    private Double temperature;
    private Double humidity;

    @JsonAlias({"dust","dust_level","dustLevel"})
    private Double dustLevel;

    @JsonAlias({"power","power_output","powerOutput"})
    private Double powerOutput;

    private Double vibration;

    @JsonAlias({"micro_fracture_risk","microFractureRisk"})
    private Double microFractureRisk;

    private GeoLocation location;

    @Data @NoArgsConstructor @AllArgsConstructor
    public static class GeoLocation {
        private Double lat;
        private Double lng;
    }

    @Data @NoArgsConstructor @AllArgsConstructor
    public static class State {
        private String mode;
        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", timezone = "UTC")
        private LocalDateTime lastChangeTs;
        private String cause;
    }

    @Data @NoArgsConstructor @AllArgsConstructor
    public static class Params {
        private Double robotSpeed;
        private Double brushRpm;
        private Double waterPressure;
        private Double detergentFlowRate;
        private Double vacuumPower;
        private Double turnRadius;
        private Double passOverlap;
        private Double pathSpacing;
        private Double squeegeePressure;
        private Double dwellTime;
        private Double rpmRampRate;
        private Double maxWaterPerMin;
        private Double maxEnergyPerMin;
    }
}


