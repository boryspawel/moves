package com.motionecosystem.onboarding;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;

import com.motionecosystem.availability.RecurringAvailabilityService;
import com.motionecosystem.identityaccess.api.ProfileType;
import com.motionecosystem.specialist.SpecialistKind;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/onboarding")
@SecurityRequirement(name = "oidc")
class OnboardingController {

    private final OnboardingService onboarding;

    OnboardingController(OnboardingService onboarding) {
        this.onboarding = onboarding;
    }

    @GetMapping
    @Operation(summary = "Return role-aware onboarding state")
    OnboardingService.State state(@AuthenticationPrincipal Jwt jwt) {
        return onboarding.state(jwt.getSubject());
    }

    @PutMapping("/profile-type")
    OnboardingService.State selectProfileType(@AuthenticationPrincipal Jwt jwt,
                                              @RequestBody ProfileTypeRequest request) {
        return onboarding.selectProfileType(jwt.getSubject(), request.profileType());
    }

    @PutMapping("/legal-acknowledgements")
    OnboardingService.State legal(@AuthenticationPrincipal Jwt jwt,
                                  @RequestBody LegalRequest request) {
        return onboarding.acknowledgeLegal(
                jwt.getSubject(), request.termsAccepted(), request.privacyNoticeAcknowledged());
    }

    @PutMapping("/participant-profile")
    OnboardingService.State participantProfile(@AuthenticationPrincipal Jwt jwt,
                                               @RequestBody ParticipantProfileRequest request) {
        return onboarding.saveParticipantProfile(jwt.getSubject(), request.displayName());
    }

    @PutMapping("/specialist-profile")
    OnboardingService.State specialistProfile(@AuthenticationPrincipal Jwt jwt,
                                              @RequestBody SpecialistProfileRequest request) {
        return onboarding.saveSpecialistProfile(
                jwt.getSubject(), request.displayName(), request.specialistKind());
    }

    @PutMapping("/availability")
    OnboardingService.State availability(@AuthenticationPrincipal Jwt jwt,
                                         @RequestBody AvailabilityRequest request) {
        List<RecurringAvailabilityService.Slot> slots = request.slots() == null ? null : request.slots().stream()
                .map(item -> new RecurringAvailabilityService.Slot(
                        item.dayOfWeek(), item.startTime(), item.endTime(), item.timeZone()))
                .toList();
        return onboarding.replaceAvailability(jwt.getSubject(), slots);
    }

    record ProfileTypeRequest(ProfileType profileType) {
    }

    record LegalRequest(boolean termsAccepted, boolean privacyNoticeAcknowledged) {
    }

    record ParticipantProfileRequest(String displayName) {
    }

    record SpecialistProfileRequest(String displayName, SpecialistKind specialistKind) {
    }

    record AvailabilityRequest(List<SlotRequest> slots) {
    }

    record SlotRequest(DayOfWeek dayOfWeek, LocalTime startTime, LocalTime endTime, String timeZone) {
    }
}
