package com.motionecosystem.specialist;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/specialist/participants")
@SecurityRequirement(name = "oidc")
@RequiredArgsConstructor
public class SpecialistRelationshipController {
    private final SpecialistRelationshipService relationships;

    @GetMapping
    @PreAuthorize("hasRole('SPECIALIST')")
    @Operation(summary = "List participants with an active specialist relationship for UI selection")
    List<SpecialistRelationshipService.ActiveParticipantView> activeParticipants(@AuthenticationPrincipal Jwt jwt) {
        return relationships.activeParticipants(jwt.getSubject());
    }
}
