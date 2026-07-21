package com.motionecosystem.safety;

import com.motionecosystem.identityaccess.api.CurrentAccountService;
import com.motionecosystem.safety.SafetyV2Service.OverrideCommand;
import com.motionecosystem.safety.SafetyV2Service.OverrideView;
import com.motionecosystem.safety.SafetyV2Service.RestrictionCommand;
import com.motionecosystem.safety.SafetyV2Service.RestrictionView;
import com.motionecosystem.specialist.api.SpecialistAuthorizationPort.ActingContext;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v2/safety")
@SecurityRequirement(name = "oidc")
@RequiredArgsConstructor
class SafetyV2Controller {

    private final SafetyV2Service safety;
    private final CurrentAccountService accounts;

    @PostMapping("/me/restrictions")
    @PreAuthorize("hasRole('PARTICIPANT')")
    RestrictionView declare(
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody RestrictionCommand command) {
        return safety.declareParticipantRestriction(jwt.getSubject(), command);
    }

    @PatchMapping("/me/restrictions/{restrictionId}")
    @PreAuthorize("hasRole('PARTICIPANT')")
    RestrictionView revise(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID restrictionId,
            @RequestBody RestrictionCommand command) {
        return safety.reviseParticipantRestriction(jwt.getSubject(), restrictionId, command);
    }

    @DeleteMapping("/me/restrictions/{restrictionId}")
    @PreAuthorize("hasRole('PARTICIPANT')")
    RestrictionView withdraw(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID restrictionId) {
        return safety.withdrawParticipantRestriction(jwt.getSubject(), restrictionId);
    }

    @PostMapping("/participants/{participantId}/restrictions")
    @PreAuthorize("hasRole('SPECIALIST')")
    RestrictionView clinicalRestriction(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID participantId,
            @RequestParam SpecialistContext actingContext,
            @RequestBody RestrictionCommand command) {
        UUID actor = accounts.requireActive(jwt.getSubject()).id();
        return safety.createPhysiotherapistRestriction(
                actor, participantId, actingContext.value(), command);
    }

    @PostMapping("/participants/{participantId}/assessments/{assessmentId}/factors/{factorId}/overrides")
    @PreAuthorize("hasRole('SPECIALIST')")
    OverrideView override(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID participantId,
            @PathVariable UUID assessmentId,
            @PathVariable UUID factorId,
            @RequestParam SpecialistContext actingContext,
            @RequestBody OverrideCommand command) {
        UUID actor = accounts.requireActive(jwt.getSubject()).id();
        return safety.overrideFactor(
                actor, participantId, actingContext.value(), assessmentId, factorId, command);
    }

    @GetMapping("/me/restrictions/history")
    @PreAuthorize("hasRole('PARTICIPANT')")
    java.util.List<RestrictionView> history(@AuthenticationPrincipal Jwt jwt) {
        return safety.participantHistory(jwt.getSubject());
    }

    @GetMapping("/admin/legacy/participant-restrictions")
    @PreAuthorize("hasRole('ADMIN')")
    SafetyV2Service.LegacyReport legacyReport() {
        return safety.legacyReport();
    }

    enum SpecialistContext {
        TRAINER,
        PHYSIOTHERAPIST;

        ActingContext value() {
            return new ActingContext(
                    com.motionecosystem.specialist.api.SpecialistAuthorizationPort.ProfessionalRole
                            .valueOf(name()));
        }
    }
}
