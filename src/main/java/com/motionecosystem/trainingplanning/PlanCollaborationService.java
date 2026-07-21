package com.motionecosystem.trainingplanning;

import com.motionecosystem.audit.AuditRecorder;
import com.motionecosystem.identityaccess.api.CurrentAccount;
import com.motionecosystem.identityaccess.api.CurrentAccountService;
import com.motionecosystem.identityaccess.api.ProfileType;
import com.motionecosystem.specialist.api.SpecialistAuthorizationPort;
import com.motionecosystem.specialist.api.SpecialistAuthorizationPort.ActingContext;
import com.motionecosystem.specialist.api.SpecialistAuthorizationPort.Capability;
import com.motionecosystem.specialist.api.SpecialistAuthorizationPort.ProfessionalRole;
import com.motionecosystem.specialist.api.SpecialistAuthorizationPort.Purpose;
import com.motionecosystem.trainingplanning.PlanCollaborationPersistence.CollaboratorData;
import com.motionecosystem.trainingplanning.PlanCollaborationPersistence.ReviewData;
import java.time.Clock;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class PlanCollaborationService {
    private final CurrentAccountService accounts;
    private final SpecialistAuthorizationPort authorization;
    private final TrainingPlanningV2Persistence plans;
    private final PlanCollaborationPersistence persistence;
    private final AuditRecorder audit;
    private final Clock clock;

    @Transactional
    public CollaboratorView addCollaborator(
            String subject, UUID planId, ActingContext ownerContext, CollaboratorCommand command) {
        CurrentAccount owner = accounts.requireActive(subject);
        var plan = plans.findPlanAccess(planId).orElseThrow(() -> notFound("training plan not found"));
        if (!owner.id().equals(plan.ownerAccountId())) throw forbidden("only the plan owner can add collaborators");
        authorizeOwner(owner, plan.participantAccountId(), ownerContext);
        if (command == null || command.specialistAccountId() == null || command.actingRole() == null
                || command.scopes() == null || command.scopes().isEmpty()) {
            throw badRequest("collaborator, professional role and scopes are required");
        }
        if (command.specialistAccountId().equals(owner.id())) throw badRequest("plan owner is not a collaborator");
        Set<String> scopes = command.scopes().stream().map(Enum::name)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        authorization.requireCapabilities(command.specialistAccountId(), plan.participantAccountId(),
                new ActingContext(command.actingRole()), Set.of(planCapability(command.actingRole())),
                purpose(command.actingRole()));
        CollaboratorData collaborator = new CollaboratorData(UUID.randomUUID(), planId,
                command.specialistAccountId(), command.actingRole().name(), scopes, "ACTIVE",
                owner.id(), clock.instant());
        persistence.saveCollaborator(collaborator);
        audit.record(subject, "PLAN_COLLABORATOR_ADDED_" + command.actingRole(), "TrainingPlan", planId);
        return view(collaborator);
    }

    @Transactional
    public CollaboratorView endCollaborator(
            String subject, UUID planId, UUID collaboratorId, ActingContext ownerContext) {
        CurrentAccount owner = accounts.requireActive(subject);
        var plan = plans.findPlanAccess(planId).orElseThrow(() -> notFound("training plan not found"));
        if (!owner.id().equals(plan.ownerAccountId())) throw forbidden("only the plan owner can end collaboration");
        authorizeOwner(owner, plan.participantAccountId(), ownerContext);
        try {
            CollaboratorData ended = persistence.endCollaborator(collaboratorId, planId, clock.instant());
            audit.record(subject, "PLAN_COLLABORATOR_ENDED", "TrainingPlan", planId);
            return view(ended);
        } catch (IllegalArgumentException missing) {
            throw notFound(missing.getMessage());
        } catch (IllegalStateException state) {
            throw conflict(state.getMessage());
        }
    }

    @Transactional
    public ReviewView requestReview(String subject, UUID revisionId, ReviewRequestCommand command) {
        CurrentAccount owner = accounts.requireActive(subject);
        var revision = plans.findRevisionAccess(revisionId)
                .orElseThrow(() -> notFound("plan revision not found"));
        if (!owner.id().equals(revision.ownerAccountId())) throw forbidden("only the plan owner can request review");
        authorizeOwner(owner, revision.participantAccountId(), owner.hasProfile(ProfileType.SPECIALIST)
                ? new ActingContext(role(revision.authorCapability())) : null);
        if (!Set.of("BLOCKED", "NEEDS_REVIEW").contains(revision.status())) {
            throw conflict("only a blocked or review-needed revision can be sent to review");
        }
        if (command == null || command.reviewerAccountId() == null) throw badRequest("reviewer is required");
        CollaboratorData reviewer = persistence.findActiveCollaborator(
                        revision.planId(), command.reviewerAccountId())
                .filter(item -> item.scopes().contains(CollaborationScope.REVIEW_SAFETY.name()))
                .orElseThrow(() -> forbidden("reviewer needs active REVIEW_SAFETY collaboration scope"));
        ProfessionalRole role = ProfessionalRole.valueOf(reviewer.professionalRole());
        if (role != ProfessionalRole.PHYSIOTHERAPIST) {
            throw forbidden("safety review requires physiotherapist collaboration context");
        }
        authorization.requireCapabilities(reviewer.specialistId(), revision.participantAccountId(),
                new ActingContext(role), Set.of(Capability.PLAN_FUNCTIONAL_RECOVERY), Purpose.FUNCTIONAL_RECOVERY);
        ReviewData review = new ReviewData(UUID.randomUUID(), revisionId, owner.id(), reviewer.specialistId(),
                "OPEN", text(command.requestReference(), "review reference"), null, clock.instant(), null);
        persistence.saveReview(review);
        audit.record(subject, "BLOCKED_PLAN_SENT_TO_PHYSIO_REVIEW", "PlanRevision", revisionId);
        return view(review);
    }

    @Transactional
    public ReviewView decideReview(
            String subject, UUID reviewId, ActingContext context, ReviewDecisionCommand command) {
        CurrentAccount reviewer = accounts.requireActive(subject);
        ReviewData review = persistence.findReview(reviewId).orElseThrow(() -> notFound("review request not found"));
        if (!review.reviewerId().equals(reviewer.id())) throw forbidden("review belongs to another collaborator");
        if (context == null || context.role() != ProfessionalRole.PHYSIOTHERAPIST) {
            throw forbidden("explicit physiotherapist acting context is required");
        }
        var revision = plans.findRevisionAccess(review.revisionId()).orElseThrow();
        CollaboratorData collaborator = persistence.findActiveCollaborator(revision.planId(), reviewer.id())
                .filter(item -> ProfessionalRole.PHYSIOTHERAPIST.name().equals(item.professionalRole()))
                .filter(item -> item.scopes().contains(CollaborationScope.REVIEW_SAFETY.name()))
                .orElseThrow(() -> forbidden("active physiotherapist REVIEW_SAFETY collaboration is required"));
        authorization.requireCapabilities(reviewer.id(), revision.participantAccountId(), context,
                Set.of(Capability.PLAN_FUNCTIONAL_RECOVERY), Purpose.FUNCTIONAL_RECOVERY);
        if (command == null || command.decision() == null) throw badRequest("review decision is required");
        String status = switch (command.decision()) {
            case PROPOSE_CHANGE -> "CHANGE_PROPOSED";
            case READY_FOR_REVALIDATION -> "READY_FOR_REVALIDATION";
        };
        ReviewData decided;
        try {
            decided = persistence.decideReview(reviewId, reviewer.id(), status,
                    text(command.decisionReference(), "decision reference"), clock.instant());
        } catch (IllegalStateException state) {
            throw conflict(state.getMessage());
        }
        audit.record(subject, "PLAN_REVIEW_" + command.decision(), "PlanReviewRequest", reviewId);
        return view(decided);
    }

    private void authorizeOwner(CurrentAccount owner, UUID participant, ActingContext context) {
        if (owner.hasProfile(ProfileType.PARTICIPANT) && owner.id().equals(participant)) return;
        if (!owner.hasProfile(ProfileType.SPECIALIST) || context == null) {
            throw forbidden("explicit specialist owner context is required");
        }
        authorization.requireCapabilities(owner.id(), participant, context,
                Set.of(planCapability(context.role())), purpose(context.role()));
    }

    static Capability planCapability(ProfessionalRole role) {
        return role == ProfessionalRole.TRAINER
                ? Capability.PLAN_PERFORMANCE : Capability.PLAN_FUNCTIONAL_RECOVERY;
    }

    static Purpose purpose(ProfessionalRole role) {
        return role == ProfessionalRole.TRAINER ? Purpose.PERFORMANCE_PLANNING : Purpose.FUNCTIONAL_RECOVERY;
    }

    private static ProfessionalRole role(String capability) {
        return Capability.PLAN_FUNCTIONAL_RECOVERY.name().equals(capability)
                ? ProfessionalRole.PHYSIOTHERAPIST : ProfessionalRole.TRAINER;
    }

    private static String text(String value, String field) {
        if (value == null || value.isBlank() || value.trim().length() > 500) {
            throw badRequest(field + " is required");
        }
        return value.trim();
    }

    private static CollaboratorView view(CollaboratorData item) {
        return new CollaboratorView(item.id(), item.planId(), item.specialistId(),
                ProfessionalRole.valueOf(item.professionalRole()), item.scopes().stream()
                .map(CollaborationScope::valueOf).collect(java.util.stream.Collectors.toUnmodifiableSet()),
                item.status(), item.addedAt());
    }

    private static ReviewView view(ReviewData item) {
        return new ReviewView(item.id(), item.revisionId(), item.reviewerId(), item.status(),
                item.requestReference(), item.decisionReference(), item.requestedAt(), item.decidedAt());
    }

    private static ResponseStatusException badRequest(String value) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, value);
    }
    private static ResponseStatusException forbidden(String value) {
        return new ResponseStatusException(HttpStatus.FORBIDDEN, value);
    }
    private static ResponseStatusException notFound(String value) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, value);
    }
    private static ResponseStatusException conflict(String value) {
        return new ResponseStatusException(HttpStatus.CONFLICT, value);
    }

    public enum CollaborationScope { VIEW_PLAN, EDIT_DRAFT, REVIEW_SAFETY }
    public enum ReviewDecision { PROPOSE_CHANGE, READY_FOR_REVALIDATION }
    public record CollaboratorCommand(UUID specialistAccountId, ProfessionalRole actingRole,
                                      Set<CollaborationScope> scopes) { }
    public record ReviewRequestCommand(UUID reviewerAccountId, String requestReference) { }
    public record ReviewDecisionCommand(ReviewDecision decision, String decisionReference) { }
    public record CollaboratorView(UUID id, UUID planId, UUID specialistAccountId,
                                   ProfessionalRole actingRole, Set<CollaborationScope> scopes,
                                   String status, java.time.Instant addedAt) { }
    public record ReviewView(UUID id, UUID revisionId, UUID reviewerAccountId, String status,
                             String requestReference, String decisionReference,
                             java.time.Instant requestedAt, java.time.Instant decidedAt) { }
}
