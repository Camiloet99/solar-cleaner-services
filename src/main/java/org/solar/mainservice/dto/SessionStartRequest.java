package org.solar.mainservice.dto;

import lombok.Data;

import java.time.Instant;
import java.util.Map;

@Data
public class SessionStartRequest {
    private String panelId;
    private String sessionId;       // opcional, si no llega generamos UUID
    private Map<String, Object> meta; // opcional (escenario, etc.)
}


