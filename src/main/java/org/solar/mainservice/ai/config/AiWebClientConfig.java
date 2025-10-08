package org.solar.mainservice.ai.config;

import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

@Configuration
@RequiredArgsConstructor
public class AiWebClientConfig {

    @Bean(name = "aiWebClient")
    public WebClient aiWebClient(
            @Value("${ai.base-url:http://localhost:5001}") String baseUrl,
            @Value("${ai.timeout-ms:2000}") int timeoutMs
    ) {
        // Cliente Netty con timeouts sólidos
        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, Math.max(1000, timeoutMs / 2))
                .responseTimeout(Duration.ofMillis(timeoutMs))
                .doOnConnected(conn -> conn
                        .addHandlerLast(new ReadTimeoutHandler(timeoutMs, TimeUnit.MILLISECONDS))
                        .addHandlerLast(new WriteTimeoutHandler(timeoutMs, TimeUnit.MILLISECONDS))
                )
                // Descomenta si sospechas keep-alive problemático con el servidor
                //.keepAlive(false)
                ;

        // Ampliar tamaño de payload (por si mandas ventanas grandes)
        ExchangeStrategies strategies = ExchangeStrategies.builder()
                .codecs(cfg -> cfg.defaultCodecs().maxInMemorySize(2 * 1024 * 1024)) // 2MB
                .build();

        return WebClient.builder()
                .baseUrl(baseUrl) // IMPORTANTE: en Docker usar http://ai:5001
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .exchangeStrategies(strategies)
                .defaultHeaders(h -> {
                    h.set("Accept", "application/json");
                    h.set("Connection", "keep-alive"); // o "close" si persiste el problema
                })
                .build();
    }
}
