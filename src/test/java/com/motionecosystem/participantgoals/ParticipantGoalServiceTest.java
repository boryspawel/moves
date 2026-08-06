package com.motionecosystem.participantgoals;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.motionecosystem.audit.AuditRecorder;
import com.motionecosystem.identityaccess.api.CurrentAccount;
import com.motionecosystem.identityaccess.api.CurrentAccountService;
import com.motionecosystem.identityaccess.api.ProfileType;
import com.motionecosystem.specialist.SpecialistRelationshipService;
import com.motionecosystem.specialist.api.SpecialistAuthorizationPort;
import com.motionecosystem.specialist.api.SpecialistAuthorizationPort.ActingContext;
import com.motionecosystem.specialist.api.SpecialistAuthorizationPort.ProfessionalRole;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.ArrayList;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class ParticipantGoalServiceTest {
    private static final Instant NOW = Instant.parse("2030-06-10T12:00:00Z");

    @Test
    void createsPerformanceGoalWithOrderedUniqueOutcomes() {
        Fixture fixture = fixture();
        List<GoalOutcome> persistedOutcomes = new ArrayList<>();
        when(fixture.goals.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(fixture.outcomes.save(any())).thenAnswer(invocation -> {
            GoalOutcome outcome = invocation.getArgument(0);
            persistedOutcomes.add(outcome);
            return outcome;
        });
        when(fixture.outcomes.findByGoalIdOrderByPositionAsc(any())).thenAnswer(invocation -> persistedOutcomes.stream()
                .filter(outcome -> outcome.goalId.equals(invocation.getArgument(0)))
                .sorted(java.util.Comparator.comparingInt(outcome -> outcome.position)).toList());

        var view = fixture.service.create("specialist", fixture.participantId, trainer(), "create-key", createPerformance());

        assertThat(view.category()).isEqualTo(ParticipantGoal.Category.PERFORMANCE);
        assertThat(view.status()).isEqualTo(ParticipantGoal.Status.ACTIVE);
        assertThat(view.outcomes()).extracting(ParticipantGoalService.OutcomeView::metricCode)
                .containsExactly("distance", "duration");
        assertThat(view.availableActions()).containsExactly("UPDATE", "RECORD_OBSERVATION", "ACHIEVE", "CANCEL");
        ArgumentCaptor<GoalOutcome> outcomes = ArgumentCaptor.forClass(GoalOutcome.class);
        verify(fixture.outcomes, org.mockito.Mockito.times(2)).save(outcomes.capture());
        assertThat(outcomes.getAllValues()).extracting(outcome -> outcome.position).containsExactly(0, 1);
        verify(fixture.relationships).requireActive(fixture.specialistId, fixture.participantId);
    }

    @Test
    void rejectsDuplicateOutcomeMetricsAfterTrimming() {
        Fixture fixture = fixture();
        var command = new ParticipantGoalService.CreateParticipantGoalCommand(ParticipantGoal.Category.PERFORMANCE,
                "Run", null, 50, null, List.of(outcome(" distance ", "km"), outcome("distance", "m")));

        assertStatus(HttpStatus.BAD_REQUEST, () -> fixture.service.create("specialist", fixture.participantId, trainer(), "create-key", command));
        verify(fixture.goals, never()).saveAndFlush(any());
    }

    @Test
    void allowsCreatingAGoalWithoutInitialOutcomes() {
        Fixture fixture = fixture();
        when(fixture.goals.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var view = fixture.service.create("specialist", fixture.participantId, trainer(), "no-outcomes",
                new ParticipantGoalService.CreateParticipantGoalCommand(ParticipantGoal.Category.PERFORMANCE, "Finish 5k", null, 60, null, List.of()));

        assertThat(view.outcomes()).isEmpty();
        verify(fixture.outcomes, never()).save(any());
    }

    @Test
    void persistsAndReturnsBaselineForPresetCreatedGoalWhileExistingOutcomesRemainNull() {
        Fixture fixture = fixture();
        List<GoalOutcome> persisted = new ArrayList<>();
        when(fixture.goals.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(fixture.outcomes.save(any())).thenAnswer(invocation -> {
            GoalOutcome outcome = invocation.getArgument(0);
            persisted.add(outcome);
            return outcome;
        });
        when(fixture.outcomes.findByGoalIdOrderByPositionAsc(any())).thenAnswer(invocation -> persisted.stream()
                .filter(outcome -> outcome.goalId.equals(invocation.getArgument(0))).toList());

        var preset = fixture.service.createFromPreset("specialist", fixture.participantId, trainer(), "preset-key",
                new ParticipantGoalService.CreateFromPresetCommand(GoalMetricPresetCatalog.PresetId.BODY_WEIGHT,
                        null, null, null, null, new BigDecimal("82.5000"), new BigDecimal("75.0000"), "kg",
                        null, null, null, null));
        GoalOutcome existing = new GoalOutcome(UUID.randomUUID(), "legacy", new BigDecimal("5.0000"), "kg", null,
                TargetComparator.AT_LEAST, 0, NOW);

        assertThat(preset.outcomes()).singleElement().extracting(ParticipantGoalService.OutcomeView::baseline)
                .isEqualTo(new BigDecimal("82.5000"));
        assertThat(existing.baseline).isNull();
    }

    @Test
    void rejectsCategoriesOutsideTheExplicitActingContext() {
        Fixture fixture = fixture();

        assertStatus(HttpStatus.FORBIDDEN, () -> fixture.service.create("specialist", fixture.participantId, trainer(), "trainer-functional",
                create(ParticipantGoal.Category.FUNCTIONAL)));
        assertStatus(HttpStatus.FORBIDDEN, () -> fixture.service.create("specialist", fixture.participantId, physiotherapist(), "physio-performance",
                create(ParticipantGoal.Category.PERFORMANCE)));
        assertStatus(HttpStatus.FORBIDDEN, () -> fixture.service.create("specialist", fixture.participantId, trainer(), "general",
                create(ParticipantGoal.Category.GENERAL_FITNESS)));
    }

    @Test
    void detailRequiresActiveRelationshipAndScopedGoalOwnership() {
        Fixture fixture = fixture();
        UUID foreignGoal = UUID.randomUUID();
        when(fixture.goals.findByIdAndSpecialistAccountIdAndParticipantId(foreignGoal, fixture.specialistId, fixture.participantId))
                .thenReturn(Optional.empty());

        assertStatus(HttpStatus.NOT_FOUND, () -> fixture.service.detail("specialist", fixture.participantId, foreignGoal, trainer()));
        verify(fixture.relationships).requireActive(fixture.specialistId, fixture.participantId);
        verify(fixture.goals).findByIdAndSpecialistAccountIdAndParticipantId(foreignGoal, fixture.specialistId, fixture.participantId);
    }

    @Test
    void updatesActiveGoalMetadata() {
        Fixture fixture = fixture();
        ParticipantGoal goal = goal(fixture, ParticipantGoal.Category.PERFORMANCE);
        when(fixture.goals.findByIdAndSpecialistAccountIdAndParticipantId(goal.id, fixture.specialistId, fixture.participantId)).thenReturn(Optional.of(goal));
        when(fixture.goals.saveAndFlush(goal)).thenReturn(goal);

        var result = fixture.service.update("specialist", fixture.participantId, goal.id, trainer(), "update-key",
                new ParticipantGoalService.UpdateParticipantGoalCommand(0L, "Faster 5k", "New plan", 80, LocalDate.parse("2030-07-01")));

        assertThat(result).extracting(ParticipantGoalService.ParticipantGoalView::title, ParticipantGoalService.ParticipantGoalView::description,
                ParticipantGoalService.ParticipantGoalView::priority, ParticipantGoalService.ParticipantGoalView::targetDate)
                .containsExactly("Faster 5k", "New plan", 80, LocalDate.parse("2030-07-01"));
        assertThat(result.status()).isEqualTo(ParticipantGoal.Status.ACTIVE);
        verify(fixture.audit).record("specialist", "PARTICIPANT_GOAL_UPDATED", "ParticipantGoal", goal.id);
    }

    @Test
    void transitionsActiveGoalsAndRejectsFurtherTerminalChanges() {
        Fixture fixture = fixture();
        ParticipantGoal achieved = goal(fixture, ParticipantGoal.Category.PERFORMANCE);
        when(fixture.goals.findByIdAndSpecialistAccountIdAndParticipantId(achieved.id, fixture.specialistId, fixture.participantId)).thenReturn(Optional.of(achieved));
        when(fixture.goals.saveAndFlush(achieved)).thenReturn(achieved);

        assertThat(fixture.service.achieve("specialist", fixture.participantId, achieved.id, trainer(), "achieve-key",
                new ParticipantGoalService.ParticipantGoalVersionCommand(0L)).status()).isEqualTo(ParticipantGoal.Status.ACHIEVED);
        assertThat(achieved.achievedAt).isEqualTo(NOW);
        assertStatus(HttpStatus.CONFLICT, () -> fixture.service.cancel("specialist", fixture.participantId, achieved.id, trainer(), "cancel-key",
                new ParticipantGoalService.ParticipantGoalVersionCommand(0L)));

        ParticipantGoal cancelled = goal(fixture, ParticipantGoal.Category.PERFORMANCE);
        when(fixture.goals.findByIdAndSpecialistAccountIdAndParticipantId(cancelled.id, fixture.specialistId, fixture.participantId)).thenReturn(Optional.of(cancelled));
        when(fixture.goals.saveAndFlush(cancelled)).thenReturn(cancelled);
        assertThat(fixture.service.cancel("specialist", fixture.participantId, cancelled.id, trainer(), "cancel-active-key",
                new ParticipantGoalService.ParticipantGoalVersionCommand(0L)).status()).isEqualTo(ParticipantGoal.Status.CANCELLED);
        assertThat(cancelled.cancelledAt).isEqualTo(NOW);
    }

    @Test
    void rejectsStaleOptimisticVersionBeforeSaving() {
        Fixture fixture = fixture();
        ParticipantGoal goal = goal(fixture, ParticipantGoal.Category.PERFORMANCE);
        goal.version = 2;
        when(fixture.goals.findByIdAndSpecialistAccountIdAndParticipantId(goal.id, fixture.specialistId, fixture.participantId)).thenReturn(Optional.of(goal));

        assertStatus(HttpStatus.CONFLICT, () -> fixture.service.update("specialist", fixture.participantId, goal.id, trainer(), "stale-key",
                new ParticipantGoalService.UpdateParticipantGoalCommand(1L, "New", null, 50, null)));
        verify(fixture.goals, never()).saveAndFlush(any());
    }

    @Test
    void replaysIdempotentCreateWithoutDuplicatingGoalOrChangingVersion() {
        Fixture fixture = fixture();
        ParticipantGoal existing = goal(fixture, ParticipantGoal.Category.PERFORMANCE);
        GoalIdempotency replay = new GoalIdempotency(fixture.specialistId, "CREATE:" + fixture.participantId, "same-key", existing.id, NOW);
        when(fixture.idempotency.findBySpecialistAccountIdAndOperationAndIdempotencyKey(fixture.specialistId, "CREATE:" + fixture.participantId, "same-key"))
                .thenReturn(Optional.of(replay));
        when(fixture.goals.findById(existing.id)).thenReturn(Optional.of(existing));

        var result = fixture.service.create("specialist", fixture.participantId, trainer(), "same-key", createPerformance());

        assertThat(result.id()).isEqualTo(existing.id);
        assertThat(result.version()).isZero();
        verify(fixture.goals, never()).saveAndFlush(any());
        verify(fixture.outcomes, never()).save(any());
        verify(fixture.idempotency, never()).saveAndFlush(any());
    }

    @Test
    void reportsNoAvailableActionsForTerminalGoal() {
        Fixture fixture = fixture();
        ParticipantGoal goal = goal(fixture, ParticipantGoal.Category.PERFORMANCE);
        goal.cancel(NOW);
        when(fixture.goals.findByIdAndSpecialistAccountIdAndParticipantId(goal.id, fixture.specialistId, fixture.participantId)).thenReturn(Optional.of(goal));

        assertThat(fixture.service.detail("specialist", fixture.participantId, goal.id, trainer()).availableActions()).isEmpty();
    }

    @Test
    void recordsObservationUsingTheOutcomeUnitAndMeasurementMethodSnapshot() {
        Fixture fixture = observationFixture();
        ParticipantGoal goal = goal(fixture, ParticipantGoal.Category.PERFORMANCE);
        GoalOutcome outcome = outcomeEntity(goal.id, "distance", "km", "gps", TargetComparator.AT_LEAST);
        when(fixture.goals.findByIdAndSpecialistAccountIdAndParticipantId(goal.id, fixture.specialistId, fixture.participantId)).thenReturn(Optional.of(goal));
        when(fixture.outcomes.findByIdAndGoalId(outcome.id, goal.id)).thenReturn(Optional.of(outcome));
        when(fixture.outcomes.findByGoalIdOrderByPositionAsc(goal.id)).thenReturn(List.of(outcome));
        when(fixture.observations.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var result = fixture.service.recordObservation("specialist", fixture.participantId, goal.id, trainer(), "observation-key",
                new ParticipantGoalService.ObservationCommand(outcome.id, new BigDecimal("4.2000"), NOW.minusSeconds(60), "ignored", "felt good", "watch"));

        assertThat(result.observation()).extracting(ParticipantGoalService.ObservationView::unit,
                ParticipantGoalService.ObservationView::measurementMethod, ParticipantGoalService.ObservationView::measuredAt)
                .containsExactly("km", "gps", NOW.minusSeconds(60));
        ArgumentCaptor<GoalObservation> observations = ArgumentCaptor.forClass(GoalObservation.class);
        verify(fixture.observations).saveAndFlush(observations.capture());
        assertThat(observations.getValue()).extracting(item -> item.unit, item -> item.measurementMethod)
                .containsExactly("km", "gps");
        verify(fixture.observationIdempotency).saveAndFlush(any());
    }

    @Test
    void rejectsObservationForForeignGoalOutcomeFutureMeasurementAndTerminalGoal() {
        Fixture fixture = observationFixture();
        ParticipantGoal goal = goal(fixture, ParticipantGoal.Category.PERFORMANCE);
        GoalOutcome foreignOutcome = outcomeEntity(UUID.randomUUID(), "distance", "km", null, TargetComparator.AT_LEAST);
        when(fixture.goals.findByIdAndSpecialistAccountIdAndParticipantId(goal.id, fixture.specialistId, fixture.participantId)).thenReturn(Optional.of(goal));
        when(fixture.outcomes.findByIdAndGoalId(foreignOutcome.id, goal.id)).thenReturn(Optional.empty());

        assertStatus(HttpStatus.BAD_REQUEST, () -> fixture.service.recordObservation("specialist", fixture.participantId, goal.id, trainer(), "foreign-outcome",
                new ParticipantGoalService.ObservationCommand(foreignOutcome.id, BigDecimal.ONE, NOW, null, null, null)));

        GoalOutcome outcome = outcomeEntity(goal.id, "distance", "km", null, TargetComparator.AT_LEAST);
        when(fixture.outcomes.findByIdAndGoalId(outcome.id, goal.id)).thenReturn(Optional.of(outcome));
        assertStatus(HttpStatus.BAD_REQUEST, () -> fixture.service.recordObservation("specialist", fixture.participantId, goal.id, trainer(), "future",
                new ParticipantGoalService.ObservationCommand(outcome.id, BigDecimal.ONE, NOW.plusSeconds(1), null, null, null)));

        goal.cancel(NOW);
        assertStatus(HttpStatus.CONFLICT, () -> fixture.service.recordObservation("specialist", fixture.participantId, goal.id, trainer(), "terminal",
                new ParticipantGoalService.ObservationCommand(outcome.id, BigDecimal.ONE, NOW, null, null, null)));
        verify(fixture.observations, never()).saveAndFlush(any());
    }

    @Test
    void replaysObservationWithoutChangingGoalStatusOrVersion() {
        Fixture fixture = observationFixture();
        ParticipantGoal goal = goal(fixture, ParticipantGoal.Category.PERFORMANCE);
        GoalOutcome outcome = outcomeEntity(goal.id, "distance", "km", null, TargetComparator.AT_LEAST);
        GoalObservation saved = new GoalObservation(goal.id, outcome.id, fixture.participantId, BigDecimal.ONE, "km", null, NOW, null, null, fixture.specialistId, NOW);
        GoalObservationIdempotency replay = new GoalObservationIdempotency(fixture.specialistId, "OBSERVATION:" + goal.id, "same-key", saved.id, NOW);
        when(fixture.observationIdempotency.findBySpecialistAccountIdAndOperationAndIdempotencyKey(fixture.specialistId, "OBSERVATION:" + goal.id, "same-key")).thenReturn(Optional.of(replay));
        when(fixture.observations.findById(saved.id)).thenReturn(Optional.of(saved));
        when(fixture.goals.findByIdAndSpecialistAccountIdAndParticipantId(goal.id, fixture.specialistId, fixture.participantId)).thenReturn(Optional.of(goal));
        when(fixture.outcomes.findByGoalIdOrderByPositionAsc(goal.id)).thenReturn(List.of(outcome));

        var result = fixture.service.recordObservation("specialist", fixture.participantId, goal.id, trainer(), "same-key",
                new ParticipantGoalService.ObservationCommand(outcome.id, BigDecimal.TEN, NOW, null, null, null));

        assertThat(result.goal()).extracting(ParticipantGoalService.ParticipantGoalView::status, ParticipantGoalService.ParticipantGoalView::version)
                .containsExactly(ParticipantGoal.Status.ACTIVE, 0L);
        verify(fixture.observations, never()).saveAndFlush(any());
        verify(fixture.observationIdempotency, never()).saveAndFlush(any());
    }

    @Test
    void reportsComparatorProgressStatesAndProvidesStableOutcomeFilteredHistoryCursor() {
        Fixture fixture = observationFixture();
        ParticipantGoal goal = goal(fixture, ParticipantGoal.Category.PERFORMANCE);
        GoalOutcome atLeast = outcomeEntity(goal.id, "distance", "km", null, TargetComparator.AT_LEAST);
        GoalOutcome atMost = outcomeEntity(goal.id, "pain", "points", null, TargetComparator.AT_MOST);
        GoalOutcome legacy = outcomeEntity(goal.id, "legacy", "count", null, null);
        GoalOutcome noData = outcomeEntity(goal.id, "sleep", "hours", null, TargetComparator.AT_LEAST);
        GoalObservation reached = observation(goal.id, atLeast.id, NOW.minusSeconds(1), NOW.minusSeconds(1), new BigDecimal("5"));
        GoalObservation progressing = observation(goal.id, atMost.id, NOW.minusSeconds(2), NOW.minusSeconds(2), new BigDecimal("6"));
        when(fixture.outcomes.findByGoalIdOrderByPositionAsc(goal.id)).thenReturn(List.of(atLeast, atMost, legacy, noData));
        when(fixture.observations.findTopByGoalIdAndOutcomeIdOrderByMeasuredAtDescRecordedAtDescIdDesc(goal.id, atLeast.id)).thenReturn(Optional.of(reached));
        when(fixture.observations.findTopByGoalIdAndOutcomeIdOrderByMeasuredAtDescRecordedAtDescIdDesc(goal.id, atMost.id)).thenReturn(Optional.of(progressing));
        when(fixture.observations.findByGoalIdAndOutcomeIdOrderByMeasuredAtDescRecordedAtDescIdDesc(eq(goal.id), eq(atLeast.id), any())).thenReturn(List.of(reached, progressing));
        when(fixture.observations.seekAfterOutcome(eq(goal.id), eq(atLeast.id), eq(reached.measuredAt), eq(reached.recordedAt), eq(reached.id), any())).thenReturn(List.of(progressing));
        when(fixture.goals.findByIdAndSpecialistAccountIdAndParticipantId(goal.id, fixture.specialistId, fixture.participantId)).thenReturn(Optional.of(goal));
        when(fixture.outcomes.findByIdAndGoalId(atLeast.id, goal.id)).thenReturn(Optional.of(atLeast));

        assertThat(fixture.service.detail("specialist", fixture.participantId, goal.id, trainer()).outcomes())
                .extracting(ParticipantGoalService.OutcomeView::progressState)
                .containsExactly(TargetComparator.ProgressState.TARGET_REACHED, TargetComparator.ProgressState.IN_PROGRESS,
                        TargetComparator.ProgressState.NOT_COMPARABLE, TargetComparator.ProgressState.NO_DATA);
        var first = fixture.service.observationHistory("specialist", fixture.participantId, goal.id, trainer(), atLeast.id, 1, null);
        assertThat(first.items()).extracting(ParticipantGoalService.ObservationView::id).containsExactly(reached.id);
        assertThat(first.nextCursor()).isNotBlank();
        var second = fixture.service.observationHistory("specialist", fixture.participantId, goal.id, trainer(), atLeast.id, 1, first.nextCursor());
        assertThat(second.items()).hasSize(1);
        verify(fixture.observations).findByGoalIdAndOutcomeIdOrderByMeasuredAtDescRecordedAtDescIdDesc(eq(goal.id), eq(atLeast.id), any());
        verify(fixture.observations).seekAfterOutcome(eq(goal.id), eq(atLeast.id), eq(reached.measuredAt), eq(reached.recordedAt), eq(reached.id), any());
        verify(fixture.observations, never()).seekAfterAllOutcomes(any(), any(), any(), any(), any());
    }

    @Test
    void usesFirstPageQueryWithoutSeekParametersWhenCursorIsAbsent() {
        Fixture fixture = observationFixture();
        ParticipantGoal goal = goal(fixture, ParticipantGoal.Category.PERFORMANCE);
        when(fixture.goals.findByIdAndSpecialistAccountIdAndParticipantId(goal.id, fixture.specialistId, fixture.participantId)).thenReturn(Optional.of(goal));
        when(fixture.observations.findByGoalIdOrderByMeasuredAtDescRecordedAtDescIdDesc(eq(goal.id), any())).thenReturn(List.of());

        var page = fixture.service.observationHistory("specialist", fixture.participantId, goal.id, trainer(), null, 10, null);

        assertThat(page.items()).isEmpty();
        verify(fixture.observations).findByGoalIdOrderByMeasuredAtDescRecordedAtDescIdDesc(eq(goal.id), any());
        verify(fixture.observations, never()).seekAfterAllOutcomes(any(), any(), any(), any(), any());
        verify(fixture.observations, never()).seekAfterOutcome(any(), any(), any(), any(), any(), any());
    }

    private static void assertStatus(HttpStatus status, Runnable call) {
        assertThatThrownBy(call::run).isInstanceOf(ResponseStatusException.class)
                .extracting(error -> ((ResponseStatusException) error).getStatusCode()).isEqualTo(status);
    }

    private static ParticipantGoalService.CreateParticipantGoalCommand createPerformance() { return create(ParticipantGoal.Category.PERFORMANCE); }
    private static ParticipantGoalService.CreateParticipantGoalCommand create(ParticipantGoal.Category category) {
        return new ParticipantGoalService.CreateParticipantGoalCommand(category, "Finish 5k", "Build endurance", 60,
                LocalDate.parse("2030-06-30"), List.of(outcome("distance", "km"), outcome("duration", "minutes")));
    }
    private static ParticipantGoalService.OutcomeCommand outcome(String metric, String unit) { return new ParticipantGoalService.OutcomeCommand(metric, new BigDecimal("5.0000"), unit); }
    private static ActingContext trainer() { return new ActingContext(ProfessionalRole.TRAINER); }
    private static ActingContext physiotherapist() { return new ActingContext(ProfessionalRole.PHYSIOTHERAPIST); }
    private static ParticipantGoal goal(Fixture fixture, ParticipantGoal.Category category) {
        return new ParticipantGoal(fixture.participantId, fixture.specialistId, category, "Finish 5k", "Build endurance", 60,
                LocalDate.parse("2030-06-30"), NOW);
    }
    private static GoalOutcome outcomeEntity(UUID goalId, String metric, String unit, String method, TargetComparator comparator) {
        return new GoalOutcome(goalId, metric, new BigDecimal("5.0000"), unit, method, comparator, 0, NOW);
    }
    private static GoalObservation observation(UUID goalId, UUID outcomeId, Instant measuredAt, Instant recordedAt, BigDecimal value) {
        return new GoalObservation(goalId, outcomeId, UUID.randomUUID(), value, "km", null, measuredAt, null, null, UUID.randomUUID(), recordedAt);
    }
    private static Fixture fixture() {
        UUID specialistId = UUID.randomUUID();
        CurrentAccountService accounts = mock(CurrentAccountService.class);
        when(accounts.requireActive("specialist")).thenReturn(new CurrentAccount(specialistId, "specialist", ProfileType.SPECIALIST));
        ParticipantGoalRepository goals = mock(ParticipantGoalRepository.class);
        GoalOutcomeRepository outcomes = mock(GoalOutcomeRepository.class);
        GoalIdempotencyRepository idempotency = mock(GoalIdempotencyRepository.class);
        SpecialistRelationshipService relationships = mock(SpecialistRelationshipService.class);
        AuditRecorder audit = mock(AuditRecorder.class);
        return new Fixture(specialistId, UUID.randomUUID(), goals, outcomes, idempotency, relationships, audit,
                new ParticipantGoalService(goals, outcomes, idempotency, accounts, relationships,
                        mock(SpecialistAuthorizationPort.class), audit, Clock.fixed(NOW, ZoneOffset.UTC)));
    }
    private static Fixture observationFixture() {
        UUID specialistId = UUID.randomUUID();
        UUID participantId = UUID.randomUUID();
        CurrentAccountService accounts = mock(CurrentAccountService.class);
        when(accounts.requireActive("specialist")).thenReturn(new CurrentAccount(specialistId, "specialist", ProfileType.SPECIALIST));
        ParticipantGoalRepository goals = mock(ParticipantGoalRepository.class);
        GoalOutcomeRepository outcomes = mock(GoalOutcomeRepository.class);
        GoalIdempotencyRepository idempotency = mock(GoalIdempotencyRepository.class);
        GoalObservationRepository observations = mock(GoalObservationRepository.class);
        GoalObservationIdempotencyRepository observationIdempotency = mock(GoalObservationIdempotencyRepository.class);
        SpecialistRelationshipService relationships = mock(SpecialistRelationshipService.class);
        AuditRecorder audit = mock(AuditRecorder.class);
        return new Fixture(specialistId, participantId, goals, outcomes, idempotency, observations, observationIdempotency, relationships, audit,
                new ParticipantGoalService(goals, outcomes, idempotency, observations, observationIdempotency, accounts, relationships,
                        mock(SpecialistAuthorizationPort.class), audit, Clock.fixed(NOW, ZoneOffset.UTC)));
    }
    private record Fixture(UUID specialistId, UUID participantId, ParticipantGoalRepository goals, GoalOutcomeRepository outcomes,
                           GoalIdempotencyRepository idempotency, GoalObservationRepository observations,
                           GoalObservationIdempotencyRepository observationIdempotency, SpecialistRelationshipService relationships,
                           AuditRecorder audit, ParticipantGoalService service) {
        Fixture(UUID specialistId, UUID participantId, ParticipantGoalRepository goals, GoalOutcomeRepository outcomes,
                GoalIdempotencyRepository idempotency, SpecialistRelationshipService relationships, AuditRecorder audit,
                ParticipantGoalService service) {
            this(specialistId, participantId, goals, outcomes, idempotency, null, null, relationships, audit, service);
        }
    }
}
