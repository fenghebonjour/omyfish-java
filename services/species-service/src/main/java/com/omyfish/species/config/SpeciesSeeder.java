package com.omyfish.species.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.omyfish.species.domain.model.Species;
import com.omyfish.species.domain.port.out.SpeciesRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.io.File;

// Seeds the species catalog from fish_info.json (same file the ai-service uses),
// mirroring the dotnet species-service startup seeding. Skipped when the path is
// unset/missing so local runs without the metadata volume still start.
@Component
public class SpeciesSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SpeciesSeeder.class);

    private final SpeciesRepository speciesRepository;
    private final String metadataPath;

    public SpeciesSeeder(
        SpeciesRepository speciesRepository,
        @Value("${omyfish.species.metadata-path:}") String metadataPath
    ) {
        this.speciesRepository = speciesRepository;
        this.metadataPath = metadataPath;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (metadataPath == null || metadataPath.isBlank()) return;
        File file = new File(metadataPath);
        if (!file.exists()) {
            log.warn("Species metadata file not found at {} — skipping seeding", metadataPath);
            return;
        }
        try {
            JsonNode entries = new ObjectMapper().readTree(file);
            int added = 0;
            for (JsonNode entry : entries) {
                String scientificName = entry.path("scientific_name").asText(null);
                String commonName = entry.path("species").asText(null);
                if (scientificName == null || commonName == null) continue;
                commonName = commonName.replace("_", " ");

                if (speciesRepository.findByScientificName(scientificName).isPresent()) continue;

                speciesRepository.save(Species.create(
                    scientificName,
                    commonName,
                    "Unknown",
                    entry.path("conservation_status").asText("Unknown"),
                    entry.path("habitat").asText(""),
                    entry.path("habitat").asText(""),
                    entry.path("description").asText(""),
                    false
                ));
                added++;
            }
            log.info("Species seeding complete from {} ({} added)", metadataPath, added);
        } catch (Exception e) {
            log.warn("Species seeding failed — continuing without seed data", e);
        }
    }
}
