package org.solar.mainservice.safety;

import lombok.Builder;
import lombok.Data;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
public class SafetyGuard {

    @Data @Builder
    public static class Limits {
        private double rpmMin;       // p.ej. 500
        private double rpmMax;       // p.ej. 1200
        private double flowMin;      // p.ej. 0.10 L/min  (no menos de esto)
        private double flowMax;      // p.ej. 0.60 L/min
        private double pressMin;     // p.ej. 1.2 bar
        private double pressMax;     // p.ej. 2.5 bar
        private double detPctMax;    // p.ej. 0.06 => 6% mezcla
        private double detPctMin;    // NUEVO: p.ej. 0.02 => 2% mezcla
        private double dwellMin;     // NUEVO: p.ej. 2 s
        private double dwellMax;     // NUEVO: p.ej. 8 s
        private int    passesMin;    // NUEVO: p.ej. 1
        private int    passesMax;    // NUEVO: p.ej. 3
        private double maxDeltaRpm;  // p.ej. 150 rpm por ventana
    }

    @Data @Builder
    public static class Commands {
        private double brushRpm;
        private double waterFlowLpm;
        private double nozzlePressureBar;
        private int    passes;
        private double detergentPct; // 0..1 (fracción de mezcla)
        private String route;
        private int    dwellSec;
    }

    /**
     * Convierte propuestas 0..1 a unidades y aplica límites duros + mínimos operativos.
     */
    public Commands projectSafe(Map<String, Object> proposed, Limits lim, Commands prev, List<String> notes) {
        double rpm01   = get01(proposed, "brushRpm");
        double flow01  = get01(proposed, "waterFlow");
        double press01 = get01(proposed, "pressure");
        double pass01  = get01(proposed, "passes");
        double det01   = get01(proposed, "detergentPct");
        double dwell01 = get01(proposed, "dwellSec"); // si no viene, se forzará a mínimo

        // 0..1 → unidades
        double rpm    = lerp(lim.rpmMin,   lim.rpmMax,   rpm01);
        double flow   = lerp(lim.flowMin,  lim.flowMax,  flow01);
        double press  = lerp(lim.pressMin, lim.pressMax, press01);
        int    passes = (int) Math.round(lerp(lim.passesMin, lim.passesMax, pass01));
        double det    = lerp(lim.detPctMin, lim.detPctMax, det01);   // ← mínimo de mezcla
        int    dwell  = (int) Math.round(lerp(lim.dwellMin, lim.dwellMax, dwell01)); // ← mínimo contacto

        // Rate limit en RPM
        if (prev != null && Math.abs(rpm - prev.brushRpm) > lim.maxDeltaRpm) {
            rpm = prev.brushRpm + Math.signum(rpm - prev.brushRpm) * lim.maxDeltaRpm;
            notes.add("rate_limited_rpm");
        }

        // Cierres por límites duros (redundantes pero seguros)
        rpm   = clamp(rpm,   lim.rpmMin,   lim.rpmMax);
        flow  = clamp(flow,  lim.flowMin,  lim.flowMax);
        press = clamp(press, lim.pressMin, lim.pressMax);
        det   = clamp(det,   lim.detPctMin, lim.detPctMax);
        dwell = (int) clamp(dwell, lim.dwellMin, lim.dwellMax);
        passes = (int) clamp(passes, lim.passesMin, lim.passesMax);

        // Ruta: mayor probabilidad si viene distribuida
        String route = "keep";
        Object r = proposed.get("route");
        if (r instanceof Map) {
            route = ((Map<String, Double>) r).entrySet()
                    .stream().max(Map.Entry.comparingByValue())
                    .map(Map.Entry::getKey).orElse("keep");
        }

        return Commands.builder()
                .brushRpm(rpm)
                .waterFlowLpm(flow)
                .nozzlePressureBar(press)
                .passes(passes)
                .detergentPct(det)
                .route(route)
                .dwellSec(dwell)
                .build();
    }

    public Commands antiRegression(Commands c,
                                   Limits lim,
                                   double beforePct,
                                   double afterPct,
                                   double targetPct,
                                   double minDeltaPct,
                                   List<String> notes) {

        if (c == null || lim == null) return c;
        if (notes == null) notes = new ArrayList<>();

        // Normaliza entradas erróneas
        if (Double.isNaN(beforePct)) beforePct = afterPct;
        if (Double.isNaN(afterPct))  afterPct  = beforePct;
        if (Double.isNaN(targetPct)) targetPct = 10.0;
        if (Double.isNaN(minDeltaPct)) minDeltaPct = 5.0;

        final double delta = beforePct - afterPct;

        // Histéresis suave: pide que esté un poco por encima del target para actuar
        final double actThreshold = targetPct + 0.5; // evita activar pegado al objetivo

        if (afterPct > actThreshold && delta < minDeltaPct) {
            // Mezcla (detergente): 25% más, dentro de límites
            c.setDetergentPct(clamp(c.getDetergentPct() * 1.25, lim.detPctMin, lim.detPctMax));

            // Caudal de agua: +0.05 L/min (ajusta si tu sistema es más sensible)
            c.setWaterFlowLpm(clamp(c.getWaterFlowLpm() + 0.05, lim.flowMin, lim.flowMax));

            // Presión de boquilla: +0.2 bar
            c.setNozzlePressureBar(clamp(c.getNozzlePressureBar() + 0.2, lim.pressMin, lim.pressMax));

            // Tiempo de contacto (dwell): +1 s
            c.setDwellSec((int) clamp(c.getDwellSec() + 1, lim.dwellMin, lim.dwellMax));

            // Pasadas: +1 hasta el máximo
            c.setPasses((int) clamp(c.getPasses() + 1, lim.passesMin, lim.passesMax));

            notes.add(String.format(
                    "anti_regression_boost: after=%.2f%%, delta=%.2f%%, target=%.2f%%",
                    afterPct, delta, targetPct));
        }

        return c;
    }

    private static double get01(Map<String,Object> m, String k) {
        return Optional.ofNullable(m.get(k))
                .filter(Number.class::isInstance)
                .map(Number.class::cast)
                .map(Number::doubleValue)
                .map(v -> clamp(v,0,1))
                .orElse(0.0);
    }
    private static double lerp(double lo, double hi, double x01){ return lo + (hi - lo) * clamp(x01,0,1); }
    private static double clamp(double v, double lo, double hi){ return Math.max(lo, Math.min(hi, v)); }
}
