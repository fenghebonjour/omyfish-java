package com.omyfish.species.adapter.in.web;

import com.omyfish.species.domain.model.Species;
import com.omyfish.species.domain.port.in.BrowseSpeciesUseCase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SpeciesController.class)
@TestPropertySource(properties = {
    "spring.flyway.enabled=false",
    "spring.rabbitmq.host=localhost",
    "omyfish.ai-service.url=http://localhost:8000",
    "minio.endpoint=http://localhost:9000",
    "minio.access-key=test",
    "minio.secret-key=test",
    "minio.bucket=test"
})
class SpeciesControllerTest {

    @Autowired MockMvc mvc;
    @MockBean BrowseSpeciesUseCase browseSpeciesUseCase;

    private static Species salmon() {
        return Species.create("Salmo salar", "Atlantic Salmon", "Salmonidae", "LC",
            "Rivers", "North Atlantic", "A salmon.", true);
    }

    @Test
    void listReturnsCatalog() throws Exception {
        when(browseSpeciesUseCase.list(null)).thenReturn(List.of(salmon()));

        mvc.perform(get("/api/v1/species"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].scientificName").value("Salmo salar"))
            .andExpect(jsonPath("$[0].commonName").value("Atlantic Salmon"))
            .andExpect(jsonPath("$[0].northAmericanFreshwater").value(true));
    }

    @Test
    void listPassesFreshwaterFilterThrough() throws Exception {
        when(browseSpeciesUseCase.list(false)).thenReturn(List.of());

        mvc.perform(get("/api/v1/species").param("northAmericanFreshwater", "false"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void getByScientificNameReturnsSpecies() throws Exception {
        when(browseSpeciesUseCase.byScientificName("Salmo salar")).thenReturn(Optional.of(salmon()));

        mvc.perform(get("/api/v1/species/{name}", "Salmo salar"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.family").value("Salmonidae"))
            .andExpect(jsonPath("$.habitat").value("Rivers"));
    }

    @Test
    void getUnknownScientificNameReturns404() throws Exception {
        when(browseSpeciesUseCase.byScientificName("Nope nope")).thenReturn(Optional.empty());

        mvc.perform(get("/api/v1/species/{name}", "Nope nope"))
            .andExpect(status().isNotFound());
    }
}
