package com.omyfish.species.config;

import com.omyfish.species.domain.model.Species;
import com.omyfish.species.domain.port.out.SpeciesRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SpeciesSeederTest {

    @Mock SpeciesRepository speciesRepository;

    private Path metadataFile(Path dir, String json) throws IOException {
        Path file = dir.resolve("fish_info.json");
        Files.writeString(file, json);
        return file;
    }

    private void seed(String metadataPath) {
        new SpeciesSeeder(speciesRepository, metadataPath).run(null);
    }

    @Test
    void seedsSpeciesFromMetadataFile(@TempDir Path dir) throws IOException {
        Path file = metadataFile(dir, """
            [{"scientific_name": "Salmo salar", "species": "atlantic_salmon",
              "conservation_status": "LC", "habitat": "Rivers", "description": "A salmon."}]
            """);
        when(speciesRepository.findByScientificName(anyString())).thenReturn(Optional.empty());

        seed(file.toString());

        ArgumentCaptor<Species> saved = ArgumentCaptor.forClass(Species.class);
        verify(speciesRepository).save(saved.capture());
        assertThat(saved.getValue().getScientificName()).isEqualTo("Salmo salar");
        assertThat(saved.getValue().getCommonName()).isEqualTo("atlantic salmon");
        assertThat(saved.getValue().getConservationStatus()).isEqualTo("LC");
        assertThat(saved.getValue().getHabitat()).isEqualTo("Rivers");
        assertThat(saved.getValue().getDescription()).isEqualTo("A salmon.");
    }

    @Test
    void skipsSpeciesThatAlreadyExist(@TempDir Path dir) throws IOException {
        Path file = metadataFile(dir, """
            [{"scientific_name": "Salmo salar", "species": "atlantic_salmon"}]
            """);
        when(speciesRepository.findByScientificName("Salmo salar"))
            .thenReturn(Optional.of(Species.create("Salmo salar", "Atlantic Salmon",
                "Salmonidae", "LC", "Rivers", "North Atlantic", "A salmon.", true)));

        seed(file.toString());

        verify(speciesRepository, never()).save(any());
    }

    @Test
    void skipsEntriesMissingNames(@TempDir Path dir) throws IOException {
        Path file = metadataFile(dir, """
            [{"species": "mystery_fish"}, {"scientific_name": "Esox lucius"}]
            """);
        when(speciesRepository.findByScientificName(anyString())).thenReturn(Optional.empty());

        seed(file.toString());

        verify(speciesRepository, never()).save(any());
    }

    @Test
    void doesNothingWhenMetadataPathIsUnset() {
        seed("");

        verifyNoInteractions(speciesRepository);
    }

    @Test
    void doesNothingWhenMetadataFileIsMissing(@TempDir Path dir) {
        seed(dir.resolve("absent.json").toString());

        verifyNoInteractions(speciesRepository);
    }

    @Test
    void malformedMetadataDoesNotFailStartup(@TempDir Path dir) throws IOException {
        Path file = metadataFile(dir, "{ not json");

        seed(file.toString());

        verifyNoInteractions(speciesRepository);
    }

    @Test
    void seedsEveryEntryInTheFile(@TempDir Path dir) throws IOException {
        Path file = metadataFile(dir, """
            [{"scientific_name": "Salmo salar", "species": "atlantic_salmon"},
             {"scientific_name": "Esox lucius", "species": "northern_pike"}]
            """);
        when(speciesRepository.findByScientificName(anyString())).thenReturn(Optional.empty());

        seed(file.toString());

        ArgumentCaptor<Species> saved = ArgumentCaptor.forClass(Species.class);
        verify(speciesRepository, times(2)).save(saved.capture());
        assertThat(saved.getAllValues()).extracting(Species::getCommonName)
            .isEqualTo(List.of("atlantic salmon", "northern pike"));
    }
}
