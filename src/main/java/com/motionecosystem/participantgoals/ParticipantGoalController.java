package com.motionecosystem.participantgoals;

import com.motionecosystem.specialist.api.SpecialistAuthorizationPort.ActingContext;
import com.motionecosystem.specialist.api.SpecialistAuthorizationPort.ProfessionalRole;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/specialist/clients/{participantId}/goals")
@SecurityRequirement(name = "oidc")
@RequiredArgsConstructor
class ParticipantGoalController {
    private final ParticipantGoalService goals;
    @PostMapping @PreAuthorize("hasRole('SPECIALIST')") @Operation(operationId = "createParticipantGoal")
    ParticipantGoalService.ParticipantGoalView create(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID participantId, @RequestParam ProfessionalRole actingContext, @RequestHeader("Idempotency-Key") String key, @Valid @RequestBody CreateParticipantGoalRequest request) { return goals.create(jwt.getSubject(), participantId, new ActingContext(actingContext), key, request.toCommand()); }
    @GetMapping("/catalog") @PreAuthorize("hasRole('SPECIALIST')") @Operation(operationId = "listParticipantGoalMetricPresets")
    List<GoalMetricPresetCatalog.PresetView> catalog(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID participantId, @RequestParam ProfessionalRole actingContext) { return goals.catalog(jwt.getSubject(), participantId, new ActingContext(actingContext)); }
    @PostMapping("/from-preset") @PreAuthorize("hasRole('SPECIALIST')") @Operation(operationId = "createParticipantGoalFromPreset")
    ParticipantGoalService.ParticipantGoalView createFromPreset(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID participantId, @RequestParam ProfessionalRole actingContext, @RequestHeader("Idempotency-Key") String key, @Valid @RequestBody CreateFromPresetRequest request) { return goals.createFromPreset(jwt.getSubject(), participantId, new ActingContext(actingContext), key, request.toCommand()); }
    @GetMapping @PreAuthorize("hasRole('SPECIALIST')") @Operation(operationId = "listParticipantGoals")
    List<ParticipantGoalService.ParticipantGoalView> list(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID participantId, @RequestParam ProfessionalRole actingContext) { return goals.list(jwt.getSubject(), participantId, new ActingContext(actingContext)); }
    @GetMapping("/{goalId}") @PreAuthorize("hasRole('SPECIALIST')") @Operation(operationId = "getParticipantGoal")
    ParticipantGoalService.ParticipantGoalView get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID participantId, @PathVariable UUID goalId, @RequestParam ProfessionalRole actingContext) { return goals.detail(jwt.getSubject(), participantId, goalId, new ActingContext(actingContext)); }
    @PutMapping("/{goalId}") @PreAuthorize("hasRole('SPECIALIST')") @Operation(operationId = "updateParticipantGoal")
    ParticipantGoalService.ParticipantGoalView update(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID participantId, @PathVariable UUID goalId, @RequestParam ProfessionalRole actingContext, @RequestHeader("Idempotency-Key") String key, @Valid @RequestBody UpdateParticipantGoalRequest request) { return goals.update(jwt.getSubject(), participantId, goalId, new ActingContext(actingContext), key, request.toCommand()); }
    @PostMapping("/{goalId}/achieve") @PreAuthorize("hasRole('SPECIALIST')") @Operation(operationId = "achieveParticipantGoal")
    ParticipantGoalService.ParticipantGoalView achieve(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID participantId, @PathVariable UUID goalId, @RequestParam ProfessionalRole actingContext, @RequestHeader("Idempotency-Key") String key, @Valid @RequestBody ParticipantGoalVersionRequest request) { return goals.achieve(jwt.getSubject(), participantId, goalId, new ActingContext(actingContext), key, request.toCommand()); }
    @PostMapping("/{goalId}/cancel") @PreAuthorize("hasRole('SPECIALIST')") @Operation(operationId = "cancelParticipantGoal")
    ParticipantGoalService.ParticipantGoalView cancel(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID participantId, @PathVariable UUID goalId, @RequestParam ProfessionalRole actingContext, @RequestHeader("Idempotency-Key") String key, @Valid @RequestBody ParticipantGoalVersionRequest request) { return goals.cancel(jwt.getSubject(), participantId, goalId, new ActingContext(actingContext), key, request.toCommand()); }
    @PostMapping("/{goalId}/observations") @PreAuthorize("hasRole('SPECIALIST')") @Operation(operationId = "recordParticipantGoalObservation")
    ParticipantGoalService.ObservationResult observation(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID participantId, @PathVariable UUID goalId, @RequestParam ProfessionalRole actingContext, @RequestHeader("Idempotency-Key") String key, @Valid @RequestBody ParticipantGoalObservationRequest request) { return goals.recordObservation(jwt.getSubject(), participantId, goalId, new ActingContext(actingContext), key, request.toCommand()); }
    @GetMapping("/{goalId}/observations") @PreAuthorize("hasRole('SPECIALIST')") @Operation(operationId = "listParticipantGoalObservations")
    ParticipantGoalService.ObservationPage observations(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID participantId, @PathVariable UUID goalId, @RequestParam ProfessionalRole actingContext, @RequestParam(required = false) UUID outcomeId, @RequestParam(defaultValue = "50") int limit, @RequestParam(required = false) String cursor) { return goals.observationHistory(jwt.getSubject(), participantId, goalId, new ActingContext(actingContext), outcomeId, limit, cursor); }

    enum SpecialistGoalPerspective { PERFORMANCE, FUNCTIONAL_RECOVERY;
        ParticipantGoal.Category toCategory() { return this == PERFORMANCE ? ParticipantGoal.Category.PERFORMANCE : ParticipantGoal.Category.FUNCTIONAL; }
    }
    record CreateParticipantGoalRequest(
            @NotNull SpecialistGoalPerspective perspective,
            @NotBlank @Size(max = 160) String title,
            @Size(max = 2000) @Pattern(regexp = "(?s).*\\S.*") String description,
            @NotNull @jakarta.validation.constraints.Min(1) @jakarta.validation.constraints.Max(100) Integer priority,
            LocalDate targetDate,
            List<@Valid ParticipantGoalOutcomeRequest> outcomes) {
        ParticipantGoalService.CreateParticipantGoalCommand toCommand() {
            return new ParticipantGoalService.CreateParticipantGoalCommand(perspective.toCategory(), title, description, priority, targetDate,
                    outcomes == null ? List.of() : outcomes.stream().map(ParticipantGoalOutcomeRequest::toCommand).toList());
        }
    }
    record CreateFromPresetRequest(@NotNull GoalMetricPresetCatalog.PresetId presetId, String bodyArea, @Size(max = 120) String customLabel,
            @Size(max = 120) String exercise, @Size(max = 120) String activity, @NotNull BigDecimal baselineValue, @NotNull BigDecimal targetValue,
            @Size(max = 40) String unit, TargetComparator targetComparator, LocalDate targetDate, @Size(max = 2000) String description, @Size(max = 160) String titleOverride) {
        ParticipantGoalService.CreateFromPresetCommand toCommand() { return new ParticipantGoalService.CreateFromPresetCommand(presetId, bodyArea, customLabel, exercise, activity, baselineValue, targetValue, unit, targetComparator, targetDate, description, titleOverride); }
    }
    record UpdateParticipantGoalRequest(
            @NotNull @PositiveOrZero Long expectedVersion,
            @NotBlank @Size(max = 160) String title,
            @Size(max = 2000) @Pattern(regexp = "(?s).*\\S.*") String description,
            @NotNull @jakarta.validation.constraints.Min(1) @jakarta.validation.constraints.Max(100) Integer priority,
            LocalDate targetDate) {
        ParticipantGoalService.UpdateParticipantGoalCommand toCommand() { return new ParticipantGoalService.UpdateParticipantGoalCommand(expectedVersion, title, description, priority, targetDate); }
    }
    record ParticipantGoalVersionRequest(@NotNull @PositiveOrZero Long expectedVersion) {
        ParticipantGoalService.ParticipantGoalVersionCommand toCommand() { return new ParticipantGoalService.ParticipantGoalVersionCommand(expectedVersion); }
    }
    record ParticipantGoalOutcomeRequest(@NotBlank @Size(max = 80) String metricCode, @NotNull BigDecimal targetValue, @NotBlank @Size(max = 40) String unit, @Size(max = 120) String measurementMethod, @NotNull TargetComparator targetComparator) {
        ParticipantGoalService.OutcomeCommand toCommand() { return new ParticipantGoalService.OutcomeCommand(metricCode, targetValue, unit, measurementMethod, targetComparator); }
    }
    record ParticipantGoalObservationRequest(@NotNull UUID outcomeId, @NotNull BigDecimal value, @NotNull Instant measuredAt, @Size(max = 2000) String note, @Size(max = 160) String evidenceSource) {
        ParticipantGoalService.ObservationCommand toCommand() { return new ParticipantGoalService.ObservationCommand(outcomeId, value, measuredAt, null, note, evidenceSource); }
    }
}
