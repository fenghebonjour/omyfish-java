package com.omyfish.species.application.service;

import com.omyfish.species.domain.model.Species;
import com.omyfish.species.domain.port.out.SpeciesRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SpeciesCatalogServiceTest {

    @Mock SpeciesRepository repository;
    @InjectMocks SpeciesCatalogService service;

    private static Species species(String scientificName, boolean naFreshwater) {
        return Species.create(scientificName, scientificName, "Family",
            "LC", "Lake", "Range", "Desc", naFreshwater);
    }

    @Test
    void list_withoutFilter_returnsAll() {
        when(repository.findAll()).thenReturn(List.of(
            species("Sander vitreus", true), species("Thunnus thynnus", false)));

        assertThat(service.list(null)).hasSize(2);
    }

    @Test
    void list_filtersByNorthAmericanFreshwater() {
        when(repository.findAll()).thenReturn(List.of(
            species("Sander vitreus", true), species("Thunnus thynnus", false)));

        List<Species> freshwater = service.list(true);
        assertThat(freshwater).hasSize(1);
        assertThat(freshwater.get(0).getScientificName()).isEqualTo("Sander vitreus");

        List<Species> other = service.list(false);
        assertThat(other).hasSize(1);
        assertThat(other.get(0).getScientificName()).isEqualTo("Thunnus thynnus");
    }

    @Test
    void byScientificName_delegatesToRepository() {
        Species walleye = species("Sander vitreus", true);
        when(repository.findByScientificName("Sander vitreus")).thenReturn(Optional.of(walleye));

        assertThat(service.byScientificName("Sander vitreus")).contains(walleye);
        assertThat(service.byScientificName("Sander vitreus").get().getCommonName())
            .isEqualTo("Sander vitreus");
    }
}
