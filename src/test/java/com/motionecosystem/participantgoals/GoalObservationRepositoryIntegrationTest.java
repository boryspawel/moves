package com.motionecosystem.participantgoals;

import static org.assertj.core.api.Assertions.assertThat;

import com.motionecosystem.application.MotionEcosystemApplication;
import com.motionecosystem.identityaccess.domain.PrincipalAccount;
import com.motionecosystem.participant.ParticipantRecord;
import com.motionecosystem.support.PostgresTestConfiguration;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(classes = MotionEcosystemApplication.class)
@Import(PostgresTestConfiguration.class)
class GoalObservationRepositoryIntegrationTest {
    private static final Instant NOW = Instant.parse("2030-06-10T12:00:00Z");

    @Autowired EntityManager entityManager;
    @Autowired GoalObservationRepository observations;

    @Test
    @Transactional
    void returnsTheLatestTwoPerOutcomeWithDeterministicTies() {
        PrincipalAccount specialist = PrincipalAccount.create("goal-observation-query-" + UUID.randomUUID(), NOW);
        entityManager.persist(specialist);
        ParticipantRecord participant = new ParticipantRecord("Goal observation participant", ParticipantRecord.RelationshipContext.CLIENT,
                null, null, ZoneOffset.UTC, specialist.id(), NOW);
        entityManager.persist(participant);
        ParticipantGoal goal = new ParticipantGoal(participant.id(), specialist.id(), ParticipantGoal.Category.PERFORMANCE, "Goal", null, 50, null, NOW);
        entityManager.persist(goal);
        GoalOutcome first = new GoalOutcome(goal.id, "distance", BigDecimal.ZERO, BigDecimal.TEN, "km", null, TargetComparator.AT_LEAST, 0, NOW);
        GoalOutcome second = new GoalOutcome(goal.id, "pain", BigDecimal.TEN, BigDecimal.ZERO, "points", null, TargetComparator.AT_MOST, 1, NOW);
        entityManager.persist(first); entityManager.persist(second);
        GoalObservation firstOld = observation(goal, first, participant.id(), specialist.id(), new BigDecimal("1"), NOW.minusSeconds(2));
        GoalObservation firstTieLow = observation(goal, first, participant.id(), specialist.id(), new BigDecimal("2"), NOW.minusSeconds(1));
        GoalObservation firstTieHigh = observation(goal, first, participant.id(), specialist.id(), new BigDecimal("3"), NOW.minusSeconds(1));
        firstTieLow.id = UUID.fromString("00000000-0000-0000-0000-000000000001");
        firstTieHigh.id = UUID.fromString("00000000-0000-0000-0000-000000000002");
        GoalObservation secondOld = observation(goal, second, participant.id(), specialist.id(), new BigDecimal("8"), NOW.minusSeconds(3));
        GoalObservation secondCurrent = observation(goal, second, participant.id(), specialist.id(), new BigDecimal("7"), NOW);
        entityManager.persist(firstOld); entityManager.persist(firstTieLow); entityManager.persist(firstTieHigh);
        entityManager.persist(secondOld); entityManager.persist(secondCurrent);
        entityManager.flush(); entityManager.clear();

        List<GoalObservation> result = observations.findLatestTwoByGoalIdAndOutcomeIdIn(goal.id, List.of(first.id, second.id));

        List<GoalObservation> firstResult = result.stream().filter(item -> item.outcomeId.equals(first.id)).toList();
        List<GoalObservation> secondResult = result.stream().filter(item -> item.outcomeId.equals(second.id)).toList();

        assertThat(firstResult).extracting(item -> item.id).containsExactly(firstTieHigh.id, firstTieLow.id);
        assertThat(firstResult.get(0).value).isEqualByComparingTo("3");
        assertThat(firstResult.get(1).value).isEqualByComparingTo("2");
        assertThat(secondResult).extracting(item -> item.id).containsExactly(secondCurrent.id, secondOld.id);
        assertThat(secondResult.get(0).value).isEqualByComparingTo("7");
        assertThat(secondResult.get(1).value).isEqualByComparingTo("8");
    }

    private GoalObservation observation(ParticipantGoal goal, GoalOutcome outcome, UUID participantId, UUID recorder, BigDecimal value, Instant measuredAt) {
        return new GoalObservation(goal.id, outcome.id, participantId, value, outcome.unit, null, measuredAt, null, null,
                recorder, measuredAt);
    }
}
