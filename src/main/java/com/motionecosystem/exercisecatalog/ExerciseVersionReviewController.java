package com.motionecosystem.exercisecatalog;

import com.motionecosystem.exercisecatalog.api.PublishExerciseVersion;
import com.motionecosystem.exercisecatalog.api.ReviewExerciseVersion;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/exercise-versions")
@SecurityRequirement(name="oidc")
@PreAuthorize("hasAnyRole('CONTENT_ADMIN','SYSTEM_ADMIN')")
class ExerciseVersionReviewController {
    private final ExerciseEditorialWorkflowService workflow;
    ExerciseVersionReviewController(ExerciseEditorialWorkflowService workflow){this.workflow=workflow;}
    @PostMapping("/{id}/reviews") ReviewExerciseVersion.ReviewResult review(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID id,@RequestBody ReviewExerciseVersion.ReviewCommand request){return workflow.review(id,jwt.getSubject(),request);}
    @GetMapping("/{id}/reviews") ReviewExerciseVersion.ReviewResult reviews(@PathVariable UUID id){return workflow.status(id);}
    @GetMapping("/{id}/diff") ExerciseEditorialWorkflowService.VersionDiff diff(@PathVariable UUID id){return workflow.diff(id);}
    @PostMapping("/{id}/publish") PublishExerciseVersion.PublicationResult publish(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID id,@RequestBody(required=false) ExerciseVersionPublishRequest request){return workflow.publish(id,jwt.getSubject(),request==null?null:request.expectedVersion());}
    record ExerciseVersionPublishRequest(Long expectedVersion){}
}
