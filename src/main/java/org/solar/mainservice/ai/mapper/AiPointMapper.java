package org.solar.mainservice.ai.mapper;

import org.solar.mainservice.ai.dto.AiParamsDTO;
import org.solar.mainservice.ai.dto.AiPointDTO;
import org.solar.mainservice.model.TelemetryReading;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Objects;

@Component
public class AiPointMapper {

    public AiPointDTO toAiPoint(TelemetryReading r) {
        AiPointDTO p = new AiPointDTO();
        p.setSessionId(r.getSessionId());
        p.setPanelId(r.getPanelId());
        p.setTimestamp(Objects.toString(r.getTimestamp(), null)); // ISO-8601

        Double temp = r.getDrivers() != null ? r.getDrivers().getTemp() : null;
        Double hum  = r.getDrivers() != null ? r.getDrivers().getHumidity() : null;
        p.setTemperature(temp != null ? temp : nvl(r.getTemperature(), 0.0));
        p.setHumidity(hum  != null ? hum  : nvl(r.getHumidity(),    0.0));

        // >>> dustIndex: elegir la mejor señal disponible <<<
        Double dustIdx = computeDustIndex(r);
        p.setDustIndex(dustIdx);

        // Potencia/sensores adicionales
        p.setPowerOutput(nvl(r.getPowerOutput(), 0.0));
        p.setVibration(r.getVibration());
        p.setMicroFractureRisk(r.getMicroFractureRisk());

        if (r.getLocation() != null) {
            p.setLocation(Map.of(
                    "lat", nvl(r.getLocation().getLat(), 0.0),
                    "lng", nvl(r.getLocation().getLng(), 0.0)
            ));
        }

        if (r.getParams() != null) {
            AiParamsDTO prm = new AiParamsDTO();
            prm.setRobotSpeed(r.getParams().getRobotSpeed());
            prm.setBrushRpm(r.getParams().getBrushRpm());
            prm.setWaterPressure(r.getParams().getWaterPressure());
            prm.setDetergentFlowRate(r.getParams().getDetergentFlowRate());
            prm.setPassOverlap(r.getParams().getPassOverlap());
            prm.setDwellTime(r.getParams().getDwellTime());
            p.setParams(prm);
        }
        return p;
    }

    /** Selección robusta del 'dustIndex' para la IA (0..1). */
    private static Double computeDustIndex(TelemetryReading r) {
        // 1) locales (si hay grid)
        if (r.getGrid() != null) {
            // si hubo acción, after es más representativo del estado actual
            if (r.getGrid().getDustLocalAfter() != null) {
                return clamp01(r.getGrid().getDustLocalAfter());
            }
            if (r.getGrid().getDustLocalBefore() != null) {
                return clamp01(r.getGrid().getDustLocalBefore());
            }
            if (r.getGrid().getDustMean() != null) {
                return clamp01(r.getGrid().getDustMean());
            }
        }
        // 2) fallback global del frame
        if (r.getDustLevel() != null) {
            return clamp01(r.getDustLevel());
        }
        // 3) sin datos de polvo → 0
        return 0.0;
    }

    private static double nvl(Double v, double d) { return v != null ? v : d; }
    private static double clamp01(Double v) {
        if (v == null) return 0.0;
        return Math.max(0.0, Math.min(1.0, v));
    }
}