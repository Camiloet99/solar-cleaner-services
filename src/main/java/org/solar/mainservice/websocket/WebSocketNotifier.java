package org.solar.mainservice.websocket;

import lombok.RequiredArgsConstructor;
import org.solar.mainservice.model.PredictionResult;
import org.solar.mainservice.model.TelemetryReading;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class WebSocketNotifier {

    private final SimpMessagingTemplate messagingTemplate;

    public void sendTelemetry(TelemetryReading reading) {
        String dest = "/topic/telemetry/" + reading.getSessionId();
        var payload = TelemetryWs.from(reading);
        messagingTemplate.convertAndSend(dest, payload);

        messagingTemplate.convertAndSend("/topic/telemetry", payload);
        if (reading.getPanelId() != null && !reading.getPanelId().isBlank()) {
            messagingTemplate.convertAndSend("/topic/panels/" + reading.getPanelId() + "/telemetry", payload);
        }
    }

    public void sendRuntimeEvent(String type, Object evt, String sessionId, String panelId) {
        messagingTemplate.convertAndSend("/topic/" + type, evt);
        if (sessionId != null && !sessionId.isBlank()) {
            messagingTemplate.convertAndSend("/topic/sessions/" + sessionId + "/" + type, evt);
        }
        if (panelId != null && !panelId.isBlank()) {
            messagingTemplate.convertAndSend("/topic/panels/" + panelId + "/" + type, evt);
        }
    }

    public void sendPrediction(PredictionResult pr) {
        String dest = "/topic/predictions/" + pr.getSessionId();
        messagingTemplate.convertAndSend(dest, pr);
    }

    // ↓ dentro de WebSocketNotifier
    public static record TelemetryWs(
            String sessionId,
            String panelId,
            String timestamp,
            Double temperature,
            Double humidity,
            Double dustLevel,
            Double vibration,
            Double microFractureRisk,
            Double powerOutput,
            Geo location,
            StateWs state,     // ← nuevo
            ParamsWs params    // ← nuevo
    ) {
        static TelemetryWs from(TelemetryReading r) {
            Geo g = null;
            if (r.getLocation() != null && r.getLocation().getLat() != null && r.getLocation().getLng() != null) {
                g = new Geo(r.getLocation().getLat(), r.getLocation().getLng());
            }
            StateWs st = null;
            if (r.getState() != null) {
                st = new StateWs(
                        r.getState().getMode(),
                        r.getState().getLastChangeTs() != null ? r.getState().getLastChangeTs().toString() : null,
                        r.getState().getCause()
                );
            }
            ParamsWs ps = null;
            if (r.getParams() != null) {
                var p = r.getParams();
                ps = new ParamsWs(
                        p.getRobotSpeed(), p.getBrushRpm(), p.getWaterPressure(),
                        p.getDetergentFlowRate(), p.getVacuumPower(), p.getTurnRadius(),
                        p.getPassOverlap(), p.getPathSpacing(), p.getSqueegeePressure(),
                        p.getDwellTime(), p.getRpmRampRate(), p.getMaxWaterPerMin(), p.getMaxEnergyPerMin()
                );
            }
            return new TelemetryWs(
                    r.getSessionId(),
                    r.getPanelId(),
                    r.getTimestamp() != null ? r.getTimestamp().toString() : null,
                    getOrNull(r::getTemperature),
                    getOrNull(r::getHumidity),
                    getOrNull(r::getDustLevel),
                    getOrNull(r::getVibration),          // <- aquí va vibration
                    getOrNull(r::getMicroFractureRisk),  // <- aquí microFractureRisk
                    getOrNull(r::getPowerOutput),        // <- y aquí powerOutput
                    g, st, ps
            );
        }

        private static Double getOrNull(java.util.concurrent.Callable<Double> c) {
            try { return c.call(); } catch (Exception e) { return null; }
        }
        public static record Geo(double lat, double lng) {}
        public static record StateWs(String mode, String lastChangeTs, String cause) {}
        public static record ParamsWs(
                Double robotSpeed, Double brushRpm, Double waterPressure,
                Double detergentFlowRate, Double vacuumPower, Double turnRadius,
                Double passOverlap, Double pathSpacing, Double squeegeePressure,
                Double dwellTime, Double rpmRampRate, Double maxWaterPerMin, Double maxEnergyPerMin
        ) {}
    }

}

