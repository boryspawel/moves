package com.motionecosystem.trainingplanning;

import com.motionecosystem.trainingplanning.TrainingPlanningV2Service.EditorView;
import com.motionecosystem.trainingplanning.TrainingPlanningV2Service.PlanListItem;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Own-participant entry points; identity is resolved from the active account link. */
@RestController
@RequestMapping("/api/v2/participant/plans")
@SecurityRequirement(name = "oidc")
@RequiredArgsConstructor
class ParticipantPlanFacadeController {
    private final TrainingPlanningV2Service planning;
    private final SpecialistPlanFacadeService practicalPlans;

    @GetMapping
    @PreAuthorize("hasRole('PARTICIPANT')")
    @Operation(operationId = "listOwnParticipantPlans")
    List<PlanListItem> list(@AuthenticationPrincipal Jwt jwt) {
        return planning.ownParticipantPlans(jwt.getSubject());
    }

    @PostMapping
    @PreAuthorize("hasRole('PARTICIPANT')")
    @Operation(operationId = "createOwnParticipantPlan")
    EditorView create(@AuthenticationPrincipal Jwt jwt,
                      @RequestBody SpecialistPlanFacadeService.OwnCreatePlanCommand command) {
        return practicalPlans.createOwn(jwt.getSubject(), command);
    }
}
