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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/participant/session-attempts")
@SecurityRequirement(name = "oidc")
@RequiredArgsConstructor
class SessionExecutionAttemptController {
    private final SessionExecutionAttemptService attempts;

    @PostMapping
    @PreAuthorize("hasRole('PARTICIPANT')")
    @Operation(summary = "Start or return the participant's active session attempt")
    AttemptView start(@AuthenticationPrincipal Jwt jwt, @RequestBody StartAttemptCommand command) {
        return attempts.start(jwt.getSubject(), command.plannedSessionId(), command.planRevisionId());
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
    AttemptView abandon(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID attemptId) {
        return attempts.abandon(jwt.getSubject(), attemptId);
    }

    record StartAttemptCommand(UUID plannedSessionId, UUID planRevisionId) { }
}
