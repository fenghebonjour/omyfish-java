package com.omyfish.observation.adapter.in.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.omyfish.observation.adapter.in.web.dto.CreateObservationRequest;
import com.omyfish.observation.domain.exception.ObservationNotFoundException;
import com.omyfish.observation.domain.model.Observation;
import com.omyfish.observation.domain.model.valueobject.GpsCoordinates;
import com.omyfish.observation.domain.port.in.CreateObservationUseCase;
import com.omyfish.observation.domain.port.in.DeleteObservationUseCase;
import com.omyfish.observation.domain.port.in.GetObservationUseCase;
import com.omyfish.observation.domain.port.in.ListObservationsUseCase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ObservationController.class)
@TestPropertySource(properties = {
    "minio.endpoint=http://localhost:9000",
    "minio.access-key=test",
    "minio.secret-key=test",
    "minio.bucket=test",
    "spring.flyway.enabled=false",
    "spring.rabbitmq.host=localhost"
})
class ObservationControllerTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper objectMapper;
    @MockBean CreateObservationUseCase createUseCase;
    @MockBean GetObservationUseCase getUseCase;
    @MockBean ListObservationsUseCase listUseCase;
    @MockBean DeleteObservationUseCase deleteUseCase;

    private final UUID userId = UUID.randomUUID();

    @Test
    void createObservation_returns200WithResponse() throws Exception {
        Observation obs = Observation.create(userId, "Atlantic Salmon", "Salmo salar",
            0.92, "img.jpg", GpsCoordinates.unknown(), null, null);
        when(createUseCase.create(any())).thenReturn(obs);

        mvc.perform(post("/api/v1/observations")
                .header("X-User-Id", userId.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                    new CreateObservationRequest("Atlantic Salmon", "Salmo salar", 0.92, "img.jpg", null, null, null)
                )))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.speciesName").value("Atlantic Salmon"))
            .andExpect(jsonPath("$.topConfidence").value(0.92));
    }

    @Test
    void createObservation_missingUserIdHeader_returns400() throws Exception {
        mvc.perform(post("/api/v1/observations")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"speciesName\":\"Salmon\",\"scientificName\":\"Salmo salar\",\"topConfidence\":0.9,\"imageStorageKey\":\"img.jpg\"}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void getObservation_found_returns200() throws Exception {
        UUID id = UUID.randomUUID();
        Observation obs = Observation.create(userId, "Brown Trout", "Salmo trutta",
            0.80, "img.jpg", GpsCoordinates.of(51.5, -0.1), null, "river catch");
        when(getUseCase.get(id)).thenReturn(obs);

        mvc.perform(get("/api/v1/observations/{id}", id))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.speciesName").value("Brown Trout"))
            .andExpect(jsonPath("$.latitude").value(51.5));
    }

    @Test
    void getObservation_notFound_returns404() throws Exception {
        UUID id = UUID.randomUUID();
        when(getUseCase.get(id)).thenThrow(new ObservationNotFoundException(id));

        mvc.perform(get("/api/v1/observations/{id}", id))
            .andExpect(status().isNotFound());
    }

    @Test
    void listObservations_returns200WithList() throws Exception {
        Observation obs = Observation.create(userId, "Atlantic Salmon", "Salmo salar",
            0.92, "img.jpg", GpsCoordinates.unknown(), null, null);
        when(listUseCase.listByUser(userId)).thenReturn(List.of(obs));

        mvc.perform(get("/api/v1/observations")
                .header("X-User-Id", userId.toString()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].speciesName").value("Atlantic Salmon"))
            .andExpect(jsonPath("$.length()").value(1));
    }
}
