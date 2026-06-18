package com.omyfish.observation.config;

import com.omyfish.observation.application.service.ObservationService;
import com.omyfish.observation.domain.port.in.CreateObservationUseCase;
import com.omyfish.observation.domain.port.out.ObservationRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AppConfig {

    @Bean
    public CreateObservationUseCase createObservationUseCase(ObservationRepository repository) {
        return new ObservationService(repository);
    }
}
