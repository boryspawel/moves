package com.motionecosystem.trainingplanning;

import java.util.List;
import java.util.UUID;

import com.motionecosystem.trainingplanning.TrainingPlanningV2Persistence.RevisionHistoryItem;
import com.motionecosystem.trainingplanning.TrainingPlanningV2Service.AddCycleCommand;
import com.motionecosystem.trainingplanning.TrainingPlanningV2Service.AddGoalCommand;
import com.motionecosystem.trainingplanning.TrainingPlanningV2Service.AddLoadBudgetCommand;
import com.motionecosystem.trainingplanning.TrainingPlanningV2Service.AddMicrocycleCommand;
import com.motionecosystem.trainingplanning.TrainingPlanningV2Service.AddPrescriptionCommand;
import com.motionecosystem.trainingplanning.TrainingPlanningV2Service.AddSessionCommand;
import com.motionecosystem.trainingplanning.TrainingPlanningV2Service.CreateDraftCommand;
import com.motionecosystem.trainingplanning.TrainingPlanningV2Service.CreateRevisionCommand;
import com.motionecosystem.trainingplanning.TrainingPlanningV2Service.EditorView;
import com.motionecosystem.trainingplanning.TrainingPlanningV2Service.ReorderCommand;
import com.motionecosystem.trainingplanning.TrainingPlanningV2Service.StructuralValidationView;
import com.motionecosystem.trainingplanning.TrainingPlanningV2Service.ValidateCommand;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v2/training-plans")
@SecurityRequirement(name = "oidc")
@RequiredArgsConstructor
class TrainingPlanningV2Controller {

    private final TrainingPlanningV2Service planning;

    @PostMapping
    @PreAuthorize("hasAnyRole('PARTICIPANT', 'SPECIALIST')")
    @Operation(summary = "Create an inactive training plan draft")
    EditorView createDraft(@AuthenticationPrincipal Jwt jwt, @RequestBody CreateDraftCommand command) {
        return planning.createDraft(jwt.getSubject(), command);
    }

    @PostMapping("/revisions/{revisionId}/goals")
    @PreAuthorize("hasAnyRole('PARTICIPANT', 'SPECIALIST')")
    EditorView addGoal(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID revisionId,
                       @RequestBody AddGoalCommand command) {
        return planning.addGoal(jwt.getSubject(), revisionId, command);
    }

    @PostMapping("/revisions/{revisionId}/cycles")
    @PreAuthorize("hasAnyRole('PARTICIPANT', 'SPECIALIST')")
    EditorView addCycle(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID revisionId,
                        @RequestBody AddCycleCommand command) {
        return planning.addCycle(jwt.getSubject(), revisionId, command);
    }

    @PostMapping("/revisions/{revisionId}/microcycles")
    @PreAuthorize("hasAnyRole('PARTICIPANT', 'SPECIALIST')")
    EditorView addMicrocycle(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID revisionId,
                             @RequestBody AddMicrocycleCommand command) {
        return planning.addMicrocycle(jwt.getSubject(), revisionId, command);
    }

    @PostMapping("/revisions/{revisionId}/sessions")
    @PreAuthorize("hasAnyRole('PARTICIPANT', 'SPECIALIST')")
    EditorView addSession(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID revisionId,
                          @RequestBody AddSessionCommand command) {
        return planning.addSession(jwt.getSubject(), revisionId, command);
    }

    @PostMapping("/revisions/{revisionId}/prescriptions")
    @PreAuthorize("hasAnyRole('PARTICIPANT', 'SPECIALIST')")
    EditorView addPrescription(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID revisionId,
                               @RequestBody AddPrescriptionCommand command) {
        return planning.addPrescription(jwt.getSubject(), revisionId, command);
    }

    @PutMapping("/revisions/{revisionId}/prescriptions/order")
    @PreAuthorize("hasAnyRole('PARTICIPANT', 'SPECIALIST')")
    EditorView reorder(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID revisionId,
                       @RequestBody ReorderCommand command) {
        return planning.reorder(jwt.getSubject(), revisionId, command);
    }

    @PostMapping("/revisions/{revisionId}/load-budgets")
    @PreAuthorize("hasAnyRole('PARTICIPANT', 'SPECIALIST')")
    EditorView addLoadBudget(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID revisionId,
                             @RequestBody AddLoadBudgetCommand command) {
        return planning.addLoadBudget(jwt.getSubject(), revisionId, command);
    }

    @PostMapping("/{planId}/revisions")
    @PreAuthorize("hasAnyRole('PARTICIPANT', 'SPECIALIST')")
    EditorView createRevision(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID planId,
                              @RequestBody CreateRevisionCommand command) {
        return planning.createRevision(jwt.getSubject(), planId, command);
    }

    @PostMapping("/revisions/{revisionId}/structural-validation")
    @PreAuthorize("hasAnyRole('PARTICIPANT', 'SPECIALIST')")
    StructuralValidationView validateStructurally(@AuthenticationPrincipal Jwt jwt,
                                                   @PathVariable UUID revisionId,
                                                   @RequestBody ValidateCommand command) {
        return planning.validateStructurally(jwt.getSubject(), revisionId, command);
    }

    @GetMapping("/revisions/{revisionId}")
    @PreAuthorize("hasAnyRole('PARTICIPANT', 'SPECIALIST')")
    EditorView editor(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID revisionId) {
        return planning.editor(jwt.getSubject(), revisionId);
    }

    @GetMapping("/{planId}/revisions")
    @PreAuthorize("hasAnyRole('PARTICIPANT', 'SPECIALIST')")
    List<RevisionHistoryItem> history(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID planId) {
        return planning.history(jwt.getSubject(), planId);
    }
}
