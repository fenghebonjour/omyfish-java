package com.omyfish.species.adapter.in.web;

import com.omyfish.species.application.service.IdentificationService;
import com.omyfish.species.domain.model.Prediction;
import com.omyfish.species.domain.model.Species;
import com.omyfish.species.domain.model.valueobject.ConfidenceScore;
import com.omyfish.species.domain.port.in.IdentifyFishUseCase;
import com.omyfish.species.domain.port.out.StoragePort;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(IdentificationController.class)
@TestPropertySource(properties = {
    "spring.flyway.enabled=false",
    "spring.rabbitmq.host=localhost",
    "omyfish.ai-service.url=http://localhost:8000",
    "minio.endpoint=http://localhost:9000",
    "minio.access-key=test",
    "minio.secret-key=test",
    "minio.bucket=test"
})
class IdentificationControllerTest {

    @Autowired MockMvc mvc;
    @MockBean IdentifyFishUseCase identifyFishUseCase;
    @MockBean StoragePort storagePort;

    private static Prediction prediction(String commonName, double confidence, int rank) {
        Species species = Species.create("Salmo salar", commonName, "Salmonidae", "LC",
            "Rivers", "North Atlantic", "A salmon.", "Insects", 150, "Leaps waterfalls.", true);
        return Prediction.createRanked(species, "uploads/fish.jpg", ConfidenceScore.of(confidence), rank);
    }

    private static MockMultipartFile image() {
        return new MockMultipartFile("image", "fish.jpg", "image/jpeg",
            "fake-image-bytes".getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void identifyStoresImageAndReturnsRankedPredictions() throws Exception {
        when(storagePort.store(any(), anyLong(), anyString(), anyString())).thenReturn("uploads/fish.jpg");
        when(identifyFishUseCase.identify(any())).thenReturn(
            new IdentificationService.IdentificationResult(
                List.of(prediction("Atlantic Salmon", 0.91, 1), prediction("Brown Trout", 0.06, 2)),
                false, true));

        mvc.perform(multipart("/api/v1/species/identify").file(image()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.imageKey").value("uploads/fish.jpg"))
            .andExpect(jsonPath("$.uncertain").value(false))
            .andExpect(jsonPath("$.isFish").value(true))
            .andExpect(jsonPath("$.predictions[0].speciesName").value("Atlantic Salmon"))
            .andExpect(jsonPath("$.predictions[0].scientificName").value("Salmo salar"))
            .andExpect(jsonPath("$.predictions[0].confidence").value(0.91))
            .andExpect(jsonPath("$.predictions[0].rank").value(1))
            .andExpect(jsonPath("$.predictions[0].maxSizeCm").value(150))
            .andExpect(jsonPath("$.predictions[0].funFact").value("Leaps waterfalls."))
            .andExpect(jsonPath("$.predictions[1].speciesName").value("Brown Trout"));
    }

    @Test
    void identifyForwardsBase64ImageStorageKeyAndTopK() throws Exception {
        when(storagePort.store(any(), anyLong(), anyString(), anyString())).thenReturn("uploads/fish.jpg");
        when(identifyFishUseCase.identify(any())).thenReturn(
            new IdentificationService.IdentificationResult(List.of(), true, true));

        mvc.perform(multipart("/api/v1/species/identify").file(image()).param("topK", "3"))
            .andExpect(status().isOk());

        ArgumentCaptor<IdentificationService.IdentifyFishCommand> command =
            ArgumentCaptor.forClass(IdentificationService.IdentifyFishCommand.class);
        verify(identifyFishUseCase).identify(command.capture());
        assertThat(command.getValue().imageBase64()).isEqualTo(
            Base64.getEncoder().encodeToString("fake-image-bytes".getBytes(StandardCharsets.UTF_8)));
        assertThat(command.getValue().imageStorageKey()).isEqualTo("uploads/fish.jpg");
        assertThat(command.getValue().topK()).isEqualTo(3);
    }

    @Test
    void identifyDefaultsToTopFive() throws Exception {
        when(storagePort.store(any(), anyLong(), anyString(), anyString())).thenReturn("uploads/fish.jpg");
        when(identifyFishUseCase.identify(any())).thenReturn(
            new IdentificationService.IdentificationResult(List.of(), true, false));

        mvc.perform(multipart("/api/v1/species/identify").file(image()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.uncertain").value(true))
            .andExpect(jsonPath("$.isFish").value(false));

        ArgumentCaptor<IdentificationService.IdentifyFishCommand> command =
            ArgumentCaptor.forClass(IdentificationService.IdentifyFishCommand.class);
        verify(identifyFishUseCase).identify(command.capture());
        assertThat(command.getValue().topK()).isEqualTo(5);
    }

    @Test
    void identifyWithoutImagePartIsRejected() throws Exception {
        mvc.perform(multipart("/api/v1/species/identify"))
            .andExpect(status().isBadRequest());
    }
}
