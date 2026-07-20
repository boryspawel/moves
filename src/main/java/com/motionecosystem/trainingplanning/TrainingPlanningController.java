package com.motionecosystem.trainingplanning;

import java.util.List;

import com.motionecosystem.trainingplanning.TrainingPlanningService.CreatePlanCommand;
import com.motionecosystem.trainingplanning.TrainingPlanningService.PlanBundle;
import com.motionecosystem.trainingplanning.TrainingPlanningService.SessionView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@SecurityRequirement(name = "oidc")
class TrainingPlanningController {

    private final TrainingPlanningService planning;

    TrainingPlanningController(TrainingPlanningService planning) {
        this.planning = planning;
    }

    @PostMapping("/training-plans")
    @PreAuthorize("hasRole('SPECIALIST')")
    @Operation(summary = "Create and assign a simple specialist-authored training plan")
    PlanBundle create(@AuthenticationPrincipal Jwt jwt, @RequestBody CreatePlanCommand command) {
        return planning.createSpecialistPlan(jwt.getSubject(), command);
    }

    @GetMapping("/planned-sessions")
    @PreAuthorize("hasRole('PARTICIPANT')")
    @Operation(summary = "List planned sessions assigned to the current participant")
    List<SessionView> sessions(@AuthenticationPrincipal Jwt jwt) {
        return planning.participantSessions(jwt.getSubject());
    }
}
