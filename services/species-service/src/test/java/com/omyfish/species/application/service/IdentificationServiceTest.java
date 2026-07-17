package com.omyfish.species.application.service;

import com.omyfish.species.application.service.IdentificationService.IdentificationResult;
import com.omyfish.species.application.service.IdentificationService.IdentifyFishCommand;
import com.omyfish.species.domain.model.Species;
import com.omyfish.species.domain.port.out.AIServicePort;
import com.omyfish.species.domain.port.out.AIServicePort.AIPrediction;
import com.omyfish.species.domain.port.out.AIServicePort.AIResult;
import com.omyfish.species.domain.port.out.EventPublisherPort;
import com.omyfish.species.domain.port.out.SpeciesRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class IdentificationServiceTest {

    @Mock AIServicePort aiService;
    @Mock SpeciesRepository speciesRepository;
    @Mock EventPublisherPort eventPublisher;
    @InjectMocks IdentificationService service;

    private static final IdentifyFishCommand COMMAND = new IdentifyFishCommand(
        "base64img", "storage/key.jpg", 3, UUID.randomUUID(), UUID.randomUUID());

    private static AIPrediction aiPrediction(String scientific, String common, double confidence, int rank) {
        return new AIPrediction(scientific, common, confidence, rank,
            "LC", "Lake", "Fish", 80, "Desc", "Fun fact");
    }

    @Test
    void identify_mapsKnownSpeciesFromRepository() {
        Species walleye = Species.create("Sander vitreus", "Walleye", "Percidae",
            "LC", "Lake", "NA", "Desc", true);
        when(aiService.predict("base64img", 3)).thenReturn(new AIResult(
            List.of(aiPrediction("Sander vitreus", "Walleye", 0.91, 1)), true));
        when(speciesRepository.findByScientificName("Sander vitreus"))
            .thenReturn(Optional.of(walleye));

        IdentificationResult result = service.identify(COMMAND);

        assertThat(result.predictions()).hasSize(1);
        assertThat(result.predictions().get(0).getSpecies()).isSameAs(walleye);
        assertThat(result.predictions().get(0).getConfidence().getValue()).isEqualTo(0.91);
        assertThat(result.predictions().get(0).getRank()).isEqualTo(1);
        assertThat(result.uncertain()).isFalse();
        assertThat(result.isFish()).isTrue();
    }

    @Test
    void identify_createsFallbackSpeciesWhenUnknownToCatalog() {
        when(aiService.predict("base64img", 3)).thenReturn(new AIResult(
            List.of(aiPrediction("Esox masquinongy", "Muskellunge", 0.55, 1)), true));
        when(speciesRepository.findByScientificName("Esox masquinongy"))
            .thenReturn(Optional.empty());

        IdentificationResult result = service.identify(COMMAND);

        Species created = result.predictions().get(0).getSpecies();
        assertThat(created.getScientificName()).isEqualTo("Esox masquinongy");
        assertThat(created.getCommonName()).isEqualTo("Muskellunge");
        assertThat(created.getFamily()).isEqualTo("Unknown");
    }

    @Test
    void identify_publishesEventWithTopPrediction() {
        when(aiService.predict("base64img", 3)).thenReturn(new AIResult(
            List.of(
                aiPrediction("Sander vitreus", "Walleye", 0.91, 1),
                aiPrediction("Perca flavescens", "Yellow Perch", 0.05, 2)), true));
        when(speciesRepository.findByScientificName(anyString())).thenReturn(Optional.empty());

        service.identify(COMMAND);

        verify(eventPublisher).publishFishIdentified(
            any(UUID.class), eq(COMMAND.observationId()), eq(COMMAND.userId()),
            eq("Walleye"), eq(0.91), anyList(), eq("storage/key.jpg"));
    }

    @Test
    void identify_noPredictions_uncertainAndNoEvent() {
        when(aiService.predict("base64img", 3)).thenReturn(new AIResult(List.of(), false));

        IdentificationResult result = service.identify(COMMAND);

        assertThat(result.predictions()).isEmpty();
        assertThat(result.uncertain()).isTrue();
        assertThat(result.isFish()).isFalse();
        verify(eventPublisher, never()).publishFishIdentified(
            any(), any(), any(), anyString(), anyDouble(), anyList(), anyString());
    }

    @Test
    void identify_lowTopConfidence_flagsUncertain() {
        when(aiService.predict("base64img", 3)).thenReturn(new AIResult(
            List.of(aiPrediction("Sander vitreus", "Walleye", 0.12, 1)), true));
        when(speciesRepository.findByScientificName(anyString())).thenReturn(Optional.empty());

        assertThat(service.identify(COMMAND).uncertain()).isTrue();
    }
}
