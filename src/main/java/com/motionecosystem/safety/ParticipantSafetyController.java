package com.motionecosystem.safety;

import java.util.Set;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/safety/me")
@SecurityRequirement(name = "oidc")
class ParticipantSafetyController {

    private final ParticipantSafetyService safety;

    ParticipantSafetyController(ParticipantSafetyService safety) {
        this.safety = safety;
    }

    @GetMapping
    @Operation(summary = "Return only the authenticated participant's non-diagnostic safety inputs")
    ParticipantSafetyService.SafetyView current(@AuthenticationPrincipal Jwt jwt) {
        return safety.current(jwt.getSubject());
    }

    @PutMapping("/restrictions")
    ParticipantSafetyService.SafetyView restrictions(@AuthenticationPrincipal Jwt jwt,
                                                     @RequestBody RestrictionRequest request) {
        return safety.replaceRestrictions(jwt.getSubject(), request.contraindicationTags());
    }

    @PostMapping("/check-ins")
    ParticipantSafetyService.SafetyView checkIn(@AuthenticationPrincipal Jwt jwt,
                                                @RequestBody CheckInRequest request) {
        return safety.checkIn(
                jwt.getSubject(), request.painLevel(), request.readinessLevel(), request.painArea());
    }

    record RestrictionRequest(Set<String> contraindicationTags) {
    }

    record CheckInRequest(int painLevel, int readinessLevel, String painArea) {
    }
}
