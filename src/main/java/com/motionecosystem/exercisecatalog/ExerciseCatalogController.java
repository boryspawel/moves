package com.motionecosystem.exercisecatalog;

import java.util.UUID;

import com.motionecosystem.exercisecatalog.api.ExerciseCatalogQueryPort.PublishedExerciseVersionSnapshot;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/exercises")
@SecurityRequirement(name = "oidc")
class ExerciseCatalogController {

    private final CatalogService catalog;

    ExerciseCatalogController(CatalogService catalog) {
        this.catalog = catalog;
    }

    @GetMapping
    @Operation(summary = "Search published exercise versions using explicitly allowed filters")
    CatalogService.CatalogPage list(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) MovementPattern movementPattern,
            @RequestParam(required = false) TechnicalLevel technicalLevel,
            @RequestParam(required = false) String equipment,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return catalog.listPublished(query, movementPattern, technicalLevel, equipment, page, size);
    }

    @GetMapping("/versions/{versionId}")
    PublishedExerciseVersionSnapshot version(@PathVariable UUID versionId) {
        return catalog.published(versionId);
    }
}
