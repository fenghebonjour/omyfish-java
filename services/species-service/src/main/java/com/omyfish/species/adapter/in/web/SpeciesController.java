package com.omyfish.species.adapter.in.web;

import com.omyfish.species.adapter.in.web.dto.SpeciesResponse;
import com.omyfish.species.domain.port.in.BrowseSpeciesUseCase;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/species")
public class SpeciesController {

    private final BrowseSpeciesUseCase browseSpeciesUseCase;

    public SpeciesController(BrowseSpeciesUseCase browseSpeciesUseCase) {
        this.browseSpeciesUseCase = browseSpeciesUseCase;
    }

    @GetMapping
    public ResponseEntity<List<SpeciesResponse>> list(
        @RequestParam(value = "northAmericanFreshwater", required = false) Boolean northAmericanFreshwater
    ) {
        List<SpeciesResponse> species = browseSpeciesUseCase.list(northAmericanFreshwater)
            .stream().map(SpeciesResponse::from).toList();
        return ResponseEntity.ok(species);
    }

    @GetMapping("/{scientificName}")
    public ResponseEntity<SpeciesResponse> get(@PathVariable("scientificName") String scientificName) {
        return browseSpeciesUseCase.byScientificName(scientificName)
            .map(s -> ResponseEntity.ok(SpeciesResponse.from(s)))
            .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
