package com.omyfish.species.config;

import com.omyfish.species.application.service.IdentificationService;
import com.omyfish.species.domain.port.in.IdentifyFishUseCase;
import com.omyfish.species.domain.port.out.AIServicePort;
import com.omyfish.species.domain.port.out.EventPublisherPort;
import com.omyfish.species.domain.port.out.SpeciesRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AppConfig {

    @Bean
    public IdentifyFishUseCase identifyFishUseCase(
        AIServicePort aiService,
        SpeciesRepository speciesRepository,
        EventPublisherPort eventPublisher
    ) {
        return new IdentificationService(aiService, speciesRepository, eventPublisher);
    }
}
