package com.omyfish.observation.config;

import com.omyfish.observation.application.service.ObservationService;
import com.omyfish.observation.domain.port.in.CreateObservationUseCase;
import com.omyfish.observation.domain.port.out.EventPublisherPort;
import com.omyfish.observation.domain.port.out.ObservationRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
public class AppConfig {

    @Bean
    ObservationService observationService(ObservationRepository repository, EventPublisherPort eventPublisher) {
        return new ObservationService(repository, eventPublisher);
    }

    // @Primary over the plain ObservationService bean (which also implements
    // CreateObservationUseCase) so callers get the transactional wrapper — see
    // TransactionalCreateObservationUseCase's Javadoc (WEAKNESS_AUDIT.md §2.3).
    @Bean
    @Primary
    CreateObservationUseCase createObservationUseCase(ObservationService observationService) {
        return new TransactionalCreateObservationUseCase(observationService);
    }
}
