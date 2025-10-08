package org.solar.mainservice.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.*;
import org.springframework.data.mongodb.core.index.CompoundIndex;

import java.time.LocalDateTime;

@Getter
@Data @NoArgsConstructor @AllArgsConstructor
@Document("telemetry_readings")
@CompoundIndex(name = "panel_ts_idx", def = "{'panelId': 1, 'timestamp': -1}")
@JsonIgnoreProperties(ignoreUnknown = true) // <- robusto ante campos nuevos
public class TelemetryReading {

    @Id
    private String id;

    private String sessionId;
    private String panelId;

    @Data @NoArgsConstructor @AllArgsConstructor
    public static class State {
        private String mode;
        /** Usamos Instant ISO-8601 (acepta ...Z con milis) */
        private LocalDateTime lastChangeTs;
        private String cause;
    }
    private State state;

    @Getter
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
    private Params params;

    /** Drivers/ambiente tal como llegan dentro de 'drivers' */
    @Data @NoArgsConstructor @AllArgsConstructor
    public static class Drivers {
        private Double wind;
        private Double pm10;
        private Double rain;

        /** alias por si llega en camel */
        private Double humidity;
        @JsonAlias({"temp","temperature"})
        private Double temp;
    }
    private Drivers drivers;

    /** Snapshot del grid en el momento de la muestra */
    @Data @NoArgsConstructor @AllArgsConstructor
    public static class Grid {
        @Data @NoArgsConstructor @AllArgsConstructor
        public static class Position {
            private Integer row;
            private Integer col;
        }
        private Position position;

        @JsonAlias({"dust_local_before","dustLocalBefore"})
        private Double dustLocalBefore;

        @JsonAlias({"dust_local_after","dustLocalAfter"})
        private Double dustLocalAfter;

        @JsonAlias({"delta_local","deltaLocal"})
        private Double deltaLocal;

        private Integer passes;      // veces que pasó el robot en la celda
        private Double coverage;     // % global (si lo calculas)

        @JsonAlias({"dust_mean","dustMean"})
        private Double dustMean;

        @JsonAlias({"dust_max","dustMax"})
        private Double dustMax;
    }
    private Grid grid;

    /** Tiempo de la lectura — ahora ISO-8601 con milis y Z */
    private LocalDateTime timestamp;

    /** Sensores “de panel” en la raíz del frame */
    private Double temperature;
    private Double humidity;

    @JsonAlias({"dust","dust_level","dustLevel"})
    private Double dustLevel;

    @JsonAlias({"power","power_output","powerOutput"})
    private Double powerOutput;

    private Double vibration;

    @JsonAlias({"micro_fracture_risk","microFractureRisk"})
    private Double microFractureRisk;

    @Data @NoArgsConstructor @AllArgsConstructor
    public static class GeoLocation {
        private Double lat;
        private Double lng;
    }
    private GeoLocation location;
}
