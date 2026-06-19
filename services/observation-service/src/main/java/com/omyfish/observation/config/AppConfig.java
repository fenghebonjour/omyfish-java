package com.omyfish.observation.config;

import com.omyfish.observation.application.service.ObservationService;
import com.omyfish.observation.domain.port.in.CreateObservationUseCase;
import com.omyfish.observation.domain.port.in.GetObservationUseCase;
import com.omyfish.observation.domain.port.in.ListObservationsUseCase;
import com.omyfish.observation.domain.port.out.EventPublisherPort;
import com.omyfish.observation.domain.port.out.ObservationRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AppConfig {

    @Bean
    ObservationService observationService(ObservationRepository repository, EventPublisherPort eventPublisher) {
        return new ObservationService(repository, eventPublisher);
    }

    @Bean
    public CreateObservationUseCase createObservationUseCase(ObservationService svc) {
        return svc::create;
    }

    @Bean
    public GetObservationUseCase getObservationUseCase(ObservationService svc) {
        return svc::get;
    }

    @Bean
    public ListObservationsUseCase listObservationsUseCase(ObservationService svc) {
        return svc::listByUser;
    }
}
