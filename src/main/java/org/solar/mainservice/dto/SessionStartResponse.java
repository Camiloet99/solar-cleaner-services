package org.solar.mainservice.dto;

import lombok.Data;

import java.time.Instant;

@Data
public class SessionStartResponse {
    private String sessionId;
    private String panelId;
    private Instant startTime;
    public SessionStartResponse(String sessionId, String panelId, Instant startTime) {
        this.sessionId = sessionId; this.panelId = panelId; this.startTime = startTime;
    }
}