package com.motionecosystem.exercisecatalog;

import com.motionecosystem.exercisecatalog.api.PublishExerciseVersion;
import com.motionecosystem.exercisecatalog.api.ReviewExerciseVersion;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.springframework.jdbc.core.JdbcTemplate;
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
    private final JdbcTemplate jdbc;

    ExerciseVersionReviewController(ExerciseEditorialWorkflowService workflow, ExerciseReviewQueryService reviewQueries, JdbcTemplate jdbc) {
        this.workflow = workflow;
        this.reviewQueries = reviewQueries;
        this.jdbc = jdbc;
    }
    @PostMapping("/{id}/reviews") ReviewExerciseVersion.ReviewResult review(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID id,@RequestBody ReviewExerciseVersion.ReviewCommand request){return workflow.review(id,jwt.getSubject(),request);}
    @GetMapping("/{id}/reviews") ReviewExerciseVersion.ReviewResult reviews(@PathVariable UUID id){return workflow.status(id);}

    @GetMapping("/review-queue")
    List<ReviewQueueItem> reviewQueue() {
        return jdbc.query("""
                SELECT version.id,exercise.canonical_name FROM exercise_catalog.exercise_version version
                JOIN exercise_catalog.exercise exercise ON exercise.id=version.exercise_id
                WHERE version.status='DRAFT' ORDER BY version.created_at,version.id
                """, (rs, n) -> {
            UUID id = rs.getObject(1, UUID.class);
            var review = workflow.status(id);
            return new ReviewQueueItem(id, rs.getString(2), review.status(), review.unmetRequirements());
        });
    }
    @GetMapping("/{id}/diff") ExerciseEditorialWorkflowService.VersionDiff diff(@PathVariable UUID id){return workflow.diff(id);}
    @PostMapping("/{id}/publish") PublishExerciseVersion.PublicationResult publish(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID id,@RequestBody(required=false) ExerciseVersionPublishRequest request){return workflow.publish(id,jwt.getSubject(),request==null?null:request.expectedVersion());}
    record ExerciseVersionPublishRequest(Long expectedVersion){}

    record ReviewQueueItem(UUID exerciseVersionId, String exerciseName, String status, List<String> unmetRequirements) {
    }
}
