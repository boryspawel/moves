package com.motionecosystem.exercisecatalog;

import com.motionecosystem.exercisecatalog.api.PublishExerciseVersion;
import com.motionecosystem.exercisecatalog.api.ReviewExerciseVersion;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/exercise-versions")
@SecurityRequirement(name="oidc")
@PreAuthorize("hasAnyRole('CONTENT_ADMIN','SYSTEM_ADMIN')")
class ExerciseVersionReviewController {
    private final ExerciseEditorialWorkflowService workflow;
    private final ExerciseReviewQueryService reviewQueries;
    private final ExerciseVersionRepository versions;
    private final ExerciseRepository exercises;

    ExerciseVersionReviewController(ExerciseEditorialWorkflowService workflow, ExerciseReviewQueryService reviewQueries,
                                    ExerciseVersionRepository versions, ExerciseRepository exercises) {
        this.workflow = workflow;
        this.reviewQueries = reviewQueries;
        this.versions = versions;
        this.exercises = exercises;
    }
    @PostMapping("/{id}/reviews") ReviewExerciseVersion.ReviewResult review(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID id,@RequestBody ReviewExerciseVersion.ReviewCommand request){return workflow.review(id,jwt.getSubject(),request);}
    @GetMapping("/{id}/reviews") ReviewExerciseVersion.ReviewResult reviews(@PathVariable UUID id){return workflow.status(id);}

    @GetMapping("/review-queue")
    List<ReviewQueueItem> reviewQueue() {
        return versions.findByStatusOrderByCreatedAtAscIdAsc(ExerciseVersionStatus.DRAFT).stream().map(version -> {
            var review = workflow.status(version.id);
            String name = exercises.findById(version.exerciseId).map(item -> item.canonicalName).orElse("");
            return new ReviewQueueItem(version.id, name, review.status(), review.unmetRequirements());
        }).toList();
    }
    @GetMapping("/{id}/diff") ExerciseEditorialWorkflowService.VersionDiff diff(@PathVariable UUID id){return workflow.diff(id);}
    @PostMapping("/{id}/publish") PublishExerciseVersion.PublicationResult publish(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID id,@Valid @RequestBody ExerciseVersionPublishRequest request){return workflow.publish(id,jwt.getSubject(),request.expectedVersion());}
    record ExerciseVersionPublishRequest(@NotNull @PositiveOrZero Long expectedVersion){}

    record ReviewQueueItem(UUID exerciseVersionId, String exerciseName, String status, List<String> unmetRequirements) {
    }
}
