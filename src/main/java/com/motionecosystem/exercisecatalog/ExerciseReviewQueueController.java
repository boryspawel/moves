package com.motionecosystem.exercisecatalog;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Admin read endpoint for the editorial queue; commands stay on catalog/workflow controllers.
 */
@RestController
@RequestMapping("/api/v1/admin/exercise-review")
@SecurityRequirement(name = "oidc")
@PreAuthorize("hasAnyRole('CONTENT_ADMIN','SYSTEM_ADMIN')")
@RequiredArgsConstructor
class ExerciseReviewQueueController {
    private final ExerciseReviewQueryService reviewQueries;

    @GetMapping("/items")
    ExerciseReviewQueryService.QueuePage items(@RequestParam(required = false) UUID batchId,
                                               @RequestParam(required = false) String query, @RequestParam(required = false) String status,
                                               @RequestParam(required = false) Boolean readyToPublish, @RequestParam(required = false) Boolean hasErrors,
                                               @RequestParam(required = false) Boolean hasBlockers, @RequestParam(defaultValue = "0") int page,
                                               @RequestParam(defaultValue = "25") int size) {
        return reviewQueries.items(batchId, query, status, readyToPublish, hasErrors, hasBlockers, page, size);
    }
}
