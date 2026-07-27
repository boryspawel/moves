package com.motionecosystem.specialist;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.Operation;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/v1/specialist/clients") @SecurityRequirement(name = "oidc") @RequiredArgsConstructor
public class SpecialistClientController {
    private final SpecialistClientService clients;
    @PostMapping @PreAuthorize("hasRole('SPECIALIST')")
    @Operation(summary = "Create a specialist client record")
    public SpecialistClientService.ClientView create(@AuthenticationPrincipal Jwt jwt, @RequestHeader("Idempotency-Key") String key, @RequestBody SpecialistClientService.ClientCommand command) { return clients.create(jwt.getSubject(), key, command); }
    @GetMapping @PreAuthorize("hasRole('SPECIALIST')") @Operation(summary = "List specialist client records") public List<SpecialistClientService.ClientView> list(@AuthenticationPrincipal Jwt jwt) { return clients.list(jwt.getSubject()); }
    @GetMapping("/{participantId}") @PreAuthorize("hasRole('SPECIALIST')") public SpecialistClientService.ClientView get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID participantId) { return clients.get(jwt.getSubject(), participantId); }
    @PatchMapping("/{participantId}") @PreAuthorize("hasRole('SPECIALIST')") public SpecialistClientService.ClientView update(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID participantId, @RequestBody SpecialistClientService.ClientCommand command) { return clients.update(jwt.getSubject(), participantId, command); }
    @PostMapping("/{participantId}/archive") @PreAuthorize("hasRole('SPECIALIST')") public SpecialistClientService.ClientView archive(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID participantId) { return clients.archive(jwt.getSubject(), participantId); }
}
