package com.motionecosystem.onboarding;

import java.util.ArrayList;
import java.util.List;

import com.motionecosystem.audit.AuditRecorder;
import com.motionecosystem.availability.RecurringAvailabilityService;
import com.motionecosystem.consent.LegalAcknowledgementService;
import com.motionecosystem.identityaccess.api.CurrentAccount;
import com.motionecosystem.identityaccess.api.CurrentAccountService;
import com.motionecosystem.identityaccess.api.ProfileType;
import com.motionecosystem.participant.ParticipantProfileService;
import com.motionecosystem.specialist.SpecialistKind;
import com.motionecosystem.specialist.SpecialistProfileService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class OnboardingService {

    private final CurrentAccountService accounts;
    private final LegalAcknowledgementService legal;
    private final ParticipantProfileService participantProfiles;
    private final SpecialistProfileService specialistProfiles;
    private final RecurringAvailabilityService availability;
    private final AuditRecorder audit;

    @Transactional
    public State state(String subject) {
        return stateFor(accounts.requireActive(subject));
    }

    @Transactional
    public State selectProfileType(String subject, ProfileType type) {
        CurrentAccount before = accounts.requireActive(subject);
        CurrentAccount account = accounts.selectProfileType(subject, type);
        if (before.profileType() == null) {
            audit.record(subject, "ONBOARDING_PROFILE_TYPE_SELECTED", "PrincipalAccount", account.id());
        }
        return stateFor(account);
    }

    @Transactional
    public State acknowledgeLegal(String subject, boolean termsAccepted, boolean privacyAcknowledged) {
        CurrentAccount account = accounts.requireActive(subject);
        boolean alreadyComplete = legal.hasAllCurrent(account.id());
        legal.acknowledgeRequired(account.id(), termsAccepted, privacyAcknowledged);
        if (!alreadyComplete) {
            audit.record(subject, "LEGAL_DOCUMENTS_ACKNOWLEDGED", "PrincipalAccount", account.id());
        }
        return stateFor(account);
    }

    @Transactional
    public State saveParticipantProfile(String subject, String displayName) {
        CurrentAccount account = requireProfileType(subject, ProfileType.PARTICIPANT);
        ParticipantProfileService.ProfileView profile = participantProfiles.save(account.id(), displayName);
        audit.record(subject, "PARTICIPANT_PROFILE_SAVED", "ParticipantProfile", profile.id());
        return stateFor(account);
    }

    @Transactional
    public State saveSpecialistProfile(String subject, String displayName, SpecialistKind specialistKind) {
        CurrentAccount account = requireProfileType(subject, ProfileType.SPECIALIST);
        SpecialistProfileService.ProfileView profile = specialistProfiles.save(account.id(), displayName, specialistKind);
        audit.record(subject, "SPECIALIST_PROFILE_SAVED", "SpecialistProfile", profile.id());
        return stateFor(account);
    }

    @Transactional
    public State replaceAvailability(String subject, List<RecurringAvailabilityService.Slot> slots) {
        CurrentAccount account = accounts.requireActive(subject);
        if (account.profileType() == null) {
            throw conflict("profile type must be selected first");
        }
        availability.replace(account.id(), slots);
        audit.record(subject, "RECURRING_AVAILABILITY_REPLACED", "PrincipalAccount", account.id());
        return stateFor(account);
    }

    private CurrentAccount requireProfileType(String subject, ProfileType expected) {
        CurrentAccount account = accounts.requireActive(subject);
        if (account.profileType() != expected) {
            throw conflict("selected profile type does not allow this profile");
        }
        return account;
    }

    private State stateFor(CurrentAccount account) {
        List<Step> missing = new ArrayList<>();
        if (account.profileType() == null) {
            missing.add(Step.PROFILE_TYPE);
        }
        if (!legal.hasAllCurrent(account.id())) {
            missing.add(Step.LEGAL_DOCUMENTS);
        }

        ProfileSummary profile = null;
        if (account.profileType() == ProfileType.PARTICIPANT) {
            profile = participantProfiles.find(account.id())
                    .map(item -> new ProfileSummary(item.displayName(), null))
                    .orElse(null);
        } else if (account.profileType() == ProfileType.SPECIALIST) {
            profile = specialistProfiles.find(account.id())
                    .map(item -> new ProfileSummary(item.displayName(), item.specialistKind()))
                    .orElse(null);
        }
        if (account.profileType() != null && profile == null) {
            missing.add(Step.BASIC_PROFILE);
        }
        if (!availability.isConfigured(account.id())) {
            missing.add(Step.RECURRING_AVAILABILITY);
        }

        Stage stage = missing.isEmpty() ? Stage.READY : switch (missing.getFirst()) {
            case PROFILE_TYPE -> Stage.PROFILE_TYPE_REQUIRED;
            case LEGAL_DOCUMENTS -> Stage.LEGAL_REQUIRED;
            case BASIC_PROFILE -> Stage.PROFILE_REQUIRED;
            case RECURRING_AVAILABILITY -> Stage.AVAILABILITY_REQUIRED;
        };
        return new State(
                stage,
                List.copyOf(missing),
                account.profileType(),
                profile,
                legal.current(account.id()),
                availability.list(account.id()));
    }

    private static ResponseStatusException conflict(String message) {
        return new ResponseStatusException(HttpStatus.CONFLICT, message);
    }

    public enum Stage {
        PROFILE_TYPE_REQUIRED,
        LEGAL_REQUIRED,
        PROFILE_REQUIRED,
        AVAILABILITY_REQUIRED,
        READY
    }

    public enum Step {
        PROFILE_TYPE,
        LEGAL_DOCUMENTS,
        BASIC_PROFILE,
        RECURRING_AVAILABILITY
    }

    public record ProfileSummary(String displayName, SpecialistKind specialistKind) {
    }

    public record State(
            Stage stage,
            List<Step> missingSteps,
            ProfileType profileType,
            ProfileSummary profile,
            List<LegalAcknowledgementService.View> currentLegalAcknowledgements,
            List<RecurringAvailabilityService.Slot> availability) {
    }
}
