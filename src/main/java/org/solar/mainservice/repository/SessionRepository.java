package org.solar.mainservice.repository;

import org.solar.mainservice.model.Session;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;

@Repository
public interface SessionRepository extends ReactiveMongoRepository<Session, String> {
    Flux<Session> findByStatus(String status);
}

