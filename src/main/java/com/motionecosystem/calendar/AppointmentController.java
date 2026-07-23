package com.motionecosystem.calendar;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.Operation;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/specialist/appointments")
@SecurityRequirement(name = "oidc")
@RequiredArgsConstructor
public class AppointmentController {
    private final AppointmentService appointments;
    @PostMapping @PreAuthorize("hasRole('SPECIALIST')") @Operation(summary = "Create a specialist appointment")
    AppointmentService.AppointmentView create(@AuthenticationPrincipal Jwt jwt, @RequestHeader("Idempotency-Key") String key, @RequestBody AppointmentService.CreateCommand command) { return appointments.create(jwt.getSubject(), key, command); }
    @PutMapping("/{id}") @PreAuthorize("hasRole('SPECIALIST')") @Operation(summary = "Update a specialist appointment")
    AppointmentService.AppointmentView update(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id, @RequestHeader("Idempotency-Key") String key, @RequestBody AppointmentService.UpdateCommand command) { return appointments.update(jwt.getSubject(), id, key, command); }
    @PostMapping("/{id}/cancel") @PreAuthorize("hasRole('SPECIALIST')") @Operation(summary = "Cancel a specialist appointment")
    AppointmentService.AppointmentView cancel(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id, @RequestHeader("Idempotency-Key") String key, @RequestBody AppointmentService.VersionCommand command) { return appointments.cancel(jwt.getSubject(), id, key, command); }
    @PostMapping("/{id}/no-show") @PreAuthorize("hasRole('SPECIALIST')") @Operation(summary = "Mark a specialist appointment as no-show")
    AppointmentService.AppointmentView noShow(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id, @RequestHeader("Idempotency-Key") String key, @RequestBody AppointmentService.VersionCommand command) { return appointments.noShow(jwt.getSubject(), id, key, command); }
}
