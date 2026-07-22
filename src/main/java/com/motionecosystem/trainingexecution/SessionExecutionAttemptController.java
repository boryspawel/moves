package com.motionecosystem.trainingexecution;

import com.motionecosystem.trainingexecution.SessionExecutionAttemptService.AttemptView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/participant/session-attempts")
@SecurityRequirement(name = "oidc")
@RequiredArgsConstructor
class SessionExecutionAttemptController {
    private final SessionExecutionAttemptService attempts;
    private final SessionExecutionService executions;

    @PostMapping
    @PreAuthorize("hasRole('PARTICIPANT')")
    @Operation(summary = "Start or return the participant's active session attempt")
    AttemptView start(@AuthenticationPrincipal Jwt jwt, @RequestHeader("Idempotency-Key") String idempotencyKey,
                      @RequestBody StartAttemptCommand command) {
        return attempts.start(jwt.getSubject(), command.plannedSessionId(), command.planRevisionId(), command.selectedVariantType(), idempotencyKey);
    }

    @PostMapping("/{attemptId}/pause")
    @PreAuthorize("hasRole('PARTICIPANT')")
    AttemptView pause(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID attemptId) {
        return attempts.pause(jwt.getSubject(), attemptId);
    }

    @PostMapping("/{attemptId}/resume")
    @PreAuthorize("hasRole('PARTICIPANT')")
    AttemptView resume(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID attemptId) {
        return attempts.resume(jwt.getSubject(), attemptId);
    }

    @PostMapping("/{attemptId}/abandon")
    @PreAuthorize("hasRole('PARTICIPANT')")
    AttemptView abandon(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID attemptId,
                        @RequestBody(required = false) AbandonAttemptCommand command) {
        return attempts.abandon(jwt.getSubject(), attemptId, command == null ? null : command.reasonCode());
    }

    @PutMapping("/{attemptId}/progress")
    @PreAuthorize("hasRole(PARTICIPANT)")
    AttemptView progress(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID attemptId,
                         @RequestBody ProgressCommand command) {
        return attempts.updateProgress(jwt.getSubject(), attemptId, command.exercisePrescriptionId(), command.completed());
    }

    @GetMapping("/{attemptId}")
    @PreAuthorize("hasRole(PARTICIPANT)")
    SessionExecutionAttemptService.AttemptDetailView get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID attemptId) {
        return attempts.get(jwt.getSubject(), attemptId);
    }

    @PostMapping("/{attemptId}/complete")
    @PreAuthorize("hasRole(PARTICIPANT)")
    SessionExecutionService.ExecutionView complete(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID attemptId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestBody SessionExecutionService.DeclareExecutionCommand command) {
        if (command == null || command.techniqueConfidenceLevel() == null) {
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST,
                    "techniqueConfidenceLevel is required for guided completion");
        }
        attempts.validateCompletionResults(jwt.getSubject(), attemptId, command);
        return executions.declare(jwt.getSubject(), attempts.plannedSessionForCompletion(jwt.getSubject(), attemptId),
                idempotencyKey, command);
    }

    record StartAttemptCommand(UUID plannedSessionId, UUID planRevisionId, String selectedVariantType) { }
    record ProgressCommand(UUID exercisePrescriptionId, boolean completed) { }
    record AbandonAttemptCommand(String reasonCode) { }
}
