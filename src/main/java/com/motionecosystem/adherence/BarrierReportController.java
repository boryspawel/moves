package com.motionecosystem.adherence;

import com.motionecosystem.adherence.BarrierReportService.BarrierReportView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/participant/barrier-reports")
@SecurityRequirement(name = "oidc")
@RequiredArgsConstructor
class BarrierReportController {
    private final BarrierReportService reports;
    @PostMapping
    @PreAuthorize("hasRole('PARTICIPANT')")
    @Operation(summary = "Report a session barrier and receive deterministic, safe options")
    BarrierReportView report(@AuthenticationPrincipal Jwt jwt, @RequestHeader("Idempotency-Key") String idempotencyKey,
                             @RequestBody BarrierReportCommand command) {
        return reports.report(jwt.getSubject(), command, idempotencyKey);
    }
    record BarrierReportCommand(UUID plannedSessionId, UUID sessionAttemptId, String category, String selectedAction) { }
}
