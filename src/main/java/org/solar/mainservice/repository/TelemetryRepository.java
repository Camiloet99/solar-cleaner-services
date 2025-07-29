package org.solar.mainservice.repository;

import org.solar.mainservice.model.TelemetryReading;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;

@Repository
public interface TelemetryRepository extends ReactiveMongoRepository<TelemetryReading, String> {
    Flux<TelemetryReading> findTop10BySessionIdOrderByTimestampDesc(String sessionId);
}
