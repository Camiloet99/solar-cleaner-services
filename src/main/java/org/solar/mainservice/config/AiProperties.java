package org.solar.mainservice.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter @Setter
@Component
@ConfigurationProperties(prefix = "ai")
public class AiProperties {
    private boolean enabled = true;
    private String baseUrl = "http://localhost:5001";
    private int timeoutMs = 2000;
}