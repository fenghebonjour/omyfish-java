package com.omyfish.observation.config;

import com.omyfish.observation.application.service.ObservationService;
import com.omyfish.observation.domain.model.Observation;
import com.omyfish.observation.domain.port.in.CreateObservationUseCase;
import org.springframework.transaction.annotation.Transactional;

/**
 * Wraps {@code ObservationService.create()} in a single database transaction so the observation
 * insert and the outbox-row insert either both commit or both roll back (WEAKNESS_AUDIT.md
 * §2.3). Lives here rather than on {@code ObservationService} itself because
 * {@code application/} must stay free of Spring annotations — see CLAUDE.md's hexagonal
 * architecture rule; {@code config/} is where ports get wired to their transactional behavior.
 */
class TransactionalCreateObservationUseCase implements CreateObservationUseCase {

    private final ObservationService delegate;

    TransactionalCreateObservationUseCase(ObservationService delegate) {
        this.delegate = delegate;
    }

    @Override
    @Transactional
    public Observation create(CreateCommand command) {
        return delegate.create(command);
    }
}
