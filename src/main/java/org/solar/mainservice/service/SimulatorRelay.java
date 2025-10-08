package org.solar.mainservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.solar.mainservice.dto.StateChangeEventDTO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class SimulatorRelay {

    private final WebClient.Builder webClientBuilder;

    @Value("${simulator.control-url:http://localhost:7072/commands}")
    private String controlUrl;

    private static final Set<String> KNOWN_PARAMS = Set.of(
            "robotSpeed","brushRpm","waterPressure","detergentFlowRate","vacuumPower",
            "turnRadius","passOverlap","pathSpacing","squeegeePressure","dwellTime",
            "rpmRampRate","maxWaterPerMin","maxEnergyPerMin"
    );

    public Mono<Map<String,Object>> relay(StateChangeEventDTO evt) {

        log.info(evt.toString());
        Map<String,Object> body = new LinkedHashMap<>();
        body.put("cause", Optional.ofNullable(evt.getCause()).orElse(evt.getType()));

        String type = Optional.ofNullable(evt.getType()).orElse("").toLowerCase(Locale.ROOT);

        switch (type) {
            case "state_change" -> {
                String nextMode = Optional.ofNullable(evt.getNext())
                        .map(StateChangeEventDTO.ModeRef::getMode)
                        .orElse(null);
                if (nextMode != null && !nextMode.isBlank()) {
                    body.put("state", Map.of("mode", nextMode));
                }
            }
            case "param_change" -> {
                Map<String, Object> params = coerceParams(evt.getParamsTarget(), evt.getDetails());
                if (!params.isEmpty()) body.put("params", params);
            }
            case "param_change_bulk" -> {
                List<Map<String,Object>> bulk = extractBulk(evt);
                if (!bulk.isEmpty()) body.put("bulk", bulk);
                else {
                    Map<String, Object> params = coerceParams(evt.getParamsTarget(), evt.getDetails());
                    if (!params.isEmpty()) body.put("params", params);
                }
            }
            case "control_update" -> {
                String nextMode = Optional.ofNullable(evt.getNext())
                        .map(StateChangeEventDTO.ModeRef::getMode)
                        .orElse(null);
                if (nextMode != null && !nextMode.isBlank()) {
                    body.put("state", Map.of("mode", nextMode));
                }
                Map<String,Object> params = coerceParams(evt.getParamsTarget(), evt.getDetails());
                if (!params.isEmpty()) body.put("params", params);
            }
            default -> {
                // nada: dejamos que el simulador responda si no hay contenido útil
            }
        }

        return webClientBuilder.build()
                .post().uri(controlUrl)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                .onErrorResume(e -> Mono.just(Map.of("ok", false, "error", e.getMessage())));
    }

    /** Convierte paramsTarget/details en { param: number } y normaliza aliases/snake_case → camelCase */
    private Map<String,Object> coerceParams(Map<String,Object> paramsTarget, Map<String,Object> details) {
        Map<String,Object> out = new LinkedHashMap<>();
        Map<String,Object> src = new LinkedHashMap<>();
        if (paramsTarget != null) src.putAll(paramsTarget);

        // Si alguien metió params en details, los recogemos también.
        if (details != null && details.get("params") instanceof Map<?,?> m) {
            m.forEach((k,v) -> src.put(String.valueOf(k), v));
        }

        src.forEach((k, v) -> {
            String key = normalizeKey(k);
            if (!KNOWN_PARAMS.contains(key)) return; // ignoramos lo que el sim no conoce
            Object val = toNumber(v);
            if (val != null) out.put(key, val);
        });
        return out;
    }

    private List<Map<String,Object>> extractBulk(StateChangeEventDTO evt) {
        List<Map<String,Object>> bulk = new ArrayList<>();
        if (evt.getDetails() != null && evt.getDetails().get("changes") instanceof List<?> list) {
            for (Object o : list) {
                if (o instanceof Map<?,?> m && m.get("params") instanceof Map<?,?> pm) {
                    Map<String,Object> one = Map.of("params", coerceParams((Map<String,Object>) pm, null));
                    if (!((Map<?,?>)one.get("params")).isEmpty()) bulk.add(one);
                }
            }
        }
        return bulk;
    }

    /** snake_case → camelCase y aliases típicos */
    private String normalizeKey(String raw) {
        if (raw == null) return "";
        String k = raw.trim();
        // aliases puntuales
        if (k.equalsIgnoreCase("brushRPM")) return "brushRpm";
        // snake_case → camelCase
        if (k.contains("_")) {
            String[] parts = k.toLowerCase(Locale.ROOT).split("_");
            StringBuilder sb = new StringBuilder(parts[0]);
            for (int i=1;i<parts.length;i++) {
                sb.append(parts[i].substring(0,1).toUpperCase()).append(parts[i].substring(1));
            }
            k = sb.toString();
        }
        return k;
    }

    private Number toNumber(Object v) {
        if (v instanceof Number n) return n.doubleValue();
        if (v instanceof String s) {
            try { return Double.parseDouble(s); } catch (Exception ignored) {}
        }
        return null;
    }
}