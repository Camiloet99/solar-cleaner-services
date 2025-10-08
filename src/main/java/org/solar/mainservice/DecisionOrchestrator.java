package org.solar.mainservice;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.solar.mainservice.ai.dto.AiPredictResponse;
import org.solar.mainservice.dto.StateChangeEventDTO;
import org.solar.mainservice.safety.SafetyGuard;
import org.solar.mainservice.service.SimulatorRelay;
import org.solar.mainservice.websocket.WebSocketNotifier;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import java.lang.reflect.Method;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class DecisionOrchestrator {

    private final SimulatorRelay simulatorRelay;
    private final WebSocketNotifier webSocketNotifier;
    private final SafetyGuard safetyGuard;
    private final Environment env;

    private SafetyGuard.Commands prev;

    public Mono<Void> handlePrediction(String sessionId,
                                       String panelId,
                                       String prevMode,
                                       Map<String, Object> prevParams,
                                       AiPredictResponse pr,
                                       boolean applyControl) {

        log.info("[ORCH] IN session={} panel={} applyControl={} pr={}", sessionId, panelId, applyControl, pr);

        webSocketNotifier.sendRuntimeEvent("ai_prediction", Map.of(
                "sessionId", sessionId,
                "panelId", panelId,
                "prediction", pr
        ), sessionId, null);

        if (!applyControl || pr.getProposedCommands() == null || pr.getProposedCommands().isEmpty()) {
            log.info("[ORCH] SKIP control (applyControl={} or no commands)", applyControl);
            return Mono.empty();
        }

        // ===== 1) Límites físicos y mínimos operativos =====
        var limits = SafetyGuard.Limits.builder()
                .rpmMin(env.getProperty("control.limits.rpmMin", Double.class, 500.0))
                .rpmMax(env.getProperty("control.limits.rpmMax", Double.class, 1200.0))
                .flowMin(env.getProperty("control.limits.flowMin", Double.class, 0.10))    // L/min
                .flowMax(env.getProperty("control.limits.flowMax", Double.class, 0.60))
                .pressMin(env.getProperty("control.limits.pressMin", Double.class, 1.2))   // bar
                .pressMax(env.getProperty("control.limits.pressMax", Double.class, 2.5))
                .detPctMin(env.getProperty("control.limits.detPctMin", Double.class, 0.02))// fracción 0..1
                .detPctMax(env.getProperty("control.limits.detPctMax", Double.class, 0.06))
                .dwellMin(env.getProperty("control.limits.dwellMin", Double.class, 2.0))   // s
                .dwellMax(env.getProperty("control.limits.dwellMax", Double.class, 8.0))   // s
                .passesMin(env.getProperty("control.limits.passesMin", Integer.class, 1))
                .passesMax(env.getProperty("control.limits.passesMax", Integer.class, 3))
                .maxDeltaRpm(env.getProperty("control.limits.maxDeltaRpm", Double.class, 150.0))
                .build();

        // ===== 2) Proyección segura 0..1 → unidades =====
        List<String> safetyNotes = new ArrayList<>();
        SafetyGuard.Commands applied = safetyGuard.projectSafe(
                pr.getProposedCommands(), limits, prev, safetyNotes);

        // ===== 3) Anti-regresión por ventana =====
        double[] ba = extractBeforeAfter(pr); // [beforePct, afterPct]
        double before = ba[0];
        double after  = ba[1];

        double targetPct   = env.getProperty("control.target.dustPct", Double.class, 10.0);
        double minDeltaPct = env.getProperty("control.target.minDeltaPct", Double.class, 5.0);
        double targetFinal = env.getProperty("control.target.finalDustPct", Double.class, 2.0); // drive-to-zero band

        applied = safetyGuard.antiRegression(applied, limits, before, after, targetPct, minDeltaPct, safetyNotes);

        // ===== 3.1) No bajar líquidos/pressión si seguimos por encima del objetivo FINAL =====
        if (prev != null && after > targetFinal) {
            // Mantener no-decreciente respecto a la pasada anterior
            applied.setWaterFlowLpm(
                    clamp(Math.max(applied.getWaterFlowLpm(), prev.getWaterFlowLpm()), limits.getFlowMin(), limits.getFlowMax()));
            applied.setNozzlePressureBar(
                    clamp(Math.max(applied.getNozzlePressureBar(), prev.getNozzlePressureBar()), limits.getPressMin(), limits.getPressMax()));
            applied.setDetergentPct(
                    clamp(Math.max(applied.getDetergentPct(), prev.getDetergentPct()), limits.getDetPctMin(), limits.getDetPctMax()));
            safetyNotes.add("no_decrease_fluids_while_dirty_final");
        }

        // ===== 3.2) Aplicar boosts enviados por la IA (contact/passes/speed) =====
        Boosts boosts = parseBoosts(pr.getExplain()); // lee "boosts(c=.., p=.., v-=..)" si viene
        if (boosts != null) {
            // dwell/contact
            if (boosts.contactBoost > 0) {
                applied.setDwellSec((int) clamp(applied.getDwellSec() + boosts.contactBoost, limits.getDwellMin(), limits.getDwellMax()));
                safetyNotes.add("contact_boost+" + boosts.contactBoost);
            }
            // passes
            if (boosts.passesBoost > 0) {
                applied.setPasses((int) clamp(applied.getPasses() + boosts.passesBoost, limits.getPassesMin(), limits.getPassesMax()));
                safetyNotes.add("passes_boost+" + boosts.passesBoost);
            }
            // speed_down se aplica en nextParams (robotSpeed)
        }

        // ===== Persistimos lo aplicado (para monotonicidad en la próxima iteración) =====
        prev = applied;

        // ===== 4) Construcción de params para el simulador =====
        Map<String, Object> nextParams = new HashMap<>();
        nextParams.put("brushRpm", applied.getBrushRpm());
        nextParams.put("waterPressure", applied.getNozzlePressureBar());
        nextParams.put("waterFlow", applied.getWaterFlowLpm());

        // Detergente: LPM = %mezcla (0..1) * caudal de agua (L/min)
        double detergentLpm = applied.getDetergentPct() * applied.getWaterFlowLpm();
        nextParams.put("detergentFlowRate", detergentLpm);

        // Velocidad base y reducción por boosts (clamp 0.25–0.55 m/s)
        double baseSpeed = env.getProperty("control.defaults.speedMs", Double.class, 0.35);
        double speedDown = (boosts != null ? boosts.speedDown : 0.0);
        double finalSpeed = clamp(baseSpeed - speedDown, 0.25, 0.55);
        nextParams.put("robotSpeed", finalSpeed);

        // Overlap fijo/por defecto (ajusta a contrato de tu sim)
        nextParams.put("passOverlap", env.getProperty("control.defaults.passOverlapFrac", Double.class, 0.30)); // fracción 0..1
        nextParams.put("dwellTime", applied.getDwellSec());
        nextParams.put("passes", applied.getPasses());

        // Si IA recomienda "now", sube pisos mínimos (bump) adicionales
        if ("now".equalsIgnoreCase(pr.getRecommendedCleaningFrequency())) {
            bumpFloor(nextParams, "brushRpm",     env.getProperty("control.bump.now.rpm",     Double.class, 800d));
            bumpFloor(nextParams, "waterPressure",env.getProperty("control.bump.now.press",   Double.class, 1.8d));
            bumpFloor(nextParams, "waterFlow",    env.getProperty("control.bump.now.flow",    Double.class, 0.35d));
        }

        // ===== 5) Cause / Mode =====
        String cause = "AI_DECISION";
        String nextMode = (prevMode != null) ? prevMode : "AUTO";
        if ("now".equalsIgnoreCase(pr.getRecommendedCleaningFrequency())) {
            cause = "AI_NOW";
            nextMode = "CLEANING";
        } else if ("hold_20s".equalsIgnoreCase(pr.getRecommendedCleaningFrequency())) {
            cause = "AI_HOLD";
        }

        // ===== 6) Evento hacia el simulador =====
        StateChangeEventDTO.ModeRef prevRef = null;
        if (prevMode != null && !prevMode.isBlank()) {
            prevRef = new StateChangeEventDTO.ModeRef(); prevRef.setMode(prevMode);
        }
        StateChangeEventDTO.ModeRef nextRef = new StateChangeEventDTO.ModeRef(); nextRef.setMode(nextMode);

        StateChangeEventDTO evt = new StateChangeEventDTO();
        evt.setType("param_change");
        evt.setSessionId(sessionId);
        evt.setPanelId(panelId);
        evt.setCause(cause);
        evt.setTimestamp(LocalDateTime.now());
        evt.setPrev(prevRef);
        evt.setNext(nextRef);
        evt.setParamsTarget(nextParams);

        SafetyGuard.Commands finalApplied = applied;
        return simulatorRelay.relay(evt)
                .doOnSuccess(r -> webSocketNotifier.sendRuntimeEvent("ai_decision", Map.of(
                        "sessionId", sessionId,
                        "proposed", pr.getProposedCommands(),
                        "applied", finalApplied,
                        "notes", safetyNotes,
                        "explain", pr.getExplain(),
                        "beforePct", before,
                        "afterPct", after
                ), sessionId, null))
                .then();
    }

    // ====================== Helpers ======================

    private void bumpFloor(Map<String, Object> m, String k, Double floor) {
        Object v = m.get(k);
        if (v instanceof Number) {
            double cur = ((Number) v).doubleValue();
            if (cur < floor) m.put(k, floor);
        }
    }

    private static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    /** Extrae before/after (%) desde AiPredictResponse sin acoplarse a un nombre fijo. */
    private double[] extractBeforeAfter(AiPredictResponse pr) {
        Double before = tryGetter(pr, "getWindowBeforeDustPct");
        Double after  = tryGetter(pr, "getWindowAfterDustPct");
        if (before != null && after != null) return new double[]{before, after};

        before = tryGetter(pr, "getBeforeDustPct");
        after  = tryGetter(pr, "getAfterDustPct");
        if (before != null && after != null) return new double[]{before, after};

        Map<String, Object> stats = tryMapGetter(pr, "getStats");
        if (stats == null) stats = tryMapGetter(pr, "getMetrics");
        if (stats == null) stats = tryMapGetter(pr, "getWindow");
        if (stats != null) {
            Double b = pickFirst(stats, "beforeDustPct","before_pct","before","avgBeforeDustPct");
            Double a = pickFirst(stats, "afterDustPct","after_pct","after","avgAfterDustPct");
            if (b != null && a != null) return new double[]{b, a};
        }
        return new double[]{10.0, 10.0};
    }

    private Double pickFirst(Map<String, Object> m, String... keys) {
        for (String k : keys) {
            Object v = m.get(k);
            if (v instanceof Number) return ((Number) v).doubleValue();
        }
        return null;
    }

    private Map<String, Object> tryMapGetter(Object obj, String method) {
        try {
            Method m = obj.getClass().getMethod(method);
            Object out = m.invoke(obj);
            if (out instanceof Map) {
                //noinspection unchecked
                return (Map<String, Object>) out;
            }
        } catch (Exception ignore) { }
        return null;
    }

    private Double tryGetter(Object obj, String method) {
        try {
            Method m = obj.getClass().getMethod(method);
            Object out = m.invoke(obj);
            if (out instanceof Number) return ((Number) out).doubleValue();
        } catch (Exception ignore) { }
        return null;
    }

    // ===== Parsing de boosts desde explain =====
    private static final Pattern BOOSTS_RE =
            Pattern.compile("boosts\\(c=(?<c>[-+]?\\d+),\\s*p=(?<p>[-+]?\\d+),\\s*v-=(?<v>[\\d.]+)\\)", Pattern.CASE_INSENSITIVE);

    private Boosts parseBoosts(String explain) {
        if (explain == null) return null;
        Matcher m = BOOSTS_RE.matcher(explain);
        if (!m.find()) return null;
        try {
            int c = Integer.parseInt(m.group("c"));
            int p = Integer.parseInt(m.group("p"));
            double v = Double.parseDouble(m.group("v"));
            return new Boosts(c, p, v);
        } catch (Exception e) {
            return null;
        }
    }

    private record Boosts(int contactBoost, int passesBoost, double speedDown) {}
}