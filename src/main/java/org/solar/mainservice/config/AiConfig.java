package org.solar.mainservice.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;

@Configuration
public class AiConfig {
    @Bean
    WebClient aiWebClient(
            @Value("${ai.base-url:http://localhost:5001}") String baseUrl,
            @Value("${ai.timeout-ms:2000}") long timeoutMs) {
        HttpClient http = HttpClient.create().responseTimeout(Duration.ofMillis(timeoutMs));
        return WebClient.builder()
                .baseUrl(baseUrl)
                .clientConnector(new ReactorClientHttpConnector(http))
                .build();
    }
}
