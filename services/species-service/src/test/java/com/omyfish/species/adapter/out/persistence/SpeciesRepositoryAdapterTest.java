package com.omyfish.species.adapter.out.persistence;

import com.omyfish.species.domain.model.Species;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SpeciesRepositoryAdapterTest {

    @Mock SpeciesJpaRepository jpaRepository;

    private static Species salmon() {
        return Species.create("Salmo salar", "Atlantic Salmon", "Salmonidae", "LC",
            "Rivers", "North Atlantic", "A salmon.", true);
    }

    @Test
    void findByScientificNameMapsEntityToDomain() {
        when(jpaRepository.findByScientificName("Salmo salar"))
            .thenReturn(Optional.of(SpeciesJpaEntity.from(salmon())));

        Optional<Species> found = adapter().findByScientificName("Salmo salar");

        assertThat(found).get().satisfies(s -> {
            assertThat(s.getScientificName()).isEqualTo("Salmo salar");
            assertThat(s.getCommonName()).isEqualTo("Atlantic Salmon");
            assertThat(s.getFamily()).isEqualTo("Salmonidae");
            assertThat(s.getConservationStatus()).isEqualTo("LC");
            assertThat(s.getHabitat()).isEqualTo("Rivers");
            assertThat(s.getGeographicRange()).isEqualTo("North Atlantic");
            assertThat(s.getDescription()).isEqualTo("A salmon.");
            assertThat(s.isNorthAmericanFreshwater()).isTrue();
        });
    }

    @Test
    void findByScientificNameReturnsEmptyWhenUnknown() {
        when(jpaRepository.findByScientificName("Nope nope")).thenReturn(Optional.empty());

        assertThat(adapter().findByScientificName("Nope nope")).isEmpty();
    }

    @Test
    void saveMapsDomainToEntityAndBack() {
        Species species = salmon();
        when(jpaRepository.save(any(SpeciesJpaEntity.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        Species saved = adapter().save(species);

        ArgumentCaptor<SpeciesJpaEntity> entity = ArgumentCaptor.forClass(SpeciesJpaEntity.class);
        verify(jpaRepository).save(entity.capture());
        assertThat(entity.getValue().getId()).isEqualTo(species.getId());
        assertThat(entity.getValue().getScientificName()).isEqualTo("Salmo salar");
        assertThat(saved.getCommonName()).isEqualTo("Atlantic Salmon");
    }

    @Test
    void findByIdMapsEntityToDomain() {
        UUID id = UUID.randomUUID();
        when(jpaRepository.findById(id)).thenReturn(Optional.of(SpeciesJpaEntity.from(salmon())));

        assertThat(adapter().findById(id)).get()
            .satisfies(s -> assertThat(s.getCommonName()).isEqualTo("Atlantic Salmon"));
    }

    @Test
    void findAllMapsEveryEntity() {
        when(jpaRepository.findAll()).thenReturn(List.of(
            SpeciesJpaEntity.from(salmon()),
            SpeciesJpaEntity.from(Species.create("Esox lucius", "Northern Pike", "Esocidae", "LC",
                "Lakes", "Holarctic", "A pike.", true))));

        assertThat(adapter().findAll())
            .extracting(Species::getCommonName)
            .containsExactly("Atlantic Salmon", "Northern Pike");
    }

    private SpeciesRepositoryAdapter adapter() {
        return new SpeciesRepositoryAdapter(jpaRepository);
    }
}
