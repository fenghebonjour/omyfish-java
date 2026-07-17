package com.omyfish.species.config;

import com.omyfish.species.application.service.BiteForecastService;
import com.omyfish.species.application.service.IdentificationService;
import com.omyfish.species.application.service.SpeciesCatalogService;
import com.omyfish.species.domain.port.in.BrowseSpeciesUseCase;
import com.omyfish.species.domain.port.in.GetBiteForecastUseCase;
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

    @Bean
    public GetBiteForecastUseCase getBiteForecastUseCase(AIServicePort aiService) {
        return new BiteForecastService(aiService);
    }

    @Bean
    public BrowseSpeciesUseCase browseSpeciesUseCase(SpeciesRepository speciesRepository) {
        return new SpeciesCatalogService(speciesRepository);
    }
}
