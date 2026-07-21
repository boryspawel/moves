package com.motionecosystem.trainingplanning;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import com.motionecosystem.audit.AuditRecorder;
import com.motionecosystem.exercisecatalog.api.ExerciseCatalogQueryPort;
import com.motionecosystem.identityaccess.api.ActiveParticipantPort;
import com.motionecosystem.identityaccess.api.CurrentAccount;
import com.motionecosystem.identityaccess.api.CurrentAccountService;
import com.motionecosystem.identityaccess.api.ProfileType;
import com.motionecosystem.specialist.SpecialistRelationshipService;
import com.motionecosystem.trainingplanning.TrainingPlanningModel.BudgetAction;
import com.motionecosystem.trainingplanning.TrainingPlanningModel.DoseType;
import com.motionecosystem.trainingplanning.TrainingPlanningModel.GoalPerspective;
import com.motionecosystem.trainingplanning.TrainingPlanningModel.GoalStatus;
import com.motionecosystem.trainingplanning.TrainingPlanningModel.IntensityType;
import com.motionecosystem.trainingplanning.TrainingPlanningModel.PlanMode;
import com.motionecosystem.trainingplanning.TrainingPlanningModel.PlanStatus;
import com.motionecosystem.trainingplanning.TrainingPlanningModel.PrescriptionSide;
import com.motionecosystem.trainingplanning.TrainingPlanningModel.RevisionStatus;
import com.motionecosystem.trainingplanning.TrainingPlanningModel.ValidationResult;
import com.motionecosystem.trainingplanning.api.PlanRevisionQueryPort;
import com.motionecosystem.trainingplanning.api.TrainingPlanningWorkflowPort;
import com.motionecosystem.trainingplanning.api.PlanRevisionQueryPort.PlanRevisionSnapshot;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class TrainingPlanningV2Service implements TrainingPlanningWorkflowPort {

    private final CurrentAccountService accounts;
    private final ActiveParticipantPort participants;
    private final SpecialistRelationshipService relationships;
    private final ExerciseCatalogQueryPort catalog;
    private final TrainingPlanningV2Persistence persistence;
    private final PlanRevisionQueryPort revisions;
    private final AuditRecorder audit;
    private final Clock clock;

    @Transactional
    public EditorView createDraft(String subject, CreateDraftCommand command) {
        CurrentAccount actor = accounts.requireActive(subject);
        if (command == null) {
            throw badRequest("draft command is required");
        }
        UUID participantId = command.participantAccountId();
        PlanMode mode = command.mode();
        if (actor.profileType() == ProfileType.PARTICIPANT) {
            participantId = participantId == null ? actor.id() : participantId;
            if (!actor.id().equals(participantId)) {
                throw forbidden("participant can only create their own plan");
            }
            mode = mode == null ? PlanMode.SELF_DIRECTED : mode;
            if (mode != PlanMode.SELF_DIRECTED) {
                throw forbidden("participant can only create a self-directed plan");
            }
        } else if (actor.profileType() == ProfileType.SPECIALIST) {
            if (participantId == null) {
                throw badRequest("participant account is required");
            }
            mode = mode == null ? PlanMode.SPECIALIST : mode;
            if (mode == PlanMode.SELF_DIRECTED) {
                throw badRequest("specialist-authored plan must be specialist or collaborative");
            }
            relationships.requireActive(actor.id(), participantId);
        } else {
            throw forbidden("selected profile cannot create a training plan");
        }
        requireParticipant(participantId);
        validateDateRange(command.validFrom(), command.validTo(), "revision");
        Instant now = clock.instant();
        UUID planId = UUID.randomUUID();
        UUID revisionId = UUID.randomUUID();
        var plan = new TrainingPlanningModel.PlanDraft(planId, participantId,
                text(command.name(), 160, "plan name"), text(command.purpose(), 500, "plan purpose"),
                actor.id(), mode, PlanStatus.DRAFT, revisionId, actor.id(), now);
        var revision = new TrainingPlanningModel.Revision(revisionId, planId, 1, null,
                RevisionStatus.DRAFT, text(command.phaseIntent(), 500, "phase intent"),
                command.validFrom(), command.validTo(), actor.id(), capability(actor),
                "NATIVE_V2", "NOT_ASSESSED", now, now, 0);
        mutate(() -> persistence.createDraft(plan, revision));
        audit.record(subject, "TRAINING_PLAN_DRAFT_CREATED", "TrainingPlan", planId);
        return editorView(planId, revisionId);
    }

    @Transactional
    public EditorView addGoal(String subject, UUID revisionId, AddGoalCommand command) {
        var access = requireEditable(subject, revisionId, command == null ? null : command.expectedVersion());
        if (command.perspective() == null) {
            throw badRequest("goal perspective is required");
        }
        List<OutcomeCommand> requested = command.outcomes() == null ? List.of() : List.copyOf(command.outcomes());
        if (requested.stream().anyMatch(Objects::isNull)) {
            throw badRequest("goal outcome is required");
        }
        Set<String> metricCodes = requested.stream().map(item -> text(item.metricCode(), 80, "metric code"))
                .collect(java.util.stream.Collectors.toSet());
        if (metricCodes.size() != requested.size()) {
            throw badRequest("goal outcome metric codes must be unique");
        }
        Instant now = clock.instant();
        UUID goalId = UUID.randomUUID();
        var goal = new TrainingPlanningModel.Goal(goalId, revisionId, access.participantAccountId(),
                command.perspective(), text(command.category(), 80, "goal category"),
                text(command.title(), 160, "goal title"), optional(command.description(), 1000, "goal description"),
                range(command.priority(), 1, 100, "goal priority"),
                command.status() == null ? GoalStatus.ACTIVE : command.status(), command.targetDate(),
                access.actor().id(), now);
        List<TrainingPlanningModel.GoalOutcome> outcomes = requested.stream().map(item -> {
            if (item.target() == null || item.unit() == null || item.measurementMethod() == null) {
                throw badRequest("outcome target, unit and measurement method are required");
            }
            return new TrainingPlanningModel.GoalOutcome(UUID.randomUUID(), goalId,
                    text(item.metricCode(), 80, "metric code"), item.baseline(), item.target(),
                    text(item.unit(), 40, "outcome unit"),
                    text(item.measurementMethod(), 500, "measurement method"),
                    optional(item.evidenceSource(), 500, "evidence source"));
        }).toList();
        mutate(() -> persistence.addGoal(revisionId, command.expectedVersion(), goal, outcomes, now));
        return editorView(access.planId(), revisionId);
    }

    @Transactional
    public EditorView addCycle(String subject, UUID revisionId, AddCycleCommand command) {
        var access = requireEditable(subject, revisionId, command == null ? null : command.expectedVersion());
        positive(command.sequenceNumber(), "cycle sequence number");
        validateDateRange(command.startDate(), command.endDate(), "cycle");
        PlanRevisionSnapshot snapshot = requireSnapshot(revisionId);
        requireContained(command.startDate(), command.endDate(), snapshot.validFrom(), snapshot.validTo(), "cycle");
        var cycle = new TrainingPlanningModel.Cycle(UUID.randomUUID(), access.planId(), revisionId,
                command.sequenceNumber(), text(command.name(), 160, "cycle name"), command.startDate(),
                command.endDate(), text(command.phaseIntent(), 500, "cycle phase intent"),
                text(command.phaseGoal(), 500, "cycle phase goal"));
        mutate(() -> persistence.addCycle(revisionId, command.expectedVersion(), cycle, clock.instant()));
        return editorView(access.planId(), revisionId);
    }

    @Transactional
    public EditorView addMicrocycle(String subject, UUID revisionId, AddMicrocycleCommand command) {
        var access = requireEditable(subject, revisionId, command == null ? null : command.expectedVersion());
        positive(command.sequenceNumber(), "microcycle sequence number");
        validateDateRange(command.startDate(), command.endDate(), "microcycle");
        var parent = requireSnapshot(revisionId).cycles().stream()
                .filter(item -> item.id().equals(command.cycleId())).findFirst()
                .orElseThrow(() -> badRequest("cycle does not belong to revision"));
        requireContained(command.startDate(), command.endDate(), parent.startDate(), parent.endDate(), "microcycle");
        var microcycle = new TrainingPlanningModel.MicrocycleV2(UUID.randomUUID(), command.cycleId(),
                command.sequenceNumber(), text(command.name(), 160, "microcycle name"), command.startDate(),
                command.endDate(), text(command.phaseIntent(), 500, "microcycle phase intent"),
                text(command.phaseGoal(), 500, "microcycle phase goal"));
        mutate(() -> persistence.addMicrocycle(revisionId, command.expectedVersion(), microcycle, clock.instant()));
        return editorView(access.planId(), revisionId);
    }

    @Transactional
    public EditorView addSession(String subject, UUID revisionId, AddSessionCommand command) {
        var access = requireEditable(subject, revisionId, command == null ? null : command.expectedVersion());
        positive(command.expectedDurationMinutes(), "expected duration");
        if (command.availableFrom() != null && command.availableTo() != null
                && command.availableTo().isBefore(command.availableFrom())) {
            throw badRequest("session availability end precedes start");
        }
        var parent = requireSnapshot(revisionId).cycles().stream().flatMap(item -> item.microcycles().stream())
                .filter(item -> item.id().equals(command.microcycleId())).findFirst()
                .orElseThrow(() -> badRequest("microcycle does not belong to revision"));
        if (command.scheduledDate() != null) {
            requireContained(command.scheduledDate(), command.scheduledDate(),
                    parent.startDate(), parent.endDate(), "session");
        }
        var session = new TrainingPlanningModel.Session(UUID.randomUUID(), command.microcycleId(),
                access.participantAccountId(), text(command.title(), 160, "session title"), command.scheduledDate(),
                command.availableFrom(), command.availableTo(), command.expectedDurationMinutes(), clock.instant());
        mutate(() -> persistence.addSession(revisionId, command.expectedVersion(), session, clock.instant()));
        return editorView(access.planId(), revisionId);
    }

    @Transactional
    public EditorView addPrescription(String subject, UUID revisionId, AddPrescriptionCommand command) {
        var access = requireEditable(subject, revisionId, command == null ? null : command.expectedVersion());
        if (command.exerciseVersionId() == null || command.side() == null || command.doseType() == null) {
            throw badRequest("exercise version, side and dose type are required");
        }
        positive(command.position(), "prescription position");
        validateDose(command);
        catalog.findPublishedVersion(command.exerciseVersionId())
                .orElseThrow(() -> badRequest("prescription requires a published exercise version"));
        var prescription = new TrainingPlanningModel.Prescription(UUID.randomUUID(), command.sessionId(),
                command.exerciseVersionId(), command.position(), command.side(), command.doseType(),
                command.sets(), command.repetitions(), command.durationSeconds(), command.distanceMeters(),
                command.contacts(), command.externalLoadValue(), optional(command.externalLoadUnit(), 24, "load unit"),
                command.intensityType(), command.intensityValue(), optional(command.intensityZone(), 40, "intensity zone"),
                optional(command.tempo(), 40, "tempo"), optional(command.rangeOfMotion(), 40, "range of motion"),
                command.restSeconds(), optional(command.substituteGroup(), 80, "substitute group"),
                optional(command.notes(), 500, "prescription notes"));
        mutate(() -> persistence.addPrescription(revisionId, command.expectedVersion(), prescription, clock.instant()));
        return editorView(access.planId(), revisionId);
    }

    @Transactional
    public EditorView reorder(String subject, UUID revisionId, ReorderCommand command) {
        var access = requireEditable(subject, revisionId, command == null ? null : command.expectedVersion());
        if (command.sessionId() == null || command.prescriptionIds() == null
                || command.prescriptionIds().size() != Set.copyOf(command.prescriptionIds()).size()) {
            throw badRequest("reorder requires a session and unique prescription ids");
        }
        mutate(() -> persistence.reorderPrescriptions(revisionId, command.expectedVersion(), command.sessionId(),
                List.copyOf(command.prescriptionIds()), clock.instant()));
        return editorView(access.planId(), revisionId);
    }

    @Transactional
    public EditorView addLoadBudget(String subject, UUID revisionId, AddLoadBudgetCommand command) {
        var access = requireEditable(subject, revisionId, command == null ? null : command.expectedVersion());
        if (command.action() == null || command.low() == null || command.high() == null
                || command.low().signum() < 0 || command.high().compareTo(command.low()) < 0) {
            throw badRequest("load budget range and action are invalid");
        }
        var budget = new TrainingPlanningModel.LoadBudget(UUID.randomUUID(), revisionId,
                text(command.channel(), 40, "load channel"), command.low(), command.high(),
                text(command.unit(), 40, "load budget unit"), command.action(), access.actor().id(), clock.instant());
        mutate(() -> persistence.addLoadBudget(revisionId, command.expectedVersion(), budget, clock.instant()));
        return editorView(access.planId(), revisionId);
    }

    @Transactional
    public EditorView createRevision(String subject, UUID planId, CreateRevisionCommand command) {
        if (command == null || command.basedOnRevisionId() == null) {
            throw badRequest("base revision is required");
        }
        Access access = requirePlanEditor(subject, planId);
        UUID revisionId = mutateResult(() -> persistence.cloneRevision(planId, command.basedOnRevisionId(),
                access.actor().id(), capability(access.actor()), clock.instant()));
        audit.record(subject, "TRAINING_PLAN_REVISION_CREATED", "TrainingPlan", planId);
        return editorView(planId, revisionId);
    }

    @Transactional
    public StructuralValidationView validateStructurally(String subject, UUID revisionId,
                                                         ValidateCommand command) {
        var access = requireEditable(subject, revisionId, command == null ? null : command.expectedVersion());
        return performStructuralValidation(subject, revisionId, access);
    }

    @Transactional
    @Override
    public TrainingPlanningWorkflowPort.StructuralValidationSnapshot validateForWorkflow(
            String subject, UUID revisionId, long expectedVersion) {
        Access access = requireView(subject, revisionId);
        if (access.revisionVersion() != expectedVersion) {
            throw conflict("draft was modified by another editor", null);
        }
        if (!Set.of("DRAFT", "READY", "NEEDS_REVIEW", "BLOCKED").contains(access.revisionStatus())) {
            throw conflict("revision workflow state does not allow validation", null);
        }
        requireOwnerForEdit(access);
        StructuralValidationView result = performStructuralValidation(subject, revisionId, access);
        return new TrainingPlanningWorkflowPort.StructuralValidationSnapshot(
                result.result() == ValidationResult.PASS, result.violations());
    }

    private StructuralValidationView performStructuralValidation(
            String subject, UUID revisionId, Access access) {
        PlanRevisionSnapshot snapshot = requireSnapshot(revisionId);
        List<String> violations = structuralViolations(snapshot);
        String checksum = checksum(snapshot);
        var validation = new TrainingPlanningModel.StructuralValidation(UUID.randomUUID(), revisionId,
                snapshot.revisionVersion(), checksum, violations.isEmpty() ? ValidationResult.PASS : ValidationResult.FAIL,
                violations, clock.instant(), access.actor().id());
        mutate(() -> persistence.saveStructuralValidation(validation));
        audit.record(subject, "TRAINING_PLAN_STRUCTURALLY_VALIDATED", "PlanRevision", revisionId);
        return new StructuralValidationView(revisionId, snapshot.revisionVersion(), checksum,
                validation.result(), violations);
    }

    @Transactional(readOnly = true)
    public EditorView editor(String subject, UUID revisionId) {
        var access = requireView(subject, revisionId);
        return editorView(access.planId(), revisionId);
    }

    @Transactional(readOnly = true)
    public List<TrainingPlanningV2Persistence.RevisionHistoryItem> history(String subject, UUID planId) {
        requirePlanView(subject, planId);
        return persistence.revisionHistory(planId);
    }

    private List<String> structuralViolations(PlanRevisionSnapshot snapshot) {
        java.util.ArrayList<String> violations = new java.util.ArrayList<>();
        if (snapshot.goals().isEmpty()) violations.add("GOAL_REQUIRED");
        if (snapshot.cycles().isEmpty()) violations.add("CYCLE_REQUIRED");
        Set<UUID> versions = new java.util.HashSet<>();
        for (var cycle : snapshot.cycles()) {
            if (cycle.microcycles().isEmpty()) violations.add("MICROCYCLE_REQUIRED:" + cycle.id());
            for (var micro : cycle.microcycles()) {
                if (micro.sessions().isEmpty()) violations.add("SESSION_REQUIRED:" + micro.id());
                for (var session : micro.sessions()) {
                    if (session.prescriptions().isEmpty()) violations.add("PRESCRIPTION_REQUIRED:" + session.id());
                    int position = 1;
                    for (var prescription : session.prescriptions()) {
                        if (prescription.position() != position++) violations.add("PRESCRIPTION_ORDER:" + session.id());
                        versions.add(prescription.exerciseVersionId());
                    }
                }
            }
        }
        if (catalog.findPublishedVersions(versions).size() != versions.size()) {
            violations.add("UNPUBLISHED_EXERCISE_VERSION");
        }
        return List.copyOf(violations);
    }

    private Access requireEditable(String subject, UUID revisionId, Long expectedVersion) {
        if (revisionId == null || expectedVersion == null || expectedVersion < 0) {
            throw badRequest("revision id and expected version are required");
        }
        Access access = requireView(subject, revisionId);
        if (!"DRAFT".equals(access.revisionStatus())) {
            throw conflict("active or finalized revision is immutable", null);
        }
        if (access.revisionVersion() != expectedVersion) {
            throw conflict("draft was modified by another editor", null);
        }
        requireOwnerForEdit(access);
        return access;
    }

    private Access requirePlanEditor(String subject, UUID planId) {
        Access access = requirePlanView(subject, planId);
        requireOwnerForEdit(access);
        return access;
    }

    private void requireOwnerForEdit(Access access) {
        if (!access.ownerAccountId().equals(access.actor().id())) {
            throw forbidden("only the plan owner can edit it");
        }
        if (access.actor().profileType() == ProfileType.PARTICIPANT && !"SELF_DIRECTED".equals(access.mode())) {
            throw forbidden("participant can only edit a self-directed plan");
        }
    }

    private Access requireView(String subject, UUID revisionId) {
        CurrentAccount actor = accounts.requireActive(subject);
        var access = persistence.findRevisionAccess(revisionId)
                .orElseThrow(() -> notFound("plan revision not found"));
        authorizeResource(actor, access.participantAccountId());
        return new Access(actor, access.planId(), access.participantAccountId(), access.ownerAccountId(),
                access.mode(), access.status(), access.revisionNumber(), access.version());
    }

    private Access requirePlanView(String subject, UUID planId) {
        CurrentAccount actor = accounts.requireActive(subject);
        var access = persistence.findPlanAccess(planId).orElseThrow(() -> notFound("training plan not found"));
        authorizeResource(actor, access.participantAccountId());
        return new Access(actor, access.planId(), access.participantAccountId(), access.ownerAccountId(),
                access.mode(), access.status(), 0, 0);
    }

    private void authorizeResource(CurrentAccount actor, UUID participantId) {
        if (actor.profileType() == ProfileType.PARTICIPANT) {
            if (!actor.id().equals(participantId)) throw forbidden("plan belongs to another participant");
            return;
        }
        if (actor.profileType() == ProfileType.SPECIALIST) {
            relationships.requireActive(actor.id(), participantId);
            return;
        }
        throw forbidden("selected profile cannot access training plans");
    }

    private EditorView editorView(UUID planId, UUID revisionId) {
        var plan = persistence.findPlanAccess(planId).orElseThrow();
        return new EditorView(plan.planId(), plan.participantAccountId(), plan.name(), plan.purpose(),
                plan.ownerAccountId(), plan.mode(), plan.status(), plan.currentRevisionId(),
                requireSnapshot(revisionId));
    }

    private PlanRevisionSnapshot requireSnapshot(UUID revisionId) {
        return revisions.findRevision(revisionId).orElseThrow(() -> notFound("plan revision not found"));
    }

    private void requireParticipant(UUID participantId) {
        if (participants.findActiveParticipant(participantId).isEmpty()) {
            throw badRequest("active participant account is required");
        }
    }

    private static void validateDose(AddPrescriptionCommand command) {
        positiveNullable(command.sets(), "sets");
        positiveNullable(command.repetitions(), "repetitions");
        positiveNullable(command.durationSeconds(), "duration");
        positiveDecimal(command.distanceMeters(), "distance");
        positiveNullable(command.contacts(), "contacts");
        nonNegative(command.externalLoadValue(), "external load");
        if ((command.externalLoadValue() == null) != (command.externalLoadUnit() == null
                || command.externalLoadUnit().isBlank())) {
            throw badRequest("external load value and unit must be provided together");
        }
        if (command.restSeconds() != null && command.restSeconds() < 0) {
            throw badRequest("rest must not be negative");
        }
        switch (command.doseType()) {
            case DYNAMIC_RESISTANCE -> requireOnly(command.sets() != null && command.repetitions() != null
                    && command.durationSeconds() == null && command.distanceMeters() == null
                    && command.contacts() == null, "dynamic resistance requires sets and repetitions");
            case ISOMETRIC -> requireOnly(command.sets() != null && command.durationSeconds() != null
                    && command.repetitions() == null && command.distanceMeters() == null
                    && command.contacts() == null, "isometric dose requires sets and duration");
            case IMPACT -> requireOnly(command.sets() != null && command.contacts() != null
                    && command.repetitions() == null && command.durationSeconds() == null
                    && command.distanceMeters() == null, "impact dose requires sets and contacts");
            case ENDURANCE -> requireOnly(command.sets() == null && command.repetitions() == null
                    && command.contacts() == null && (command.durationSeconds() != null
                    ^ command.distanceMeters() != null), "endurance requires duration or distance");
            case MOBILITY_CONTROL -> requireOnly(command.contacts() == null && command.distanceMeters() == null
                    && (command.repetitions() != null ^ command.durationSeconds() != null),
                    "mobility/control requires repetitions or duration");
        }
        if (command.intensityType() == null) {
            if (command.intensityValue() != null || command.intensityZone() != null) {
                throw badRequest("intensity type is required for an intensity target");
            }
        } else if (command.intensityType() == IntensityType.ZONE) {
            if (command.intensityValue() != null || command.intensityZone() == null
                    || command.intensityZone().isBlank()) throw badRequest("zone intensity requires a zone");
        } else {
            if (command.intensityValue() == null || command.intensityZone() != null) {
                throw badRequest("numeric intensity requires a value");
            }
            BigDecimal value = command.intensityValue();
            if (value.signum() < 0 || command.intensityType() == IntensityType.RPE
                    && value.compareTo(BigDecimal.TEN) > 0
                    || command.intensityType() == IntensityType.PERCENT_1RM
                    && value.compareTo(BigDecimal.valueOf(100)) > 0) {
                throw badRequest("intensity value is outside range");
            }
        }
    }

    private static String checksum(PlanRevisionSnapshot snapshot) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(snapshot.toString().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private static String capability(CurrentAccount actor) {
        return actor.profileType() == ProfileType.SPECIALIST ? "SPECIALIST_LEGACY" : "SELF_DIRECTED_LEGACY";
    }

    private static void validateDateRange(LocalDate start, LocalDate end, String field) {
        if (start != null && end != null && end.isBefore(start)) throw badRequest(field + " dates are invalid");
    }

    private static void requireContained(LocalDate start, LocalDate end, LocalDate parentStart,
                                         LocalDate parentEnd, String field) {
        if (start != null && parentStart != null && start.isBefore(parentStart)
                || end != null && parentEnd != null && end.isAfter(parentEnd)) {
            throw badRequest(field + " dates must fit inside parent dates");
        }
    }

    private static String text(String value, int max, String field) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty() || normalized.length() > max) {
            throw badRequest(field + " is required and must not exceed " + max + " characters");
        }
        return normalized;
    }

    private static String optional(String value, int max, String field) {
        if (value == null || value.isBlank()) return null;
        String normalized = value.trim();
        if (normalized.length() > max) throw badRequest(field + " is too long");
        return normalized;
    }

    private static int range(Integer value, int low, int high, String field) {
        if (value == null || value < low || value > high) throw badRequest(field + " is outside range");
        return value;
    }

    private static void positive(Integer value, String field) {
        if (value == null || value <= 0) throw badRequest(field + " must be positive");
    }

    private static void positiveNullable(Integer value, String field) {
        if (value != null && value <= 0) throw badRequest(field + " must be positive");
    }

    private static void positiveDecimal(BigDecimal value, String field) {
        if (value != null && value.signum() <= 0) throw badRequest(field + " must be positive");
    }

    private static void nonNegative(BigDecimal value, String field) {
        if (value != null && value.signum() < 0) throw badRequest(field + " must not be negative");
    }

    private static void requireOnly(boolean condition, String message) {
        if (!condition) throw badRequest(message);
    }

    private static void mutate(Runnable action) {
        mutateResult(() -> { action.run(); return null; });
    }

    private static <T> T mutateResult(java.util.function.Supplier<T> action) {
        try {
            return action.get();
        } catch (TrainingPlanningV2Persistence.RevisionConflictException
                 | ObjectOptimisticLockingFailureException conflict) {
            throw conflict("draft was modified by another editor", conflict);
        } catch (TrainingPlanningV2Persistence.ImmutableRevisionException immutable) {
            throw conflict("active or finalized revision is immutable", immutable);
        } catch (DataIntegrityViolationException duplicate) {
            throw conflict("plan ordering or unique value conflicts with current draft", duplicate);
        } catch (IllegalArgumentException invalid) {
            throw badRequest(invalid.getMessage());
        }
    }

    private static ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    private static ResponseStatusException forbidden(String message) {
        return new ResponseStatusException(HttpStatus.FORBIDDEN, message);
    }

    private static ResponseStatusException notFound(String message) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, message);
    }

    private static ResponseStatusException conflict(String message, Throwable cause) {
        return new ResponseStatusException(HttpStatus.CONFLICT, message, cause);
    }

    private record Access(CurrentAccount actor, UUID planId, UUID participantAccountId,
                          UUID ownerAccountId, String mode, String revisionStatus,
                          int revisionNumber, long revisionVersion) {
    }

    public record CreateDraftCommand(UUID participantAccountId, String name, String purpose, PlanMode mode,
                                     String phaseIntent, LocalDate validFrom, LocalDate validTo) {
    }
    public record AddGoalCommand(long expectedVersion, GoalPerspective perspective, String category,
                                 String title, String description, Integer priority, GoalStatus status,
                                 LocalDate targetDate, List<OutcomeCommand> outcomes) {
    }
    public record OutcomeCommand(String metricCode, BigDecimal baseline, BigDecimal target, String unit,
                                 String measurementMethod, String evidenceSource) {
    }
    public record AddCycleCommand(long expectedVersion, Integer sequenceNumber, String name,
                                  LocalDate startDate, LocalDate endDate, String phaseIntent, String phaseGoal) {
    }
    public record AddMicrocycleCommand(long expectedVersion, UUID cycleId, Integer sequenceNumber, String name,
                                       LocalDate startDate, LocalDate endDate, String phaseIntent, String phaseGoal) {
    }
    public record AddSessionCommand(long expectedVersion, UUID microcycleId, String title,
                                    LocalDate scheduledDate, Instant availableFrom, Instant availableTo,
                                    Integer expectedDurationMinutes) {
    }
    public record AddPrescriptionCommand(long expectedVersion, UUID sessionId, UUID exerciseVersionId,
                                          Integer position, PrescriptionSide side, DoseType doseType,
                                          Integer sets, Integer repetitions, Integer durationSeconds,
                                          BigDecimal distanceMeters, Integer contacts,
                                          BigDecimal externalLoadValue, String externalLoadUnit,
                                          IntensityType intensityType, BigDecimal intensityValue,
                                          String intensityZone, String tempo, String rangeOfMotion,
                                          Integer restSeconds, String substituteGroup, String notes) {
    }
    public record ReorderCommand(long expectedVersion, UUID sessionId, List<UUID> prescriptionIds) {
    }
    public record AddLoadBudgetCommand(long expectedVersion, String channel, BigDecimal low,
                                       BigDecimal high, String unit, BudgetAction action) {
    }
    public record CreateRevisionCommand(UUID basedOnRevisionId) {
    }
    public record ValidateCommand(long expectedVersion) {
    }
    public record EditorView(UUID planId, UUID participantAccountId, String name, String purpose,
                             UUID ownerAccountId, String mode, String planStatus, UUID currentRevisionId,
                             PlanRevisionSnapshot revision) {
    }
    public record StructuralValidationView(UUID revisionId, long draftVersion, String inputChecksum,
                                           ValidationResult result, List<String> violations) {
        public StructuralValidationView { violations = List.copyOf(violations); }
    }
}
