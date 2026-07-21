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
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

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
    @Operation(summary = "Deprecated V1 plan creation endpoint", deprecated = true)
    PlanBundle create(@AuthenticationPrincipal Jwt jwt, @RequestBody CreatePlanCommand command) {
        throw new ResponseStatusException(HttpStatus.GONE,
                "V1 active-plan creation was retired; use the V2 draft and revision workflow");
    }

    @GetMapping("/planned-sessions")
    @PreAuthorize("hasRole('PARTICIPANT')")
    @Operation(summary = "List planned sessions assigned to the current participant")
    List<SessionView> sessions(@AuthenticationPrincipal Jwt jwt) {
        return planning.participantSessions(jwt.getSubject());
    }
}
