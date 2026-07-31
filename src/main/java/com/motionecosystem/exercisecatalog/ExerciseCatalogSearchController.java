package com.motionecosystem.exercisecatalog;

import java.util.UUID;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** V2 picker API. It intentionally returns a projection, never catalog entities. */
@RestController
@RequestMapping("/api/v2/exercises")
@SecurityRequirement(name = "oidc")
public class ExerciseCatalogSearchController {
    private final ExerciseCatalogSearchService search;

    public ExerciseCatalogSearchController(ExerciseCatalogSearchService search) {
        this.search = search;
    }

    @PostMapping("/search")
    @Operation(summary = "Search currently selectable published exercise versions")
    public ExerciseCatalogSearchService.SearchPage search(@Valid @RequestBody ExerciseCatalogSearchService.SearchRequest request) {
        return search.search(request);
    }

    @GetMapping("/versions/{exerciseVersionId}/preview")
    @Operation(summary = "Read a lightweight preview of one selectable published exercise version")
    public ExerciseCatalogSearchService.Preview preview(@PathVariable UUID exerciseVersionId) {
        return search.preview(exerciseVersionId);
    }
}
