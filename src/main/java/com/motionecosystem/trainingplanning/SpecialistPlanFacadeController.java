package com.motionecosystem.trainingplanning;

import com.motionecosystem.identityaccess.api.SpecialistAuthorizationPort.ActingContext;
import com.motionecosystem.trainingplanning.TrainingPlanningV2Persistence.RevisionHistoryItem;
import com.motionecosystem.trainingplanning.TrainingPlanningV2Service.CreateRevisionCommand;
import com.motionecosystem.trainingplanning.TrainingPlanningV2Service.EditorView;
import com.motionecosystem.trainingplanning.TrainingPlanningV2Service.PlanListItem;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v2/specialist/clients/{participantId}/plans")
@RequiredArgsConstructor
class SpecialistPlanFacadeController {
    private final SpecialistPlanFacadeService plans;

    @Operation(operationId = "listSpecialistPlans")
    @GetMapping @PreAuthorize("hasRole('SPECIALIST')")
    List<PlanListItem> list(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID participantId,
                            @RequestParam com.motionecosystem.identityaccess.api.SpecialistAuthorizationPort.ProfessionalRole role) { return plans.list(jwt.getSubject(), participantId, new ActingContext(role)); }
    @Operation(operationId = "createSpecialistPlan")
    @PostMapping @PreAuthorize("hasRole('SPECIALIST')")
    EditorView create(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID participantId, @RequestBody SpecialistPlanFacadeService.CreatePlanCommand command) { return plans.create(jwt.getSubject(), participantId, command); }
    @Operation(operationId = "readSpecialistPlanRevision")
    @GetMapping("/{planId}/revisions/{revisionId}") @PreAuthorize("hasRole('SPECIALIST')")
    EditorView editor(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID participantId, @PathVariable UUID planId, @PathVariable UUID revisionId) { return plans.editor(jwt.getSubject(), participantId, planId, revisionId); }
    @Operation(operationId = "listSpecialistPlanRevisions")
    @GetMapping("/{planId}/revisions") @PreAuthorize("hasRole('SPECIALIST')")
    List<RevisionHistoryItem> history(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID participantId, @PathVariable UUID planId) { return plans.history(jwt.getSubject(), participantId, planId); }
    @Operation(operationId = "createSpecialistPlanRevision")
    @PostMapping("/{planId}/revisions") @PreAuthorize("hasRole('SPECIALIST')")
    EditorView createRevision(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID participantId, @PathVariable UUID planId, @RequestBody CreateRevisionCommand command) { return plans.createRevision(jwt.getSubject(), participantId, planId, command); }
    @Operation(operationId = "addSpecialistPlanSession")
    @PostMapping("/{planId}/revisions/{revisionId}/sessions") @PreAuthorize("hasRole('SPECIALIST')")
    EditorView addSession(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID participantId, @PathVariable UUID planId, @PathVariable UUID revisionId, @RequestBody SpecialistPlanFacadeService.SessionCommand command) { return plans.addSession(jwt.getSubject(), participantId, planId, revisionId, command); }
    @Operation(operationId = "updateSpecialistPlanPeriod")
    @PutMapping("/{planId}/revisions/{revisionId}/period") @PreAuthorize("hasRole('SPECIALIST')")
    EditorView updatePeriod(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID participantId, @PathVariable UUID planId, @PathVariable UUID revisionId, @RequestBody SpecialistPlanFacadeService.PeriodCommand command) { return plans.updatePeriod(jwt.getSubject(), participantId, planId, revisionId, command); }
    @Operation(operationId = "updateSpecialistPlanSession")
    @PutMapping("/{planId}/revisions/{revisionId}/sessions") @PreAuthorize("hasRole('SPECIALIST')")
    EditorView updateSession(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID participantId, @PathVariable UUID planId, @PathVariable UUID revisionId, @RequestBody SpecialistPlanFacadeService.SessionUpdateCommand command) { return plans.updateSession(jwt.getSubject(), participantId, planId, revisionId, command); }
    @Operation(operationId = "deleteSpecialistPlanSession")
    @DeleteMapping("/{planId}/revisions/{revisionId}/sessions/{sessionId}") @PreAuthorize("hasRole('SPECIALIST')")
    EditorView deleteSession(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID participantId, @PathVariable UUID planId,
                             @PathVariable UUID revisionId, @PathVariable UUID sessionId, @RequestParam long expectedVersion) {
        return plans.deleteSession(jwt.getSubject(), participantId, planId, revisionId, expectedVersion, sessionId);
    }
}
