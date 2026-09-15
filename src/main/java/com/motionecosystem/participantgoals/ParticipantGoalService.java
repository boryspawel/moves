package com.motionecosystem.participantgoals;

import com.motionecosystem.audit.AuditRecorder;
import com.motionecosystem.identityaccess.api.CurrentAccountService;
import com.motionecosystem.identityaccess.api.ProfileType;
import com.motionecosystem.participantgoals.api.ParticipantGoalQueryPort;
import com.motionecosystem.participant.api.ParticipantClientPort;
import com.motionecosystem.identityaccess.api.SpecialistAuthorizationPort;
import com.motionecosystem.identityaccess.api.SpecialistAuthorizationPort.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ParticipantGoalService implements ParticipantGoalQueryPort {
    private final ParticipantGoalRepository goals;
    private final GoalOutcomeRepository outcomes;
    private final GoalIdempotencyRepository idempotency;
    private final GoalObservationRepository observations;
    private final GoalObservationIdempotencyRepository observationIdempotency;
    private final ParticipantGoalEventRepository events;
    private final CurrentAccountService accounts;
    private final SpecialistAuthorizationPort authorization;
    private final ParticipantClientPort participants;
    private final AuditRecorder audit;
    private final Clock clock;
    private final ParticipantGoalLifecyclePolicy lifecycle = new ParticipantGoalLifecyclePolicy();

    @Autowired
    public ParticipantGoalService(ParticipantGoalRepository goals, GoalOutcomeRepository outcomes, GoalIdempotencyRepository idempotency,
            GoalObservationRepository observations, GoalObservationIdempotencyRepository observationIdempotency, ParticipantGoalEventRepository events,
            CurrentAccountService accounts, SpecialistAuthorizationPort authorization, ParticipantClientPort participants,
            AuditRecorder audit, Clock clock) {
        this.goals = goals; this.outcomes = outcomes; this.idempotency = idempotency; this.observations = observations;
        this.observationIdempotency = observationIdempotency; this.events = events; this.accounts = accounts;
        this.authorization = authorization; this.participants = participants; this.audit = audit; this.clock = clock;
    }

    /** Compatibility constructor for existing focused callers; observation operations require the full constructor. */
    ParticipantGoalService(ParticipantGoalRepository goals, GoalOutcomeRepository outcomes, GoalIdempotencyRepository idempotency,
            CurrentAccountService accounts, SpecialistAuthorizationPort authorization,
            AuditRecorder audit, Clock clock) {
        this(goals, outcomes, idempotency, null, null, null, accounts, authorization, null, audit, clock);
    }

    ParticipantGoalService(ParticipantGoalRepository goals, GoalOutcomeRepository outcomes, GoalIdempotencyRepository idempotency,
            GoalObservationRepository observations, GoalObservationIdempotencyRepository observationIdempotency,
            CurrentAccountService accounts, SpecialistAuthorizationPort authorization,
            AuditRecorder audit, Clock clock) {
        this(goals, outcomes, idempotency, observations, observationIdempotency, null, accounts, authorization, null, audit, clock);
    }

    ParticipantGoalService(ParticipantGoalRepository goals, GoalOutcomeRepository outcomes, GoalIdempotencyRepository idempotency,
            GoalObservationRepository observations, GoalObservationIdempotencyRepository observationIdempotency, ParticipantGoalEventRepository events,
            CurrentAccountService accounts, SpecialistAuthorizationPort authorization,
            AuditRecorder audit, Clock clock) {
        this(goals, outcomes, idempotency, observations, observationIdempotency, events, accounts, authorization, null, audit, clock);
    }

    @Transactional
    public ParticipantGoalView create(String subject, UUID participantId, ActingContext context, String key, CreateParticipantGoalCommand command) {
        UUID specialist = authorize(subject, participantId, context, command == null ? null : command.category());
        String idempotencyKey = key(key); String operation = "CREATE:" + participantId;
        return replay(specialist, operation, idempotencyKey).orElseGet(() -> {
            Values values = values(command);
            Instant now = clock.instant();
            ParticipantGoal saved = goals.saveAndFlush(new ParticipantGoal(participantId, specialist, values.category, values.title, values.description, values.priority, values.targetDate, now));
            for (int position = 0; position < values.outcomes.size(); position++) {
                OutcomeCommand outcome = values.outcomes.get(position);
                outcomes.save(new GoalOutcome(saved.id, outcome.metricCode().trim(), outcome.baseline(), outcome.targetValue(), outcome.unit().trim(), textOptional(outcome.measurementMethod(), 120, "outcome measurementMethod"), outcome.targetComparator(), position, now));
            }
            remember(specialist, operation, idempotencyKey, saved.id, now);
            event(ParticipantGoalEvent.Type.CREATED, saved, null, now, now, null, null, null);
            audit.record(subject, "PARTICIPANT_GOAL_CREATED", "ParticipantGoal", saved.id);
            return view(saved);
        });
    }

    @Transactional
    public ParticipantGoalView createFromPreset(String subject, UUID participantId, ActingContext context, String key, CreateFromPresetCommand command) {
        if (command == null) throw bad("preset command is required");
        GoalMetricPresetCatalog.Derived derived = GoalMetricPresetCatalog.derive(command.presetId(), command.bodyArea(), command.customLabel(), command.exercise(), command.activity(), command.unit(), command.targetComparator());
        if (command.baselineValue() == null || command.targetValue() == null) throw bad("baselineValue and targetValue are required");
        ParticipantGoal.Category category = context == null || context.role() == null ? null : context.role() == ProfessionalRole.TRAINER ? ParticipantGoal.Category.PERFORMANCE : ParticipantGoal.Category.FUNCTIONAL;
        String title = command.titleOverride() == null || command.titleOverride().isBlank() ? derived.title() : command.titleOverride();
        return create(subject, participantId, context, key, new CreateParticipantGoalCommand(category, title, command.description(), 50, command.targetDate(),
                List.of(new OutcomeCommand(derived.metricCode(), command.baselineValue(), command.targetValue(), derived.unit(), derived.measurementMethod(), derived.comparator()))));
    }

    @Transactional(readOnly = true)
    public List<GoalMetricPresetCatalog.PresetView> catalog(String subject, UUID participantId, ActingContext context) {
        authorize(subject, participantId, context, null);
        return GoalMetricPresetCatalog.views();
    }

    @Transactional(readOnly = true)
    public List<ParticipantGoalView> list(String subject, UUID participantId, ActingContext context) {
        UUID specialist = authorize(subject, participantId, context, null);
        return goals.findForSpecialistParticipant(specialist, participantId).stream()
                .filter(goal -> matches(context.role(), goal.category)).map(this::view).toList();
    }

    @Transactional(readOnly = true)
    public ParticipantGoalView detail(String subject, UUID participantId, UUID goalId, ActingContext context) {
        UUID specialist = authorize(subject, participantId, context, null);
        ParticipantGoal goal = owned(specialist, participantId, goalId); requireRole(context, goal); return view(goal);
    }

    @Transactional(readOnly = true)
    public List<ParticipantGoalSummary> participantGoals(String subject) { return findByParticipantId(participantIdFor(subject)); }

    @Transactional(readOnly = true)
    public ParticipantGoalSummary participantGoal(String subject, UUID goalId) {
        UUID participantId = participantIdFor(subject);
        ParticipantGoalSummary goal = findById(goalId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "participant goal not found"));
        if (!participantId.equals(goal.participantId())) throw forbidden("participant goal belongs to another participant");
        return goal;
    }

    @Transactional(readOnly = true)
    public ParticipantGoalDetail ownGoalDetail(String subject, UUID goalId) {
        UUID participantId = participantIdFor(subject);
        ParticipantGoal goal = goals.findById(goalId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "participant goal not found"));
        if (!participantId.equals(goal.participantId)) throw forbidden("participant goal belongs to another participant");
        return ownDetail(goal);
    }

    @Transactional(readOnly = true)
    public ObservationHistory ownObservationHistory(String subject, UUID goalId, UUID outcomeId, int limit, String cursor) {
        UUID participantId = participantIdFor(subject);
        ParticipantGoal goal = goals.findById(goalId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "participant goal not found"));
        if (!participantId.equals(goal.participantId)) throw forbidden("participant goal belongs to another participant");
        if (outcomeId != null) outcomes.findByIdAndGoalId(outcomeId, goalId).orElseThrow(() -> bad("outcome does not belong to goal"));
        if (limit < 1 || limit > 100) throw bad("limit must be from 1 to 100");
        Cursor after = Cursor.parse(cursor);
        var page = org.springframework.data.domain.PageRequest.of(0, limit + 1);
        List<GoalObservation> result = observationPage(goalId, outcomeId, after, page);
        boolean more = result.size() > limit; List<GoalObservation> items = more ? result.subList(0, limit) : result;
        return new ObservationHistory(items.stream().map(this::observationSnapshot).toList(), more ? Cursor.of(items.get(items.size() - 1)) : null);
    }

    @Transactional
    public ParticipantGoalView update(String subject, UUID participantId, UUID goalId, ActingContext context, String key, UpdateParticipantGoalCommand command) {
        UUID specialist = authorize(subject, participantId, context, null); String operation = "UPDATE:" + goalId;
        return replay(specialist, operation, key(key)).orElseGet(() -> {
            ParticipantGoal goal = owned(specialist, participantId, goalId); requireRole(context, goal); version(goal, command == null ? null : command.expectedVersion());
            require(ParticipantGoalLifecyclePolicy.Action.UPDATE, goal);
            Metadata values = metadata(command == null ? null : command.title(), command == null ? null : command.description(), command == null ? null : command.priority(), command == null ? null : command.targetDate());
            Instant now = clock.instant();
            goal.update(values.title, values.description, values.priority, values.targetDate, now);
            ParticipantGoal saved = save(goal); remember(specialist, operation, key(key), goalId, now);
            event(ParticipantGoalEvent.Type.UPDATED, saved, ParticipantGoal.Status.ACTIVE, now, now, null, null, null);
            audit.record(subject, "PARTICIPANT_GOAL_UPDATED", "ParticipantGoal", goalId); return view(saved);
        });
    }

    @Transactional public ParticipantGoalView achieve(String subject, UUID participantId, UUID goalId, ActingContext context, String key, ParticipantGoalVersionCommand command) { return status(subject, participantId, goalId, context, key, command, "ACHIEVE", ParticipantGoalLifecyclePolicy.Action.ACHIEVE); }
    @Transactional public ParticipantGoalView cancel(String subject, UUID participantId, UUID goalId, ActingContext context, String key, ParticipantGoalVersionCommand command) { return status(subject, participantId, goalId, context, key, command, "CANCEL", ParticipantGoalLifecyclePolicy.Action.CANCEL); }

    @Transactional
    public ObservationResult recordObservation(String subject, UUID participantId, UUID goalId, ActingContext context, String key, ObservationCommand command) {
        UUID specialist = authorize(subject, participantId, context, null); String operation = "OBSERVATION:" + goalId;
        String idempotencyKey = key(key);
        Optional<ObservationResult> replay = observationIdempotency.findBySpecialistAccountIdAndOperationAndIdempotencyKey(specialist, operation, idempotencyKey)
                .flatMap(item -> observations.findById(item.observationId)).map(item -> new ObservationResult(observationView(item), view(owned(specialist, participantId, goalId))));
        if (replay.isPresent()) return replay.get();
        ParticipantGoal goal = owned(specialist, participantId, goalId); requireRole(context, goal); require(ParticipantGoalLifecyclePolicy.Action.RECORD_OBSERVATION, goal);
        if (command == null || command.outcomeId() == null || command.value() == null || command.measuredAt() == null) throw bad("outcomeId, value and measuredAt are required");
        Instant now = clock.instant(); if (command.measuredAt().isAfter(now)) throw bad("measuredAt cannot be in the future");
        GoalOutcome outcome = outcomes.findByIdAndGoalId(command.outcomeId(), goalId).orElseThrow(() -> bad("outcome does not belong to goal"));
        GoalObservation saved = observations.saveAndFlush(new GoalObservation(goalId, outcome.id, participantId, command.value(), outcome.unit,
                outcome.measurementMethod, command.measuredAt(), textOptional(command.note(), 2000, "note"),
                textOptional(command.evidenceSource(), 160, "evidenceSource"), specialist, now));
        observationIdempotency.saveAndFlush(new GoalObservationIdempotency(specialist, operation, idempotencyKey, saved.id, now));
        String progress = outcome.targetComparator == null ? TargetComparator.ProgressState.NOT_COMPARABLE.name()
                : outcome.targetComparator.progress(outcome.targetValue, saved.value).name();
        event(ParticipantGoalEvent.Type.OBSERVATION_RECORDED, goal, goal.status, saved.measuredAt, now, saved, outcome, progress);
        audit.record(subject, "PARTICIPANT_GOAL_OBSERVATION_RECORDED", "GoalObservation", saved.id);
        return new ObservationResult(observationView(saved), view(goal));
    }

    @Transactional(readOnly = true)
    public ObservationPage observationHistory(String subject, UUID participantId, UUID goalId, ActingContext context, UUID outcomeId, int limit, String cursor) {
        UUID specialist = authorize(subject, participantId, context, null); ParticipantGoal goal = owned(specialist, participantId, goalId); requireRole(context, goal);
        if (outcomeId != null) outcomes.findByIdAndGoalId(outcomeId, goalId).orElseThrow(() -> bad("outcome does not belong to goal"));
        if (limit < 1 || limit > 100) throw bad("limit must be from 1 to 100");
        Cursor after = Cursor.parse(cursor);
        var page = org.springframework.data.domain.PageRequest.of(0, limit + 1);
        List<GoalObservation> filtered = observationPage(goalId, outcomeId, after, page);
        boolean hasMore = filtered.size() > limit; List<GoalObservation> items = hasMore ? filtered.subList(0, limit) : filtered;
        String next = hasMore ? Cursor.of(items.get(items.size() - 1)) : null;
        return new ObservationPage(items.stream().map(this::observationView).toList(), next);
    }

    private ParticipantGoalView status(String subject, UUID participantId, UUID goalId, ActingContext context, String key, ParticipantGoalVersionCommand command, String actionName, ParticipantGoalLifecyclePolicy.Action action) {
        UUID specialist = authorize(subject, participantId, context, null); String operation = actionName + ":" + goalId;
        return replay(specialist, operation, key(key)).orElseGet(() -> {
            ParticipantGoal goal = owned(specialist, participantId, goalId); requireRole(context, goal); version(goal, command == null ? null : command.expectedVersion()); require(action, goal);
            ParticipantGoal.Status from = goal.status;
            Instant now = clock.instant(); if (action == ParticipantGoalLifecyclePolicy.Action.ACHIEVE) goal.achieve(now); else goal.cancel(now);
            ParticipantGoal saved = save(goal); remember(specialist, operation, key(key), goalId, now);
            event(action == ParticipantGoalLifecyclePolicy.Action.ACHIEVE ? ParticipantGoalEvent.Type.ACHIEVED : ParticipantGoalEvent.Type.CANCELLED,
                    saved, from, now, now, null, null, null);
            audit.record(subject, action == ParticipantGoalLifecyclePolicy.Action.ACHIEVE ? "PARTICIPANT_GOAL_ACHIEVED" : "PARTICIPANT_GOAL_CANCELLED", "ParticipantGoal", goalId); return view(saved);
        });
    }

    @Override @Transactional(readOnly = true)
    public List<ParticipantGoalSummary> findActiveByParticipantId(UUID participantId) {
        if (participantId == null) return List.of();
        return goals.findByParticipantIdAndStatus(participantId, ParticipantGoal.Status.ACTIVE).stream().map(this::summary).toList();
    }
    @Override @Transactional(readOnly = true)
    public List<ParticipantGoalSummary> findByParticipantId(UUID participantId) {
        if (participantId == null) return List.of();
        return goals.findByParticipantIdOrderByCreatedAtDesc(participantId).stream().map(this::summary).toList();
    }
    @Override @Transactional(readOnly = true)
    public Optional<ParticipantGoalSummary> findById(UUID goalId) { return goals.findById(goalId).map(this::summary); }
    @Override @Transactional(readOnly = true)
    public ObservationHistory findObservationHistory(UUID goalId, UUID outcomeId, Instant measuredBefore, Instant recordedBefore, UUID idBefore, int limit) {
        if (goalId == null || limit < 1 || limit > 100) return new ObservationHistory(List.of(), null);
        Cursor after = measuredBefore == null || recordedBefore == null || idBefore == null ? null : new Cursor(measuredBefore, recordedBefore, idBefore);
        List<GoalObservation> result = observationPage(goalId, outcomeId, after, org.springframework.data.domain.PageRequest.of(0, limit + 1));
        boolean more = result.size() > limit; List<GoalObservation> items = more ? result.subList(0, limit) : result;
        return new ObservationHistory(items.stream().map(this::observationSnapshot).toList(), more ? Cursor.of(items.get(items.size() - 1)) : null);
    }

    private List<GoalObservation> observationPage(UUID goalId, UUID outcomeId, Cursor after, org.springframework.data.domain.Pageable page) {
        if (after == null) {
            return outcomeId == null
                    ? observations.findByGoalIdOrderByMeasuredAtDescRecordedAtDescIdDesc(goalId, page)
                    : observations.findByGoalIdAndOutcomeIdOrderByMeasuredAtDescRecordedAtDescIdDesc(goalId, outcomeId, page);
        }
        return outcomeId == null
                ? observations.seekAfterAllOutcomes(goalId, after.measuredAt, after.recordedAt, after.id, page)
                : observations.seekAfterOutcome(goalId, outcomeId, after.measuredAt, after.recordedAt, after.id, page);
    }

    private UUID authorize(String subject, UUID participantId, ActingContext context, ParticipantGoal.Category category) {
        if (participantId == null) throw bad("participantId is required");
        var account = accounts.requireActive(subject);
        if (!account.hasProfile(ProfileType.SPECIALIST)) throw forbidden("specialist profile is required");
        if (context == null || context.role() == null) throw forbidden("explicit specialist acting context is required");
        ParticipantGoal.Category effective = category == null ? null : category;
        if (effective == ParticipantGoal.Category.GENERAL_FITNESS) throw forbidden("GENERAL_FITNESS goals are not supported");
        if (effective != null && !matches(context.role(), effective)) throw forbidden("goal category does not match specialist acting context");
        ProfessionalRole role = context.role();
        authorization.requireCapabilities(account.id(), participantId, context,
                Set.of(role == ProfessionalRole.TRAINER ? Capability.PLAN_PERFORMANCE : Capability.PLAN_FUNCTIONAL_RECOVERY),
                role == ProfessionalRole.TRAINER ? Purpose.PERFORMANCE_PLANNING : Purpose.FUNCTIONAL_RECOVERY);
        return account.id();
    }
    private UUID participantIdFor(String subject) {
        var account = accounts.requireActive(subject);
        if (!account.hasProfile(ProfileType.PARTICIPANT)) throw forbidden("participant profile is required");
        if (participants == null) throw forbidden("an active participant access link is required");
        return participants.findParticipantIdByPrincipalAccountId(account.id())
                .orElseThrow(() -> forbidden("an active participant access link is required"));
    }
    private static boolean matches(ProfessionalRole role, ParticipantGoal.Category category) { return (category == ParticipantGoal.Category.PERFORMANCE && role == ProfessionalRole.TRAINER) || (category == ParticipantGoal.Category.FUNCTIONAL && role == ProfessionalRole.PHYSIOTHERAPIST); }
    private static void requireRole(ActingContext context, ParticipantGoal goal) { if (!matches(context.role(), goal.category)) throw forbidden("goal category does not match specialist acting context"); }
    private ParticipantGoalView view(ParticipantGoal goal) { return new ParticipantGoalView(goal.id, goal.participantId, goal.category, goal.title, goal.description, goal.priority, goal.targetDate, goal.status, outcomeViews(goal.id), lifecycle.availableActions(goal), goal.version, goal.createdAt, goal.updatedAt, goal.achievedAt, goal.cancelledAt); }
    private ParticipantGoalSummary summary(ParticipantGoal goal) { return new ParticipantGoalSummary(goal.id, goal.participantId, goal.category.name(), goal.title, goal.description, goal.priority, goal.targetDate, goal.status.name(), outcomeViews(goal.id).stream().map(item -> new OutcomeSnapshot(item.metricCode, item.baseline, item.targetValue, item.unit, item.position)).toList(), goal.version, goal.createdAt, goal.updatedAt); }
    private ParticipantGoalDetail ownDetail(ParticipantGoal goal) {
        return new ParticipantGoalDetail(goal.id, goal.participantId, goal.category.name(), goal.title, goal.description, goal.priority,
                goal.targetDate, goal.status.name(), outcomeViews(goal.id).stream().map(item -> new OwnOutcomeView(item.id, item.metricCode,
                item.baseline, item.targetValue, item.unit, item.targetComparator, item.position, item.progress)).toList(), goal.version,
                goal.createdAt, goal.updatedAt);
    }
    private List<OutcomeView> outcomeViews(UUID goalId) {
        List<GoalOutcome> goalOutcomes = outcomes.findByGoalIdOrderByPositionAsc(goalId);
        if (observations == null || goalOutcomes.isEmpty()) return goalOutcomes.stream().map(item -> outcomeView(item, List.of(), 0)).toList();
        List<UUID> ids = goalOutcomes.stream().map(item -> item.id).toList();
        Map<UUID, List<GoalObservation>> latestTwo = observations.findLatestTwoByGoalIdAndOutcomeIdIn(goalId, ids).stream()
                .collect(java.util.stream.Collectors.groupingBy(item -> item.outcomeId, LinkedHashMap::new, java.util.stream.Collectors.toList()));
        Map<UUID, Long> counts = observations.countByGoalIdGroupedByOutcomeId(goalId).stream()
                .collect(java.util.stream.Collectors.toMap(GoalObservationRepository.OutcomeObservationCount::getOutcomeId,
                        GoalObservationRepository.OutcomeObservationCount::getObservationCount));
        return goalOutcomes.stream().map(item -> outcomeView(item, latestTwo.getOrDefault(item.id, List.of()), counts.getOrDefault(item.id, 0L).intValue())).toList();
    }
    private OutcomeView outcomeView(GoalOutcome outcome, List<GoalObservation> latestTwo, int count) {
        Optional<GoalObservation> latest = latestTwo.stream().findFirst();
        TargetComparator.ProgressState legacyState = outcome.targetComparator == null ? TargetComparator.ProgressState.NOT_COMPARABLE
                : latest.map(value -> outcome.targetComparator.progress(outcome.targetValue, value.value)).orElse(TargetComparator.ProgressState.NO_DATA);
        OutcomeProgress progress = progress(outcome, latestTwo);
        return new OutcomeView(outcome.id, outcome.metricCode, outcome.baseline, outcome.targetValue, outcome.unit, outcome.targetComparator,
                outcome.position, latest.map(this::observationView).orElse(null), count, legacyState, progress);
    }
    private OutcomeProgress progress(GoalOutcome outcome, List<GoalObservation> observations) {
        if (outcome.targetComparator == null) return new OutcomeProgress(OutcomeProgress.State.NOT_COMPARABLE, null, null);
        List<GoalObservation> compatible = observations.stream().filter(item -> Objects.equals(outcome.unit, item.unit)).toList();
        if (compatible.isEmpty()) return new OutcomeProgress(outcome.baseline == null ? OutcomeProgress.State.NO_MEASUREMENT : OutcomeProgress.State.BASELINE_ONLY, null, null);
        GoalObservation latest = compatible.getFirst();
        ObservationSnapshot latestSnapshot = observationSnapshot(latest);
        if (outcome.targetComparator.progress(outcome.targetValue, latest.value) == TargetComparator.ProgressState.TARGET_REACHED)
            return new OutcomeProgress(OutcomeProgress.State.TARGET_REACHED, percent(outcome, latest.value), latestSnapshot);
        BigDecimal anchor = compatible.size() > 1 ? compatible.get(1).value : outcome.baseline;
        if (anchor == null) return new OutcomeProgress(OutcomeProgress.State.INSUFFICIENT_DATA, percent(outcome, latest.value), latestSnapshot);
        int movement = latest.value.subtract(anchor).signum() * outcome.targetValue.subtract(anchor).signum();
        OutcomeProgress.State state = movement > 0 ? OutcomeProgress.State.PROGRESSING
                : movement < 0 ? OutcomeProgress.State.MOVING_AWAY : OutcomeProgress.State.UNCHANGED;
        return new OutcomeProgress(state, percent(outcome, latest.value), latestSnapshot);
    }
    private static BigDecimal percent(GoalOutcome outcome, BigDecimal latest) {
        if (outcome.baseline == null || outcome.targetValue == null || outcome.targetComparator == null) return null;
        int direction = outcome.targetValue.compareTo(outcome.baseline);
        if ((outcome.targetComparator == TargetComparator.AT_LEAST && direction <= 0)
                || (outcome.targetComparator == TargetComparator.AT_MOST && direction >= 0)) return null;
        return latest.subtract(outcome.baseline).multiply(BigDecimal.valueOf(100))
                .divide(outcome.targetValue.subtract(outcome.baseline), 8, java.math.RoundingMode.HALF_UP).stripTrailingZeros();
    }
    private ParticipantGoal owned(UUID specialist, UUID participant, UUID goalId) { return goals.findByIdAndSpecialistAccountIdAndParticipantId(goalId, specialist, participant).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "participant goal not found")); }
    private Optional<ParticipantGoalView> replay(UUID specialist, String operation, String key) { return idempotency.findBySpecialistAccountIdAndOperationAndIdempotencyKey(specialist, operation, key).flatMap(item -> goals.findById(item.goalId)).map(this::view); }
    private void remember(UUID specialist, String operation, String key, UUID goalId, Instant now) { idempotency.saveAndFlush(new GoalIdempotency(specialist, operation, key, goalId, now)); }
    private ParticipantGoal save(ParticipantGoal goal) { try { return goals.saveAndFlush(goal); } catch (ObjectOptimisticLockingFailureException conflict) { throw conflict("participant goal version is stale"); } }
    private void event(ParticipantGoalEvent.Type type, ParticipantGoal goal, ParticipantGoal.Status fromStatus, Instant effectiveAt,
                       Instant recordedAt, GoalObservation observation, GoalOutcome outcome, String progressState) {
        if (events != null) events.save(new ParticipantGoalEvent(type, goal, fromStatus, effectiveAt, recordedAt, observation, outcome, progressState));
    }
    private void require(ParticipantGoalLifecyclePolicy.Action action, ParticipantGoal goal) { if (!lifecycle.allows(action, goal)) throw conflict("participant goal cannot " + action.name().toLowerCase() + " after it is terminal"); }
    private static void version(ParticipantGoal goal, Long expected) { if (expected == null || expected != goal.version) throw conflict("participant goal version is stale"); }
    private static Values values(CreateParticipantGoalCommand command) { if (command == null || command.category() == null) throw bad("goal category is required"); Metadata metadata = metadata(command.title(), command.description(), command.priority(), command.targetDate()); List<OutcomeCommand> requestedOutcomes = command.outcomes() == null ? List.of() : command.outcomes(); Set<String> codes = new HashSet<>(); for (OutcomeCommand outcome : requestedOutcomes) { if (outcome == null || text(outcome.metricCode(), 80, "outcome metricCode") == null || outcome.targetValue() == null || text(outcome.unit(), 40, "outcome unit") == null || outcome.targetComparator() == null || !codes.add(outcome.metricCode().trim())) throw bad("outcomes must have unique metricCode, targetValue, unit and targetComparator"); } return new Values(command.category(), metadata.title, metadata.description, metadata.priority, metadata.targetDate, List.copyOf(requestedOutcomes)); }
    private static Metadata metadata(String title, String description, Integer priority, LocalDate targetDate) { String requiredTitle = text(title, 160, "title"); if (requiredTitle == null || priority == null || priority < 1 || priority > 100) throw bad("title and priority from 1 to 100 are required"); String trimmedDescription = description == null ? null : text(description, 2000, "description"); return new Metadata(requiredTitle, trimmedDescription, priority, targetDate); }
    private static String text(String value, int max, String field) { if (value == null || value.isBlank() || value.trim().length() > max) throw bad(field + " is required and must be at most " + max + " characters"); return value.trim(); }
    private static String textOptional(String value, int max, String field) { return value == null ? null : text(value, max, field); }
    private static String key(String key) { if (key == null || key.isBlank() || key.trim().length() > 120) throw bad("Idempotency-Key is required"); return key.trim(); }
    private static ResponseStatusException bad(String message) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, message); }
    private static ResponseStatusException forbidden(String message) { return new ResponseStatusException(HttpStatus.FORBIDDEN, message); }
    private static ResponseStatusException conflict(String message) { return new ResponseStatusException(HttpStatus.CONFLICT, message); }
    private record Values(ParticipantGoal.Category category, String title, String description, int priority, LocalDate targetDate, List<OutcomeCommand> outcomes) { }
    private record Metadata(String title, String description, int priority, LocalDate targetDate) { }
    public record CreateParticipantGoalCommand(ParticipantGoal.Category category, String title, String description, Integer priority, LocalDate targetDate, List<OutcomeCommand> outcomes) { }
    public record CreateFromPresetCommand(GoalMetricPresetCatalog.PresetId presetId, String bodyArea, String customLabel, String exercise, String activity,
            BigDecimal baselineValue, BigDecimal targetValue, String unit, TargetComparator targetComparator, LocalDate targetDate, String description, String titleOverride) { }
    public record UpdateParticipantGoalCommand(Long expectedVersion, String title, String description, Integer priority, LocalDate targetDate) { }
    public record ParticipantGoalVersionCommand(Long expectedVersion) { }
    public record OutcomeCommand(String metricCode, BigDecimal baseline, BigDecimal targetValue, String unit, String measurementMethod, TargetComparator targetComparator) { public OutcomeCommand(String metricCode, BigDecimal targetValue, String unit, String measurementMethod, TargetComparator targetComparator) { this(metricCode, null, targetValue, unit, measurementMethod, targetComparator); } public OutcomeCommand(String metricCode, BigDecimal targetValue, String unit) { this(metricCode, null, targetValue, unit, null, TargetComparator.AT_LEAST); } }
    public record ObservationCommand(UUID outcomeId, BigDecimal value, Instant measuredAt, String measurementMethod, String note, String evidenceSource) { }
    public record ObservationView(UUID id, UUID goalId, UUID outcomeId, UUID participantId, BigDecimal value, String unit, String measurementMethod, Instant measuredAt, String note, String evidenceSource, Instant recordedAt) { }
    public record ObservationResult(ObservationView observation, ParticipantGoalView goal) { }
    public record ObservationPage(List<ObservationView> items, String nextCursor) { }
    public record OutcomeProgress(State state, BigDecimal baselineToTargetPercent, ObservationSnapshot latestObservation) {
        public enum State { NO_MEASUREMENT, BASELINE_ONLY, INSUFFICIENT_DATA, PROGRESSING, MOVING_AWAY, UNCHANGED, TARGET_REACHED, NOT_COMPARABLE }
    }
    public record OutcomeView(UUID id, String metricCode, BigDecimal baseline, BigDecimal targetValue, String unit, TargetComparator targetComparator, int position, ObservationView latestObservation, int observationCount, TargetComparator.ProgressState progressState, OutcomeProgress progress) { }
    public record ParticipantGoalView(UUID id, UUID participantId, ParticipantGoal.Category category, String title, String description, int priority, LocalDate targetDate, ParticipantGoal.Status status, List<OutcomeView> outcomes, List<String> availableActions, long version, Instant createdAt, Instant updatedAt, Instant achievedAt, Instant cancelledAt) { }
    public record OwnOutcomeView(UUID id, String metricCode, BigDecimal baseline, BigDecimal targetValue, String unit, TargetComparator targetComparator, int position, OutcomeProgress progress) { }
    public record ParticipantGoalDetail(UUID id, UUID participantId, String category, String title, String description, int priority, LocalDate targetDate, String status, List<OwnOutcomeView> outcomes, long version, Instant createdAt, Instant updatedAt) { }
    private ObservationView observationView(GoalObservation item) { return new ObservationView(item.id, item.goalId, item.outcomeId, item.participantId, item.value, item.unit, item.measurementMethod, item.measuredAt, item.note, item.evidenceSource, item.recordedAt); }
    private ObservationSnapshot observationSnapshot(GoalObservation item) { return new ObservationSnapshot(item.id, item.goalId, item.outcomeId, item.participantId, item.value, item.unit, item.measurementMethod, item.measuredAt, item.recordedAt); }
    private record Cursor(Instant measuredAt, Instant recordedAt, UUID id) {
        boolean before(GoalObservation item) { int measured = item.measuredAt.compareTo(measuredAt); if (measured != 0) return measured < 0; int recorded = item.recordedAt.compareTo(recordedAt); if (recorded != 0) return recorded < 0; return item.id.compareTo(id) < 0; }
        static String of(GoalObservation item) { return java.util.Base64.getUrlEncoder().withoutPadding().encodeToString((item.measuredAt + "|" + item.recordedAt + "|" + item.id).getBytes(java.nio.charset.StandardCharsets.UTF_8)); }
        static Cursor parse(String value) { if (value == null || value.isBlank()) return null; try { String[] parts = new String(java.util.Base64.getUrlDecoder().decode(value), java.nio.charset.StandardCharsets.UTF_8).split("\\|", -1); if (parts.length != 3) throw new IllegalArgumentException(); return new Cursor(Instant.parse(parts[0]), Instant.parse(parts[1]), UUID.fromString(parts[2])); } catch (RuntimeException ex) { throw bad("cursor is invalid"); } }
    }
}
