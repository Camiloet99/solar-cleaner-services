package org.solar.mainservice.service;

import lombok.AllArgsConstructor;
import org.solar.mainservice.dto.SessionStartRequest;
import org.solar.mainservice.dto.SessionStartResponse;
import org.solar.mainservice.model.Session;
import org.solar.mainservice.repository.SessionRepository;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.UUID;

@Service
@AllArgsConstructor
public class SessionService {
    private final SessionRepository repo;

    public Mono<SessionStartResponse> start(SessionStartRequest req) {
        String id = (req.getSessionId() == null || req.getSessionId().isBlank())
                ? UUID.randomUUID().toString()
                : req.getSessionId();

        Session s = new Session(id, req.getPanelId(), Instant.now(), null, "active");
        return repo.save(s).map(saved -> new SessionStartResponse(saved.getId(), saved.getPanelId(), saved.getStartTime()));
    }

    public Mono<Session> stop(String id) {
        return repo.findById(id)
                .flatMap(s -> { s.setEndTime(Instant.now()); s.setStatus("ended"); return repo.save(s); });
    }

    public Flux<Session> active() { return repo.findByStatus("active"); }
}

