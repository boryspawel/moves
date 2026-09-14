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
import com.motionecosystem.identityaccess.api.CurrentAccount;
import com.motionecosystem.identityaccess.api.CurrentAccountService;
import com.motionecosystem.identityaccess.api.ProfileType;
import com.motionecosystem.identityaccess.api.SpecialistAuthorizationPort;
import com.motionecosystem.identityaccess.api.SpecialistAuthorizationPort.ActingContext;
import com.motionecosystem.identityaccess.api.SpecialistAuthorizationPort.Capability;
import com.motionecosystem.identityaccess.api.SpecialistAuthorizationPort.ProfessionalRole;
import com.motionecosystem.identityaccess.api.SpecialistAuthorizationPort.Purpose;
import com.motionecosystem.participant.api.ParticipantClientPort;
import com.motionecosystem.participantgoals.api.ParticipantGoalQueryPort;
import com.motionecosystem.exercisesets.api.ExerciseSetVersionQueryPort;
import tools.jackson.databind.ObjectMapper;
import com.motionecosystem.trainingplanning.TrainingPlanningModel.BudgetAction;
import com.motionecosystem.trainingplanning.TrainingPlanningModel.DoseType;
import com.motionecosystem.trainingplanning.TrainingPlanningModel.GoalPerspective;
import com.motionecosystem.trainingplanning.TrainingPlanningModel.GoalStatus;
import com.motionecosystem.trainingplanning.TrainingPlanningModel.IntensityType;
import com.motionecosystem.trainingplanning.TrainingPlanningModel.PlanMode;
import com.motionecosystem.trainingplanning.TrainingPlanningModel.PlanStatus;
import com.motionecosystem.trainingplanning.TrainingPlanningModel.PrescriptionSide;
import com.motionecosystem.trainingplanning.TrainingPlanningModel.RevisionStatus;
import com.motionecosystem.trainingplanning.TrainingPlanningModel.SessionVariantType;
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
    private final ParticipantClientPort participants;
    private final SpecialistAuthorizationPort authorization;
    private final PlanCollaborationPersistence collaborations;
    private final ExerciseCatalogQueryPort catalog;
    private final ParticipantGoalQueryPort participantGoals;
    private final ExerciseSetVersionQueryPort exerciseSetVersions;
    private final ObjectMapper json;
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
        UUID participantId = command.participantId();
        PlanMode mode = command.mode();
        if (actor.profileType() == ProfileType.PARTICIPANT) {
            throw new ResponseStatusException(HttpStatus.GONE,
                    "new self-directed planning is historical-only; specialist authoring is required");
        } else if (actor.profileType() == ProfileType.SPECIALIST) {
            if (participantId == null) {
                throw badRequest("participantId is required");
            }
            mode = mode == null ? PlanMode.SPECIALIST : mode;
            if (mode == PlanMode.SELF_DIRECTED) {
                throw badRequest("new self-directed planning is historical-only; specialist authoring is required");
            }
            requireSpecialistPlanning(actor.id(), participantId, command.actingContext());
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
                command.validFrom(), command.validTo(), actor.id(), capability(actor, command.actingContext()),
                "NATIVE_V2", "NOT_ASSESSED", now, now, 0);
        mutate(() -> persistence.createDraft(plan, revision));
        audit.record(subject, "TRAINING_PLAN_DRAFT_CREATED", "TrainingPlan", planId);
        return editorView(planId, revisionId);
    }

    @Transactional
    public EditorView addGoal(String subject, UUID revisionId, AddGoalCommand command) {
        var access = requireEditable(subject, revisionId, command == null ? null : command.expectedVersion());
        if (command.participantGoalId() == null) throw badRequest("participantGoalId is required");
        var source = participantGoals.findById(command.participantGoalId())
                .orElseThrow(() -> badRequest("participant goal not found"));
        if (!access.participantId().equals(source.participantId())) {
            throw forbidden("participant goal belongs to another participant");
        }
        if (!"ACTIVE".equals(source.status())) throw badRequest("participant goal must be active");
        GoalPerspective perspective = goalPerspective(source.category());
        if (access.actingRole() == ProfessionalRole.TRAINER && perspective != GoalPerspective.PERFORMANCE
                || access.actingRole() == ProfessionalRole.PHYSIOTHERAPIST && perspective != GoalPerspective.FUNCTIONAL_RECOVERY) {
            throw forbidden("participant goal category is incompatible with the specialist planning context");
        }
        Instant now = clock.instant();
        UUID goalId = UUID.randomUUID();
        var goal = new TrainingPlanningModel.Goal(goalId, revisionId, access.participantId(),
                perspective, source.category(), source.title(), source.description(), source.priority(),
                GoalStatus.valueOf(source.status()), source.targetDate(), access.actor().id(), now,
                source.id(), source.version(), now);
        List<TrainingPlanningModel.GoalOutcome> outcomes = source.outcomes().stream().map(item -> {
            return new TrainingPlanningModel.GoalOutcome(UUID.randomUUID(), goalId,
                    item.metricCode(), item.baseline(), item.targetValue(), item.unit(),
                    "participant-goal:" + item.metricCode(), null);
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
        if (command.exerciseSetVersionId() == null) throw badRequest("exerciseSetVersionId is required");
        var source = exerciseSetVersions.findById(command.exerciseSetVersionId())
                .orElseThrow(() -> badRequest("exercise set version not found"));
        if (!"PUBLISHED".equals(source.status())) throw badRequest("exercise set version must be published");
        if (!access.actor().id().equals(source.ownerAccountId())) throw forbidden("exercise set belongs to another specialist");
        UUID sessionId = UUID.randomUUID();
        var session = new TrainingPlanningModel.Session(sessionId, command.microcycleId(),
                access.participantId(), text(command.title(), 160, "session title"), command.scheduledDate(),
                command.availableFrom(), command.availableTo(), command.expectedDurationMinutes(), clock.instant(),
                source.exerciseSetId(), source.exerciseSetVersionId(), sourceSnapshot(source));
        List<TrainingPlanningModel.Prescription> prescriptions = source.items().stream()
                .map(item -> materialize(sessionId, source.exerciseSetVersionId(), item)).toList();
        mutate(() -> persistence.addSession(revisionId, command.expectedVersion(), session, prescriptions, clock.instant()));
        return editorView(access.planId(), revisionId);
    }

    /** Updates draft scheduling in place; changing the source deliberately rematerializes it and clears variants. */
    @Transactional
    public EditorView updateSession(String subject, UUID revisionId, UpdateSessionCommand command) {
        var access = requireEditable(subject, revisionId, command == null ? null : command.expectedVersion());
        if (command.sessionId() == null) throw badRequest("sessionId is required");
        positive(command.expectedDurationMinutes(), "expected duration");
        if (command.availableFrom() != null && command.availableTo() != null
                && command.availableTo().isBefore(command.availableFrom())) {
            throw badRequest("session availability end precedes start");
        }
        var snapshot = requireSnapshot(revisionId);
        var parent = snapshot.cycles().stream().flatMap(cycle -> cycle.microcycles().stream())
                .filter(microcycle -> microcycle.sessions().stream().anyMatch(session -> session.id().equals(command.sessionId())))
                .findFirst().orElseThrow(() -> badRequest("session does not belong to revision"));
        var existing = parent.sessions().stream().filter(session -> session.id().equals(command.sessionId())).findFirst().orElseThrow();
        if (command.scheduledDate() != null) {
            requireContained(command.scheduledDate(), command.scheduledDate(), parent.startDate(), parent.endDate(), "session");
        }
        UUID sourceVersionId = command.exerciseSetVersionId() == null
                ? existing.sourceExerciseSetVersionId() : command.exerciseSetVersionId();
        if (sourceVersionId == null) throw badRequest("exerciseSetVersionId is required to replace a legacy session source");
        boolean sourceChanged = !sourceVersionId.equals(existing.sourceExerciseSetVersionId());
        ExerciseSetVersionQueryPort.ExerciseSetVersionSnapshot source = null;
        if (sourceChanged) {
            source = exerciseSetVersions.findById(sourceVersionId)
                    .orElseThrow(() -> badRequest("exercise set version not found"));
            if (!"PUBLISHED".equals(source.status())) throw badRequest("exercise set version must be published");
            if (!access.actor().id().equals(source.ownerAccountId())) throw forbidden("exercise set belongs to another specialist");
        }
        var updated = new TrainingPlanningModel.Session(existing.id(), parent.id(), access.participantId(),
                text(command.title(), 160, "session title"), command.scheduledDate(), command.availableFrom(),
                command.availableTo(), command.expectedDurationMinutes(), clock.instant(),
                sourceChanged ? source.exerciseSetId() : existing.sourceExerciseSetId(), sourceVersionId,
                sourceChanged ? sourceSnapshot(source) : existing.sourceSnapshot());
        List<TrainingPlanningModel.Prescription> replacement = sourceChanged
                ? source.items().stream().map(item -> materialize(existing.id(), sourceVersionId, item)).toList()
                : null;
        mutate(() -> persistence.updateSession(revisionId, command.expectedVersion(), updated, replacement, clock.instant()));
        return editorView(access.planId(), revisionId);
    }

    @Transactional
    public EditorView updatePeriod(String subject, UUID revisionId, UpdatePeriodCommand command) {
        var access = requireEditable(subject, revisionId, command == null ? null : command.expectedVersion());
        validateDateRange(command.validFrom(), command.validTo(), "revision");
        var snapshot = requireSnapshot(revisionId);
        boolean defaultStage = snapshot.cycles().size() == 1 && snapshot.cycles().getFirst().microcycles().size() == 1;
        for (var cycle : snapshot.cycles()) {
            if (!defaultStage) {
                requireContained(cycle.startDate(), cycle.endDate(), command.validFrom(), command.validTo(), "existing cycle");
            }
            for (var micro : cycle.microcycles()) for (var session : micro.sessions()) {
                if (session.scheduledDate() != null) requireContained(session.scheduledDate(), session.scheduledDate(),
                        command.validFrom(), command.validTo(), "existing session");
                if (session.availableFrom() != null && session.availableFrom().atZone(java.time.ZoneOffset.UTC).toLocalDate().isBefore(command.validFrom())
                        || session.availableTo() != null && session.availableTo().atZone(java.time.ZoneOffset.UTC).toLocalDate().isAfter(command.validTo())) {
                    throw badRequest("period excludes an existing session availability window");
                }
            }
        }
        mutate(() -> persistence.updatePeriod(revisionId, command.expectedVersion(), command.validFrom(), command.validTo(), defaultStage, clock.instant()));
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
    public EditorView defineSessionVariant(String subject, UUID revisionId, DefineSessionVariantCommand command) {
        var access = requireEditable(subject, revisionId, command == null ? null : command.expectedVersion());
        if (command.sessionId() == null || command.type() == null || command.type() == SessionVariantType.STANDARD
                || command.items() == null || command.items().isEmpty()) throw badRequest("a SHORT or MINIMUM session variant requires items");
        if (command.expectedDurationMinutes() != null) positive(command.expectedDurationMinutes(), "variant duration");
        var session = requireSnapshot(revisionId).cycles().stream().flatMap(cycle -> cycle.microcycles().stream())
                .flatMap(microcycle -> microcycle.sessions().stream()).filter(item -> item.id().equals(command.sessionId()))
                .findFirst().orElseThrow(() -> badRequest("session does not belong to revision"));
        if (session.variants().stream().anyMatch(item -> item.type().equals(command.type().name()))) throw conflict("session variant already exists", null);
        Set<UUID> prescriptions = session.prescriptions().stream().map(PlanRevisionQueryPort.PrescriptionSnapshot::id).collect(java.util.stream.Collectors.toSet());
        Set<UUID> selected = command.items().stream().map(VariantItemCommand::basePrescriptionId).collect(java.util.stream.Collectors.toSet());
        Set<Integer> positions = command.items().stream().map(VariantItemCommand::position).collect(java.util.stream.Collectors.toSet());
        if (selected.size() != command.items().size() || !prescriptions.containsAll(selected) || positions.contains(null)
                || positions.size() != command.items().size() || positions.stream().anyMatch(value -> value < 1)) {
            throw badRequest("variant items must be unique, ordered session prescriptions");
        }
        UUID variantId = UUID.randomUUID();
        var variant = new TrainingPlanningModel.SessionVariant(variantId, command.sessionId(), command.type(), command.expectedDurationMinutes());
        var items = command.items().stream().map(item -> new TrainingPlanningModel.SessionVariantItem(
                UUID.randomUUID(), variantId, item.basePrescriptionId(), item.position(), null, null, null, null)).toList();
        mutate(() -> persistence.defineSessionVariant(revisionId, command.expectedVersion(), variant, items, clock.instant()));
        return editorView(access.planId(), revisionId);
    }

    @Transactional
    public EditorView addLoadBudget(String subject, UUID revisionId, AddLoadBudgetCommand command) {
        var access = requireEditable(subject, revisionId, command == null ? null : command.expectedVersion());
        if (access.actor().hasProfile(ProfileType.SPECIALIST)) {
            if (access.actingRole() != ProfessionalRole.TRAINER) {
                throw forbidden("only trainer context can set a performance load budget");
            }
            authorization.requireCapabilities(access.actor().id(), access.participantId(),
                    new ActingContext(access.actingRole()), Set.of(Capability.SET_PERFORMANCE_BUDGET),
                    Purpose.PERFORMANCE_PLANNING);
        }
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
    public EditorView deleteGoal(String subject, UUID revisionId, DeleteGoalCommand command) {
        var access = requireEditable(subject, revisionId, command == null ? null : command.expectedVersion());
        if (command.goalId() == null) throw badRequest("goalId is required");
        mutate(() -> persistence.deleteGoal(revisionId, command.expectedVersion(), command.goalId(), clock.instant()));
        return editorView(access.planId(), revisionId);
    }

    @Transactional
    public EditorView deleteSession(String subject, UUID revisionId, DeleteSessionCommand command) {
        var access = requireEditable(subject, revisionId, command == null ? null : command.expectedVersion());
        if (command.sessionId() == null) throw badRequest("sessionId is required");
        mutate(() -> persistence.deleteSession(revisionId, command.expectedVersion(), command.sessionId(), clock.instant()));
        return editorView(access.planId(), revisionId);
    }

    @Transactional
    public EditorView createRevision(String subject, UUID planId, CreateRevisionCommand command) {
        if (command == null || command.basedOnRevisionId() == null) {
            throw badRequest("base revision is required");
        }
        Access access = requirePlanEditor(subject, planId);
        if (access.actor().hasProfile(ProfileType.SPECIALIST)
                && (command.actingContext() == null
                || command.actingContext().role() != access.actingRole())) {
            throw forbidden("revision requires the explicit plan owner acting context");
        }
        UUID revisionId = mutateResult(() -> persistence.cloneRevision(planId, command.basedOnRevisionId(),
                access.actor().id(), capability(access.actor(), command.actingContext()), clock.instant()));
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
    public PlanRevisionSnapshot participantRevision(String subject, UUID revisionId) {
        CurrentAccount account = accounts.requireActive(subject);
        if (!account.hasProfile(ProfileType.PARTICIPANT)) throw forbidden("participant profile is required");
        UUID participantId = participantIdFor(account);
        PlanRevisionSnapshot revision = revisions.findRevision(revisionId).orElseThrow(() -> notFound("plan revision not found"));
        if (!participantId.equals(revision.participantId())) throw forbidden("plan revision belongs to another participant");
        return revision;
    }

    @Transactional(readOnly = true)
    public List<TrainingPlanningV2Persistence.RevisionHistoryItem> history(String subject, UUID planId) {
        requirePlanView(subject, planId);
        return persistence.revisionHistory(planId);
    }

    @Transactional(readOnly = true)
    public List<PlanListItem> participantPlans(String subject, UUID participantId, ActingContext actingContext) {
        CurrentAccount actor = accounts.requireActive(subject);
        if (!actor.hasProfile(ProfileType.SPECIALIST) || participantId == null) {
            throw forbidden("specialist participant planning access is required");
        }
        requireSpecialistPlanning(actor.id(), participantId, actingContext);
        return persistence.plansForParticipant(participantId).stream().flatMap(plan -> {
            try {
                authorizeResource(actor, plan.planId(), participantId, plan.ownerAccountId(), plan.ownerCapability());
                return java.util.stream.Stream.of(new PlanListItem(plan.planId(), plan.name(), plan.purpose(),
                        plan.status(), plan.currentRevisionId()));
            } catch (ResponseStatusException ignored) {
                return java.util.stream.Stream.empty();
            }
        }).toList();
    }

    private List<String> structuralViolations(PlanRevisionSnapshot snapshot) {
        java.util.ArrayList<String> violations = new java.util.ArrayList<>();
        if (snapshot.goals().isEmpty()) violations.add("GOAL_REQUIRED");
        for (var goal : snapshot.goals()) {
            if (goal.sourceParticipantGoalId() == null) {
                violations.add("LEGACY_GOAL_SOURCE_REQUIRED:" + goal.id());
                continue;
            }
            var source = participantGoals.findById(goal.sourceParticipantGoalId()).orElse(null);
            if (source == null || !source.participantId().equals(snapshot.participantId())
                    || !"ACTIVE".equals(source.status())) {
                violations.add("PARTICIPANT_GOAL_REVALIDATION_REQUIRED:" + goal.id());
            }
        }
        if (snapshot.cycles().isEmpty()) violations.add("CYCLE_REQUIRED");
        Set<UUID> versions = new java.util.HashSet<>();
        for (var cycle : snapshot.cycles()) {
            if (cycle.microcycles().isEmpty()) violations.add("MICROCYCLE_REQUIRED:" + cycle.id());
            for (var micro : cycle.microcycles()) {
                if (micro.sessions().isEmpty()) violations.add("SESSION_REQUIRED:" + micro.id());
                for (var session : micro.sessions()) {
                    if (session.sourceExerciseSetVersionId() == null) {
                        violations.add("LEGACY_EXERCISE_SET_SOURCE_REQUIRED:" + session.id());
                    } else {
                        var source = exerciseSetVersions.findById(session.sourceExerciseSetVersionId()).orElse(null);
                        if (source == null || !"PUBLISHED".equals(source.status())
                                || !source.exerciseSetId().equals(session.sourceExerciseSetId())
                                || !matchesMaterialization(session, source)) {
                            violations.add("EXERCISE_SET_VERSION_REVALIDATION_REQUIRED:" + session.id());
                        }
                    }
                    if (session.sourceSnapshot() == null) violations.add("SESSION_SOURCE_SNAPSHOT_REQUIRED:" + session.id());
                    if (session.prescriptions().isEmpty()) violations.add("PRESCRIPTION_REQUIRED:" + session.id());
                    int position = 1;
                    for (var prescription : session.prescriptions()) {
                        if (prescription.position() != position++) violations.add("PRESCRIPTION_ORDER:" + session.id());
                        if (prescription.materializedSnapshot() == null || prescription.sourceExerciseSetVersionId() == null
                                || !session.sourceExerciseSetVersionId().equals(prescription.sourceExerciseSetVersionId())) {
                            violations.add("MATERIALIZED_PRESCRIPTION_REQUIRED:" + prescription.id());
                        }
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
        if ("SELF_DIRECTED".equals(access.mode())) {
            throw new ResponseStatusException(HttpStatus.GONE,
                    "self-directed plans are historical-only and cannot be authored or revised");
        }
        if (!access.ownerAccountId().equals(access.actor().id())
                && !access.collaborationScopes().contains("EDIT_DRAFT")) {
            throw forbidden("plan edit requires ownership or EDIT_DRAFT collaboration scope");
        }
        if (access.actor().profileType() == ProfileType.PARTICIPANT) {
            throw forbidden("participants cannot author training plans");
        }
    }

    private Access requireView(String subject, UUID revisionId) {
        CurrentAccount actor = accounts.requireActive(subject);
        var access = persistence.findRevisionAccess(revisionId)
                .orElseThrow(() -> notFound("plan revision not found"));
        var resource = authorizeResource(actor, access.planId(), access.participantId(),
                access.ownerAccountId(), access.authorCapability());
        return new Access(actor, access.planId(), access.participantId(), access.ownerAccountId(),
                access.mode(), access.status(), access.revisionNumber(), access.version(),
                resource.role(), resource.scopes());
    }

    private Access requirePlanView(String subject, UUID planId) {
        CurrentAccount actor = accounts.requireActive(subject);
        var access = persistence.findPlanAccess(planId).orElseThrow(() -> notFound("training plan not found"));
        var resource = authorizeResource(actor, access.planId(), access.participantId(),
                access.ownerAccountId(), access.ownerCapability());
        return new Access(actor, access.planId(), access.participantId(), access.ownerAccountId(),
                access.mode(), access.status(), 0, 0, resource.role(), resource.scopes());
    }

    private ResourceAuthorization authorizeResource(CurrentAccount actor, UUID planId, UUID participantId,
                                                     UUID ownerId, String ownerCapability) {
        if (actor.profileType() == ProfileType.PARTICIPANT) {
            if (!actor.id().equals(participantId)) throw forbidden("plan belongs to another participant");
            return new ResourceAuthorization(null, Set.of());
        }
        if (actor.profileType() == ProfileType.SPECIALIST) {
            ProfessionalRole role;
            Set<String> scopes;
            if (actor.id().equals(ownerId)) {
                role = role(ownerCapability);
                scopes = Set.of("VIEW_PLAN", "EDIT_DRAFT", "REVIEW_SAFETY");
            } else {
                var collaborator = collaborations.findActiveCollaborator(planId, actor.id())
                        .filter(item -> item.scopes().contains("VIEW_PLAN")
                                || item.scopes().contains("EDIT_DRAFT")
                                || item.scopes().contains("REVIEW_SAFETY"))
                        .orElseThrow(() -> forbidden("active plan collaboration scope is required"));
                role = ProfessionalRole.valueOf(collaborator.professionalRole());
                scopes = collaborator.scopes();
            }
            requireSpecialistPlanning(actor.id(), participantId, new ActingContext(role));
            return new ResourceAuthorization(role, scopes);
        }
        throw forbidden("selected profile cannot access training plans");
    }

    private EditorView editorView(UUID planId, UUID revisionId) {
        var plan = persistence.findPlanAccess(planId).orElseThrow();
        return new EditorView(plan.planId(), plan.participantId(), plan.name(), plan.purpose(),
                plan.ownerAccountId(), plan.mode(), plan.status(), plan.currentRevisionId(),
                requireSnapshot(revisionId));
    }

    private PlanRevisionSnapshot requireSnapshot(UUID revisionId) {
        return revisions.findRevision(revisionId).orElseThrow(() -> notFound("plan revision not found"));
    }

    private void requireParticipant(UUID participantId) {
        if (participants.find(participantId)
                .filter(value -> value.recordStatus() == ParticipantClientPort.RecordStatus.ACTIVE).isEmpty()) {
            throw badRequest("active participant record is required");
        }
    }

    private UUID participantIdFor(CurrentAccount account) {
        return participants.findParticipantIdByPrincipalAccountId(account.id())
                .orElseThrow(() -> forbidden("an active participant access link is required"));
    }

    static String checksum(PlanRevisionSnapshot snapshot) {
        try {
            String content = snapshot.revisionId() + "|" + snapshot.planId() + "|"
                    + snapshot.participantId() + "|" + snapshot.phaseIntent() + "|"
                    + snapshot.validFrom() + "|" + snapshot.validTo() + "|" + snapshot.goals()
                    + "|" + snapshot.cycles() + "|" + snapshot.loadBudgets();
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(content.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private TrainingPlanningModel.Prescription materialize(UUID sessionId, UUID setVersionId,
                                                            ExerciseSetVersionQueryPort.ItemSnapshot item) {
        ExerciseSetVersionQueryPort.DoseSnapshot dose = item.dose();
        Integer sets = null, repetitions = null, duration = null, contacts = null, rest = null;
        BigDecimal distance = null, load = null, intensityValue = null;
        String loadUnit = null, intensityZone = null, tempo = null, range = null;
        PrescriptionSide side = PrescriptionSide.NOT_APPLICABLE;
        DoseType type;
        if (dose instanceof ExerciseSetVersionQueryPort.StrengthDoseSnapshot value) {
            type = value.reps() == null ? null : DoseType.DYNAMIC_RESISTANCE; sets = value.sets(); repetitions = value.reps();
            rest = value.restSeconds(); tempo = value.tempo(); load = value.loadValue(); loadUnit = value.loadUnit();
            intensityValue = value.rpe(); side = side(value.side());
        } else if (dose instanceof ExerciseSetVersionQueryPort.IsometricDoseSnapshot value) {
            type = DoseType.ISOMETRIC; sets = value.sets(); duration = value.holdSeconds(); rest = value.restSeconds(); side = side(value.side());
        } else if (dose instanceof ExerciseSetVersionQueryPort.MobilityDoseSnapshot value) {
            type = DoseType.MOBILITY_CONTROL; repetitions = value.reps(); duration = value.durationSeconds(); tempo = value.tempo(); range = value.rangeTarget(); side = side(value.side());
        } else if (dose instanceof ExerciseSetVersionQueryPort.AerobicDoseSnapshot value) {
            type = DoseType.ENDURANCE; duration = value.durationSeconds(); distance = value.distanceMeters() == null ? null : BigDecimal.valueOf(value.distanceMeters()); intensityZone = value.zone(); intensityValue = value.rpe();
        } else if (dose instanceof ExerciseSetVersionQueryPort.BreathingDoseSnapshot value) {
            type = value.durationSeconds() == null ? null : DoseType.ENDURANCE; duration = value.durationSeconds();
        } else if (dose instanceof ExerciseSetVersionQueryPort.StretchDoseSnapshot value) {
            type = null; side = side(value.side());
        } else {
            throw badRequest("unsupported exercise set dose");
        }
        String snapshot;
        try { snapshot = json.writeValueAsString(item); }
        catch (Exception failure) { throw new IllegalStateException("cannot materialize exercise set item", failure); }
        return new TrainingPlanningModel.Prescription(UUID.randomUUID(), sessionId, item.exerciseVersionId(),
                item.position(), side, type == null ? DoseType.DYNAMIC_RESISTANCE : type, sets, repetitions, duration, distance, contacts, load, loadUnit,
                intensityZone == null && intensityValue == null ? null : intensityZone == null ? IntensityType.RPE : IntensityType.ZONE,
                intensityValue, intensityZone, tempo, range, rest, null, item.participantInstruction(),
                item.itemId(), setVersionId, dose.type(), snapshot);
    }

    private String sourceSnapshot(ExerciseSetVersionQueryPort.ExerciseSetVersionSnapshot source) {
        try { return json.writeValueAsString(source); }
        catch (Exception failure) { throw new IllegalStateException("cannot materialize exercise set version snapshot", failure); }
    }

    private static boolean matchesMaterialization(PlanRevisionQueryPort.SessionSnapshot session,
                                                   ExerciseSetVersionQueryPort.ExerciseSetVersionSnapshot source) {
        if (session.prescriptions().size() != source.items().size()) return false;
        java.util.Map<UUID, ExerciseSetVersionQueryPort.ItemSnapshot> items = source.items().stream()
                .collect(java.util.stream.Collectors.toMap(ExerciseSetVersionQueryPort.ItemSnapshot::itemId, item -> item));
        return session.prescriptions().stream().allMatch(prescription -> {
            var item = items.get(prescription.sourceExerciseSetItemId());
            return item != null && item.exerciseVersionId().equals(prescription.exerciseVersionId())
                    && source.exerciseSetVersionId().equals(prescription.sourceExerciseSetVersionId());
        });
    }

    private static PrescriptionSide side(String value) {
        return value == null ? PrescriptionSide.NOT_APPLICABLE : PrescriptionSide.valueOf(value);
    }

    private static GoalPerspective goalPerspective(String category) {
        return switch (category) {
            case "PERFORMANCE" -> GoalPerspective.PERFORMANCE;
            case "FUNCTIONAL" -> GoalPerspective.FUNCTIONAL_RECOVERY;
            case "GENERAL_FITNESS" -> throw badRequest("GENERAL_FITNESS goals are not supported by specialist planning");
            default -> throw badRequest("participant goal category is unsupported by planning");
        };
    }

    private void requireSpecialistPlanning(UUID actor, UUID participant, ActingContext context) {
        if (context == null || context.role() == null) {
            throw forbidden("explicit specialist acting context is required");
        }
        authorization.requireCapabilities(actor, participant, context,
                Set.of(PlanCollaborationService.planCapability(context.role())),
                PlanCollaborationService.purpose(context.role()));
    }

    private static ProfessionalRole role(String capability) {
        return Capability.PLAN_FUNCTIONAL_RECOVERY.name().equals(capability)
                ? ProfessionalRole.PHYSIOTHERAPIST : ProfessionalRole.TRAINER;
    }

    private static String capability(CurrentAccount actor, ActingContext context) {
        if (actor.profileType() != ProfileType.SPECIALIST) return "PARTICIPANT_SELF_DIRECTED";
        if (context == null || context.role() == null) throw forbidden("explicit specialist acting context is required");
        return PlanCollaborationService.planCapability(context.role()).name();
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

    private record Access(CurrentAccount actor, UUID planId, UUID participantId,
                          UUID ownerAccountId, String mode, String revisionStatus,
                          int revisionNumber, long revisionVersion, ProfessionalRole actingRole,
                          Set<String> collaborationScopes) {
    }

    private record ResourceAuthorization(ProfessionalRole role, Set<String> scopes) { }

    public record CreateDraftCommand(UUID participantId, String name, String purpose, PlanMode mode,
                                     String phaseIntent, LocalDate validFrom, LocalDate validTo,
                                     ActingContext actingContext) {
        public CreateDraftCommand(UUID participantId, String name, String purpose, PlanMode mode,
                                  String phaseIntent, LocalDate validFrom, LocalDate validTo) {
            this(participantId, name, purpose, mode, phaseIntent, validFrom, validTo, null);
        }
    }
    public record AddGoalCommand(long expectedVersion, UUID participantGoalId) { }
    public record AddCycleCommand(long expectedVersion, Integer sequenceNumber, String name,
                                  LocalDate startDate, LocalDate endDate, String phaseIntent, String phaseGoal) {
    }
    public record AddMicrocycleCommand(long expectedVersion, UUID cycleId, Integer sequenceNumber, String name,
                                       LocalDate startDate, LocalDate endDate, String phaseIntent, String phaseGoal) {
    }
    public record DeleteGoalCommand(long expectedVersion, UUID goalId) { }
    public record DeleteSessionCommand(long expectedVersion, UUID sessionId) { }
    public record AddSessionCommand(long expectedVersion, UUID microcycleId, String title,
                                    LocalDate scheduledDate, Instant availableFrom, Instant availableTo,
                                    Integer expectedDurationMinutes, UUID exerciseSetVersionId) { }
    public record UpdateSessionCommand(long expectedVersion, UUID sessionId, String title,
                                       LocalDate scheduledDate, Instant availableFrom, Instant availableTo,
                                       Integer expectedDurationMinutes, UUID exerciseSetVersionId) { }
    public record UpdatePeriodCommand(long expectedVersion, LocalDate validFrom, LocalDate validTo) { }
    public record ReorderCommand(long expectedVersion, UUID sessionId, List<UUID> prescriptionIds) {
    }
    public record DefineSessionVariantCommand(long expectedVersion, UUID sessionId, SessionVariantType type,
                                              Integer expectedDurationMinutes, List<VariantItemCommand> items) {
    }
    public record VariantItemCommand(UUID basePrescriptionId, Integer position) { }
    public record AddLoadBudgetCommand(long expectedVersion, String channel, BigDecimal low,
                                       BigDecimal high, String unit, BudgetAction action) {
    }
    public record CreateRevisionCommand(UUID basedOnRevisionId, ActingContext actingContext) {
        public CreateRevisionCommand(UUID basedOnRevisionId) { this(basedOnRevisionId, null); }
    }
    public record ValidateCommand(long expectedVersion) {
    }
    public record EditorView(UUID planId, UUID participantId, String name, String purpose,
                             UUID ownerAccountId, String mode, String planStatus, UUID currentRevisionId,
                             PlanRevisionSnapshot revision) {
    }
    public record PlanListItem(UUID planId, String name, String purpose, String status, UUID currentRevisionId) { }
    public record StructuralValidationView(UUID revisionId, long draftVersion, String inputChecksum,
                                           ValidationResult result, List<String> violations) {
        public StructuralValidationView { violations = List.copyOf(violations); }
    }
}
