package com.motionecosystem.trainingplanning;

import com.motionecosystem.specialist.api.SpecialistAuthorizationPort.ActingContext;
import com.motionecosystem.specialist.api.SpecialistAuthorizationPort.ProfessionalRole;
import com.motionecosystem.trainingplanning.PlanCollaborationService.CollaboratorCommand;
import com.motionecosystem.trainingplanning.PlanCollaborationService.CollaboratorView;
import com.motionecosystem.trainingplanning.PlanCollaborationService.ReviewDecisionCommand;
import com.motionecosystem.trainingplanning.PlanCollaborationService.ReviewRequestCommand;
import com.motionecosystem.trainingplanning.PlanCollaborationService.ReviewView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v2/training-plans")
@SecurityRequirement(name = "oidc")
@RequiredArgsConstructor
class PlanCollaborationController {
    private final PlanCollaborationService collaboration;

    @PostMapping("/{planId}/collaborators")
    @PreAuthorize("hasAnyRole('PARTICIPANT', 'SPECIALIST')")
    @Operation(operationId = "addPlanCollaborator")
    CollaboratorView add(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID planId,
                         @RequestParam(required = false) ProfessionalRole actingContext,
                         @RequestBody CollaboratorCommand command) {
        return collaboration.addCollaborator(jwt.getSubject(), planId,
                actingContext == null ? null : new ActingContext(actingContext), command);
    }

    @DeleteMapping("/{planId}/collaborators/{collaboratorId}")
    @PreAuthorize("hasAnyRole('PARTICIPANT', 'SPECIALIST')")
    @Operation(operationId = "endPlanCollaboration")
    CollaboratorView end(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID planId,
                         @PathVariable UUID collaboratorId,
                         @RequestParam(required = false) ProfessionalRole actingContext) {
        return collaboration.endCollaborator(jwt.getSubject(), planId, collaboratorId,
                actingContext == null ? null : new ActingContext(actingContext));
    }

    @PostMapping("/revisions/{revisionId}/reviews")
    @PreAuthorize("hasAnyRole('PARTICIPANT', 'SPECIALIST')")
    @Operation(operationId = "requestPlanReview")
    ReviewView requestReview(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID revisionId,
                             @RequestBody ReviewRequestCommand command) {
        return collaboration.requestReview(jwt.getSubject(), revisionId, command);
    }

    @PostMapping("/reviews/{reviewId}/decision")
    @PreAuthorize("hasRole('SPECIALIST')")
    @Operation(operationId = "decidePlanReview")
    ReviewView decide(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID reviewId,
                      @RequestParam ProfessionalRole actingContext,
                      @RequestBody ReviewDecisionCommand command) {
        return collaboration.decideReview(jwt.getSubject(), reviewId,
                new ActingContext(actingContext), command);
    }
}
