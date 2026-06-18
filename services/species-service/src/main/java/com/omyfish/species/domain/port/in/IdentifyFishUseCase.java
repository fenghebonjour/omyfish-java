package com.omyfish.species.domain.port.in;

import com.omyfish.species.application.service.IdentificationService;

public interface IdentifyFishUseCase {
    IdentificationService.IdentificationResult identify(IdentificationService.IdentifyFishCommand command);
}
