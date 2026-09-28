package com.motionecosystem.application.workspace;

import com.motionecosystem.trainingexecution.SessionExecutionService;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.security.oauth2.jwt.Jwt;

/** Specialist task boundary: the appointment, never the browser, establishes session ownership. */
@RestController
@RequestMapping("/api/v1/specialist/appointments")
@RequiredArgsConstructor
class SpecialistAppointmentExecutionController {
    private final SpecialistAppointmentExecutionService execution;

    @GetMapping("/{appointmentId}/execution-context")
    SpecialistAppointmentExecutionService.SpecialistAppointmentExecutionContext context(@AuthenticationPrincipal Jwt jwt,
                                                                                          @PathVariable UUID appointmentId) {
        return execution.context(subject(jwt), appointmentId);
    }

    @PostMapping("/{appointmentId}/execution")
    SessionExecutionService.ExecutionView recordAppointmentExecution(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID appointmentId,
                                                                     @RequestHeader("Idempotency-Key") String key,
                                                                     @RequestBody SessionExecutionService.DeclareExecutionCommand command) {
        return execution.record(subject(jwt), appointmentId, key, command);
    }

    private static String subject(Jwt jwt) {
        if (jwt == null || jwt.getSubject() == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "authentication is required");
        return jwt.getSubject();
    }
}
