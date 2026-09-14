package com.motionecosystem.trainingplanning;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.motionecosystem.trainingplanning.api.PlanRevisionQueryPort.CycleSnapshot;
import com.motionecosystem.trainingplanning.api.PlanRevisionQueryPort.GoalOutcomeSnapshot;
import com.motionecosystem.trainingplanning.api.PlanRevisionQueryPort.GoalSnapshot;
import com.motionecosystem.trainingplanning.api.PlanRevisionQueryPort.MicrocycleSnapshot;
import com.motionecosystem.trainingplanning.api.PlanRevisionQueryPort.PlanRevisionSnapshot;
import com.motionecosystem.trainingplanning.api.PlanRevisionQueryPort.PrescriptionSnapshot;
import com.motionecosystem.trainingplanning.api.PlanRevisionQueryPort.SessionSnapshot;
import org.junit.jupiter.api.Test;

class TrainingPlanningChecksumTest {

    @Test
    void includesSourceGoalAndMaterializedExerciseSetSemanticsDeterministically() {
        PlanRevisionSnapshot base = revision(UUID.randomUUID(), 4L, "Return to running", "20", UUID.randomUUID(), UUID.randomUUID(),
                "{\"dose\":{\"type\":\"STRENGTH\",\"sets\":3,\"reps\":8}}");
        String checksum = TrainingPlanningV2Service.checksum(base);

        assertThat(TrainingPlanningV2Service.checksum(base)).isEqualTo(checksum);
        assertThat(TrainingPlanningV2Service.checksum(revision(base.goals().getFirst().sourceParticipantGoalId(), 4L,
                "Return to running", "20", base.cycles().getFirst().microcycles().getFirst().sessions().getFirst().sourceExerciseSetId(),
                base.cycles().getFirst().microcycles().getFirst().sessions().getFirst().sourceExerciseSetVersionId(),
                "{\"dose\":{\"type\":\"STRENGTH\",\"sets\":3,\"reps\":8}}"))).isEqualTo(checksum);

        assertThat(TrainingPlanningV2Service.checksum(revision(UUID.randomUUID(), 4L, "Return to running", "20",
                sourceSetId(base), sourceSetVersionId(base), materializedDose(base)))).isNotEqualTo(checksum);
        assertThat(TrainingPlanningV2Service.checksum(revision(base.goals().getFirst().sourceParticipantGoalId(), 5L,
                "Changed title", "25", sourceSetId(base), sourceSetVersionId(base), materializedDose(base)))).isNotEqualTo(checksum);
        assertThat(TrainingPlanningV2Service.checksum(revision(base.goals().getFirst().sourceParticipantGoalId(), 4L,
                "Return to running", "20", UUID.randomUUID(), UUID.randomUUID(), materializedDose(base)))).isNotEqualTo(checksum);
        assertThat(TrainingPlanningV2Service.checksum(revision(base.goals().getFirst().sourceParticipantGoalId(), 4L,
                "Return to running", "20", sourceSetId(base), sourceSetVersionId(base),
                "{\"dose\":{\"type\":\"STRENGTH\",\"sets\":4,\"reps\":8}}"))).isNotEqualTo(checksum);
    }

    private static PlanRevisionSnapshot revision(UUID sourceGoalId, long sourceGoalVersion, String title, String target,
                                                 UUID setId, UUID setVersionId, String materializedDose) {
        UUID revisionId = UUID.nameUUIDFromBytes("revision".getBytes());
        UUID goalId = UUID.nameUUIDFromBytes("goal".getBytes());
        UUID cycleId = UUID.nameUUIDFromBytes("cycle".getBytes());
        UUID microcycleId = UUID.nameUUIDFromBytes("microcycle".getBytes());
        UUID sessionId = UUID.nameUUIDFromBytes("session".getBytes());
        UUID prescriptionId = UUID.nameUUIDFromBytes("prescription".getBytes());
        GoalSnapshot goal = new GoalSnapshot(goalId, sourceGoalId, sourceGoalVersion, Instant.EPOCH,
                "PERFORMANCE", "PERFORMANCE", title, 1, "ACTIVE", LocalDate.of(2027, 1, 1),
                List.of(new GoalOutcomeSnapshot(UUID.nameUUIDFromBytes("outcome".getBytes()), "RUN_5K",
                        new java.math.BigDecimal("10"), new java.math.BigDecimal(target), "minutes", "TEST", "OBSERVATION")));
        PrescriptionSnapshot prescription = new PrescriptionSnapshot(prescriptionId, UUID.nameUUIDFromBytes("item".getBytes()),
                setVersionId, materializedDose, "STRENGTH", UUID.nameUUIDFromBytes("exercise".getBytes()), 1,
                "BILATERAL", "DYNAMIC_RESISTANCE", 3, 8, null, null, null, null, null,
                null, null, null, null, null, null, null, null);
        SessionSnapshot session = new SessionSnapshot(sessionId, setId, setVersionId, "Session", LocalDate.of(2026, 8, 1),
                null, null, 30, "DRAFT", "{\"source\":\"set\"}", List.of(prescription), List.of());
        MicrocycleSnapshot microcycle = new MicrocycleSnapshot(microcycleId, 1, "Micro", null, null,
                "intent", "goal", List.of(session));
        CycleSnapshot cycle = new CycleSnapshot(cycleId, 1, "Cycle", null, null, "intent", "goal", List.of(microcycle));
        return new PlanRevisionSnapshot(revisionId, UUID.nameUUIDFromBytes("plan".getBytes()),
                UUID.nameUUIDFromBytes("participant".getBytes()), 1, null, 0, "DRAFT",
                UUID.nameUUIDFromBytes("author".getBytes()), "PLAN_PERFORMANCE", Instant.EPOCH,
                "NATIVE_V2", "NOT_ASSESSED", "phase", null, null, List.of(goal), List.of(cycle), List.of());
    }

    private static UUID sourceSetId(PlanRevisionSnapshot revision) {
        return revision.cycles().getFirst().microcycles().getFirst().sessions().getFirst().sourceExerciseSetId();
    }

    private static UUID sourceSetVersionId(PlanRevisionSnapshot revision) {
        return revision.cycles().getFirst().microcycles().getFirst().sessions().getFirst().sourceExerciseSetVersionId();
    }

    private static String materializedDose(PlanRevisionSnapshot revision) {
        return revision.cycles().getFirst().microcycles().getFirst().sessions().getFirst()
                .prescriptions().getFirst().materializedSnapshot();
    }
}
