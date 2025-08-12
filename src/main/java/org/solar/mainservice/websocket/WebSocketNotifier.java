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
        messagingTemplate.convertAndSend(dest, TelemetryWS.from(reading));
    }

    public void sendPrediction(PredictionResult pr) {
        String dest = "/topic/predictions/" + pr.getSessionId();
        messagingTemplate.convertAndSend(dest, pr);
    }

    /** DTO estable para el frontend */
    public static record TelemetryWS(
            String sessionId,
            long timestampMs,
            Double power,
            Double temperature,
            Double humidity,
            Double dust,
            Double vibration,
            Double microFractureRisk,
            Geo location
    ) {
        public static TelemetryWS from(TelemetryReading r) {
            long ts = r.getTimestamp() != null
                    ? r.getTimestamp().atZone(java.time.ZoneOffset.UTC).toInstant().toEpochMilli()
                    : System.currentTimeMillis();
            TelemetryReading.GeoLocation loc = r.getLocation();
            Geo g = loc != null ? new Geo(loc.getLat(), loc.getLng()) : null;
            return new TelemetryWS(
                    r.getSessionId(),
                    ts,
                    getOrNull(() -> r.getPowerOutput()),
                    getOrNull(() -> r.getTemperature()),
                    getOrNull(() -> r.getHumidity()),
                    getOrNull(() -> r.getDustLevel()),
                    getOrNull(() -> r.getVibration()),
                    getOrNull(() -> r.getMicroFractureRisk()),
                    g
            );
        }
        private static Double getOrNull(java.util.concurrent.Callable<Double> c) {
            try { return c.call(); } catch (Exception e) { return null; }
        }
        public static record Geo(double lat, double lng) {}
    }
}

