package org.solar.mainservice.websocket;

import lombok.RequiredArgsConstructor;
import org.solar.mainservice.model.PredictionResult;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class WebSocketNotifier {

    private final SimpMessagingTemplate messagingTemplate;

    public Mono<Void> notifyFrontend(PredictionResult prediction) {
        messagingTemplate.convertAndSend("/topic/predictions", prediction);
        return Mono.empty();
    }
}

