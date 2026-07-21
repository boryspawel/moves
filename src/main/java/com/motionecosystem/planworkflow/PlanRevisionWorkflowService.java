package com.motionecosystem.planworkflow;

import com.motionecosystem.audit.AuditRecorder;
import com.motionecosystem.exercisecatalog.api.ExerciseCatalogQueryPort;
import com.motionecosystem.identityaccess.api.CurrentAccount;
import com.motionecosystem.identityaccess.api.CurrentAccountService;
import com.motionecosystem.identityaccess.api.ProfileType;
import com.motionecosystem.loadanalysis.api.PlannedLoadCalculationPort;
import com.motionecosystem.loadanalysis.api.PlannedLoadCalculationPort.LoadCalculationVersion;
import com.motionecosystem.safety.api.SafetyAssessmentPort;
import com.motionecosystem.safety.api.SafetyAssessmentPort.AssessmentSnapshot;
import com.motionecosystem.safety.api.SafetyAssessmentPort.Result;
import com.motionecosystem.specialist.api.SpecialistAuthorizationPort;
import com.motionecosystem.specialist.api.SpecialistAuthorizationPort.ActingContext;
import com.motionecosystem.specialist.api.SpecialistAuthorizationPort.Capability;
import com.motionecosystem.trainingplanning.api.PlanRevisionWorkflowPersistence;
import com.motionecosystem.trainingplanning.api.PlanRevisionWorkflowPersistence.ActivationOutcome;
import com.motionecosystem.trainingplanning.api.PlanRevisionWorkflowPersistence.WorkflowState;
import com.motionecosystem.trainingplanning.api.PlanRevisionQueryPort;
import com.motionecosystem.trainingplanning.api.PlanRevisionQueryPort.PlanRevisionSnapshot;
import com.motionecosystem.trainingplanning.api.TrainingPlanningWorkflowPort;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class PlanRevisionWorkflowService {

    private static final LoadCalculationVersion LOAD_VERSION =
            new LoadCalculationVersion("planned-load-v1", "safety-validation-v1");

    private final CurrentAccountService accounts;
    private final PlanRevisionQueryPort revisions;
    private final TrainingPlanningWorkflowPort planning;
    private final PlannedLoadCalculationPort loads;
    private final SafetyAssessmentPort safety;
    private final SpecialistAuthorizationPort authorization;
    private final ExerciseCatalogQueryPort catalog;
    private final PlanRevisionWorkflowPersistence persistence;
    private final AuditRecorder audit;
    private final Clock clock;

    @Transactional
    public ValidationView validate(
            String subject, UUID revisionId, ValidateWorkflowCommand command) {
        if (command == null || command.expectedVersion() < 0) {
            throw badRequest("expected draft version is required");
        }
        WorkflowState state = requireState(revisionId);
        CurrentAccount actor = authorize(subject, state, command.actingContext(), true);
        PlanRevisionSnapshot revision = requireRevision(revisionId);
        String checksum = contentChecksum(revision);
        var structural = planning.validateForWorkflow(subject, revisionId, command.expectedVersion());
        if (!structural.passed()) {
            throw conflict("structural validation failed: " + String.join(",", structural.violations()));
        }
        var load = loads.calculate(revision, LOAD_VERSION);
        AssessmentSnapshot assessment = safety.assess(state.participantId(), revision, load);
        String targetStatus = switch (assessment.recordedResult()) {
            case PASS, INFO -> "READY";
            case WARNING -> "NEEDS_REVIEW";
            case HARD_BLOCK -> "BLOCKED";
        };
        WorkflowState saved = mutate(() -> persistence.completeValidation(
                revisionId,
                command.expectedVersion(),
                checksum,
                load.snapshotId(),
                assessment.id(),
                assessment.recordedResult().name(),
                targetStatus,
                clock.instant()));
        audit.record(subject, "PLAN_REVISION_WORKFLOW_VALIDATED", "PlanRevision", revisionId);
        return new ValidationView(
                revisionId,
                saved.revisionStatus(),
                checksum,
                load.snapshotId(),
                assessment);
    }

    @Transactional
    public AcknowledgementView acknowledge(
            String subject, UUID revisionId, AcknowledgeWarningCommand command) {
        if (command == null || command.factorIds() == null || command.factorIds().isEmpty()
                || command.rationale() == null || command.rationale().isBlank()
                || command.rationale().length() > 500) {
            throw badRequest("warning factors and concise rationale are required");
        }
        WorkflowState state = requireState(revisionId);
        CurrentAccount actor = authorize(subject, state, command.actingContext(), false);
        AssessmentSnapshot assessment = requireAssessment(state);
        Set<UUID> warningIds = assessment.factors().stream()
                .filter(factor -> factor.result() == Result.WARNING)
                .map(SafetyAssessmentPort.FactorSnapshot::id)
                .collect(Collectors.toSet());
        if (!warningIds.containsAll(command.factorIds())) {
            throw badRequest("only warning factors from the current assessment can be acknowledged");
        }
        String capability = actor.hasProfile(ProfileType.SPECIALIST)
                ? Capability.ACKNOWLEDGE_PERFORMANCE_WARNING.name()
                : "PARTICIPANT_SELF_ACKNOWLEDGEMENT";
        command.factorIds().forEach(factorId -> persistence.acknowledgeWarning(
                revisionId,
                assessment.id(),
                factorId,
                actor.id(),
                capability,
                command.rationale().trim(),
                clock.instant()));
        audit.record(subject, "PLAN_WARNINGS_ACKNOWLEDGED", "PlanRevision", revisionId);
        return new AcknowledgementView(revisionId, assessment.id(), Set.copyOf(command.factorIds()));
    }

    @Transactional
    public ActivationOutcome activate(
            String subject,
            UUID revisionId,
            String idempotencyKey,
            ActivateWorkflowCommand command) {
        String key = idempotencyKey == null ? "" : idempotencyKey.trim();
        if (key.isEmpty() || key.length() > 120 || command == null) {
            throw badRequest("Idempotency-Key and activation command are required");
        }
        WorkflowState state = requireState(revisionId);
        CurrentAccount actor = authorize(subject, state, command.actingContext(), true);
        if ("ACTIVE".equals(state.revisionStatus())) {
            return mutate(() -> persistence.activate(
                    revisionId, state.validationChecksum(), key, actor.id(), clock.instant()));
        }
        PlanRevisionSnapshot revision = requireRevision(revisionId);
        String checksum = contentChecksum(revision);
        if (!checksum.equals(state.validationChecksum())) {
            throw conflict("revision changed after validation");
        }
        AssessmentSnapshot assessment = requireAssessment(state);
        if (!safety.isRestrictionSnapshotFresh(
                assessment.id(), state.participantId(), clock.instant())) {
            throw conflict("effective restrictions changed; revalidation is required");
        }
        Set<UUID> exerciseIds = revision.cycles().stream()
                .flatMap(cycle -> cycle.microcycles().stream())
                .flatMap(micro -> micro.sessions().stream())
                .flatMap(session -> session.prescriptions().stream())
                .map(item -> item.exerciseVersionId())
                .collect(Collectors.toSet());
        if (catalog.findPublishedVersions(exerciseIds).size() != exerciseIds.size()) {
            throw conflict("an exercise version was withdrawn; revalidation is required");
        }
        if (assessment.effectiveResult() == Result.HARD_BLOCK) {
            List<String> codes = assessment.factors().stream()
                    .filter(factor -> factor.result() == Result.HARD_BLOCK
                            && !factor.activelyOverridden())
                    .map(SafetyAssessmentPort.FactorSnapshot::explanationCode)
                    .distinct()
                    .toList();
            throw new SafetyBlockException(codes);
        }
        Set<UUID> warnings = assessment.factors().stream()
                .filter(factor -> factor.result() == Result.WARNING)
                .map(SafetyAssessmentPort.FactorSnapshot::id)
                .collect(Collectors.toSet());
        if (!persistence.acknowledgedFactors(revisionId, assessment.id()).containsAll(warnings)) {
            throw conflict("all warning factors require acknowledgement");
        }
        ActivationOutcome outcome = mutate(() -> persistence.activate(
                revisionId, checksum, key, actor.id(), clock.instant()));
        if (!outcome.repeated()) {
            audit.record(subject, "PLAN_REVISION_ACTIVATED", "PlanRevision", revisionId);
        }
        return outcome;
    }

    @Transactional(readOnly = true)
    public WorkflowView workflow(String subject, UUID revisionId, ActingContext actingContext) {
        WorkflowState state = requireState(revisionId);
        authorize(subject, state, actingContext, true);
        AssessmentSnapshot assessment = state.assessmentId() == null
                ? null
                : safety.findAssessment(state.assessmentId(), clock.instant()).orElse(null);
        return new WorkflowView(state, assessment);
    }

    private CurrentAccount authorize(
            String subject, WorkflowState state, ActingContext context, boolean planningCapability) {
        CurrentAccount actor = accounts.requireActive(subject);
        if (actor.hasProfile(ProfileType.PARTICIPANT) && actor.id().equals(state.participantId())) {
            if (!actor.id().equals(state.ownerId()) || !"SELF_DIRECTED".equals(state.mode())) {
                throw forbidden("participant can manage only their owned self-directed plan");
            }
            return actor;
        }
        if (!actor.hasProfile(ProfileType.SPECIALIST) || context == null) {
            throw forbidden("explicit specialist acting context is required");
        }
        Capability capability = planningCapability
                ? (context.role() == SpecialistAuthorizationPort.ProfessionalRole.PHYSIOTHERAPIST
                        ? Capability.PLAN_FUNCTIONAL_RECOVERY : Capability.PLAN_PERFORMANCE)
                : Capability.ACKNOWLEDGE_PERFORMANCE_WARNING;
        SpecialistAuthorizationPort.Purpose purpose = context.role()
                == SpecialistAuthorizationPort.ProfessionalRole.PHYSIOTHERAPIST
                ? SpecialistAuthorizationPort.Purpose.FUNCTIONAL_RECOVERY
                : SpecialistAuthorizationPort.Purpose.PERFORMANCE_PLANNING;
        authorization.requireCapabilities(
                actor.id(), state.participantId(), context, Set.of(capability), purpose);
        if (!actor.id().equals(state.ownerId())) {
            throw forbidden("only the plan owner can run its workflow");
        }
        return actor;
    }

    private AssessmentSnapshot requireAssessment(WorkflowState state) {
        if (state.assessmentId() == null) {
            throw conflict("revision has no safety assessment");
        }
        return safety.findAssessment(state.assessmentId(), clock.instant())
                .orElseThrow(() -> conflict("safety assessment is unavailable"));
    }

    private WorkflowState requireState(UUID revisionId) {
        try {
            return persistence.state(revisionId);
        } catch (IllegalArgumentException missing) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "plan revision not found");
        }
    }

    private PlanRevisionSnapshot requireRevision(UUID revisionId) {
        return revisions.findRevision(revisionId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "plan revision not found"));
    }

    private static String contentChecksum(PlanRevisionSnapshot revision) {
        String content = revision.revisionId() + "|" + revision.planId() + "|"
                + revision.participantAccountId() + "|" + revision.phaseIntent() + "|"
                + revision.validFrom() + "|" + revision.validTo() + "|"
                + revision.goals() + "|" + revision.cycles() + "|" + revision.loadBudgets();
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(content.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private static <T> T mutate(java.util.function.Supplier<T> action) {
        try {
            return action.get();
        } catch (PlanRevisionWorkflowPersistence.RevisionConflictException
                | ObjectOptimisticLockingFailureException conflict) {
            throw conflict("revision was changed concurrently");
        } catch (PlanRevisionWorkflowPersistence.ImmutableRevisionException immutable) {
            throw conflict("revision workflow state does not allow this operation");
        } catch (DataIntegrityViolationException duplicate) {
            throw conflict("concurrent or repeated workflow command conflicted");
        }
    }

    private static ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    private static ResponseStatusException forbidden(String message) {
        return new ResponseStatusException(HttpStatus.FORBIDDEN, message);
    }

    private static ResponseStatusException conflict(String message) {
        return new ResponseStatusException(HttpStatus.CONFLICT, message);
    }

    public record ValidateWorkflowCommand(long expectedVersion, ActingContext actingContext) {
    }

    public record AcknowledgeWarningCommand(
            Set<UUID> factorIds, String rationale, ActingContext actingContext) {
        public AcknowledgeWarningCommand {
            factorIds = factorIds == null ? Set.of() : Set.copyOf(factorIds);
        }
    }

    public record ActivateWorkflowCommand(ActingContext actingContext) {
    }

    public record ValidationView(
            UUID revisionId,
            String status,
            String checksum,
            UUID loadSnapshotId,
            AssessmentSnapshot assessment) {
    }

    public record AcknowledgementView(
            UUID revisionId, UUID assessmentId, Set<UUID> factorIds) {
    }

    public record WorkflowView(WorkflowState state, AssessmentSnapshot assessment) {
    }

    public static final class SafetyBlockException extends ResponseStatusException {
        private final List<String> explanationCodes;

        SafetyBlockException(List<String> explanationCodes) {
            super(HttpStatus.CONFLICT, "plan activation is blocked by safety assessment");
            this.explanationCodes = List.copyOf(explanationCodes);
        }

        public List<String> explanationCodes() {
            return explanationCodes;
        }
    }
}
