package org.solar.mainservice.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.Map;

@Data
@Getter
public class StateChangeEventDTO {
    private String type;

    private String sessionId;
    private String panelId;
    private Integer version;
    private String cause;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime timestamp;

    private ModeRef prev; // opcional
    private ModeRef next; // opcional

    private Map<String, Object> paramsTarget; // opcional

    private Map<String, Object> details;      // opcional

    @Data
    public static class ModeRef { private String mode; }
}
