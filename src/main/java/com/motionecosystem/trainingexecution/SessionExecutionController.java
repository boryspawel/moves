package com.motionecosystem.trainingexecution;

import java.util.List;
import java.util.UUID;

import com.motionecosystem.trainingexecution.SessionExecutionService.CorrectionCommand;
import com.motionecosystem.trainingexecution.SessionExecutionService.DeclareExecutionCommand;
import com.motionecosystem.trainingexecution.SessionExecutionService.ExecutionView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@SecurityRequirement(name = "oidc")
class SessionExecutionController {

    private final SessionExecutionService executions;

    SessionExecutionController(SessionExecutionService executions) {
        this.executions = executions;
    }

    @PostMapping("/planned-sessions/{sessionId}/executions")
    @PreAuthorize("hasRole('PARTICIPANT')")
    @Operation(summary = "Declare completion of an assigned planned session")
    ExecutionView declare(@AuthenticationPrincipal Jwt jwt,
                          @PathVariable UUID sessionId,
                          @RequestHeader("Idempotency-Key") String idempotencyKey,
                          @RequestBody DeclareExecutionCommand command) {
        return executions.declare(jwt.getSubject(), sessionId, idempotencyKey, command);
    }

    @PostMapping("/session-executions/{executionId}/corrections")
    @PreAuthorize("hasAnyRole('PARTICIPANT', 'SPECIALIST')")
    @Operation(summary = "Append an audited correction without changing execution history")
    ExecutionView correct(@AuthenticationPrincipal Jwt jwt,
                          @PathVariable UUID executionId,
                          @RequestBody CorrectionCommand command) {
        return executions.correct(jwt.getSubject(), executionId, command);
    }

    @GetMapping("/specialist/participants/{participantAccountId}/executions")
    @PreAuthorize("hasRole('SPECIALIST')")
    @Operation(summary = "List executions and alerts for a participant with an active relationship")
    List<ExecutionView> specialistExecutions(@AuthenticationPrincipal Jwt jwt,
                                             @PathVariable UUID participantAccountId) {
        return executions.specialistExecutions(jwt.getSubject(), participantAccountId);
    }
}
