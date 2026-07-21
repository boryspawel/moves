package com.motionecosystem.loadanalysis;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.motionecosystem.exercisecatalog.api.ExerciseCatalogQueryPort.*;
import com.motionecosystem.loadanalysis.api.PlannedLoadCalculationPort.LoadCalculationVersion;
import com.motionecosystem.loadanalysis.domain.LoadCalculator;
import com.motionecosystem.trainingplanning.api.PlanRevisionQueryPort.*;
import org.junit.jupiter.api.Test;

class LoadCalculatorTest {
    UUID child = UUID.randomUUID();
    UUID parent = UUID.randomUUID();
    UUID exerciseVersion = UUID.randomUUID();

    @Test
    void separatesChannelsSidesIntervalsAndParentRollupsWithoutDescriptiveArithmetic() {
        var plan = plan(List.of(
                prescription(1, "LEFT", "DYNAMIC_RESISTANCE", 3, 8, null, null),
                prescription(2, "RIGHT", "ISOMETRIC", 2, null, 30, null),
                prescription(3, "BILATERAL", "IMPACT", 4, null, null, 10)));
        var exercise = exercise(List.of(
                contribution("DYN_EXU", "ALLOCATION", "AS_PRESCRIBED", ".25", ".50"),
                contribution("ISO_SEC", "ALLOCATION", "AS_PRESCRIBED", ".10", ".20"),
                contribution("IMPACT_CONTACTS", "ALLOCATION", "BILATERAL", ".50", ".75"),
                contribution("DYN_EXU", "DESCRIPTIVE_ONLY", "LEFT", "1", "1")));

        var profile = new LoadCalculator().calculate(plan, Map.of(exerciseVersion, exercise),
                Map.of(child, Set.of(parent)), new LoadCalculationVersion("v1", "default"),
                UUID.randomUUID(), "checksum", Instant.EPOCH);

        assertThat(profile.observations()).hasSize(3);
        assertThat(profile.observations()).extracting(item -> item.channel() + ":" + item.side())
                .containsExactlyInAnyOrder("DYN_EXU:LEFT", "ISO_SEC:RIGHT", "IMPACT_CONTACTS:BILATERAL");
        var dynamic = profile.observations().stream().filter(item -> item.channel().equals("DYN_EXU"))
                .findFirst().orElseThrow();
        assertThat(dynamic.low()).isEqualByComparingTo("6.000000");
        assertThat(dynamic.high()).isEqualByComparingTo("12.000000");
        assertThat(profile.aggregates()).anySatisfy(item -> {
            assertThat(item.structureId()).isEqualTo(parent);
            assertThat(item.observationFamily()).isEqualTo("DESCENDANT_ROLLUP");
        });
        assertThat(profile.aggregates()).allSatisfy(item ->
                assertThat(item.channel()).isNotEqualTo("SESSION_S_RPE"));
    }

    @Test
    void roundingIsDeterministicAndAlgorithmVersionBelongsToIdentity() {
        var plan = plan(List.of(prescription(1, "LEFT", "DYNAMIC_RESISTANCE", 1, 1, null, null)));
        var exercise = exercise(List.of(contribution("DYN_EXU", "ALLOCATION", "AS_PRESCRIBED",
                ".3333333", ".6666666")));
        LoadCalculator calculator = new LoadCalculator();
        var first = calculator.calculate(plan, Map.of(exerciseVersion, exercise), Map.of(),
                new LoadCalculationVersion("v1", "a"), UUID.randomUUID(), "one", Instant.EPOCH);
        var second = calculator.calculate(plan, Map.of(exerciseVersion, exercise), Map.of(),
                new LoadCalculationVersion("v2", "a"), UUID.randomUUID(), "two", Instant.EPOCH);
        assertThat(first.observations().getFirst().low()).isEqualByComparingTo("0.333333");
        assertThat(first.observations().getFirst().high()).isEqualByComparingTo("0.666667");
        assertThat(first.algorithmVersion()).isNotEqualTo(second.algorithmVersion());
    }

    @Test
    void directParentAllocationIsNotDoubleCountedWithDescendantRollup() {
        var plan = plan(List.of(prescription(1, "LEFT", "DYNAMIC_RESISTANCE", 3, 8, null, null)));
        var exercise = exercise(List.of(
                contributionAt(child, "DYN_EXU", "ALLOCATION", "LEFT", ".25", ".25"),
                contributionAt(parent, "DYN_EXU", "ALLOCATION", "LEFT", ".10", ".10")));
        var profile = new LoadCalculator().calculate(plan, Map.of(exerciseVersion, exercise),
                Map.of(child, Set.of(parent), parent, Set.of()), new LoadCalculationVersion("v1", "default"),
                UUID.randomUUID(), "checksum", Instant.EPOCH);
        var parentSession = profile.aggregates().stream().filter(item -> item.scope().equals("SESSION"))
                .filter(item -> item.structureId().equals(parent)).toList();
        assertThat(parentSession).extracting(item -> item.observationFamily() + ":" + item.low())
                .containsExactlyInAnyOrder("DIRECT_ALLOCATION:2.400000", "DESCENDANT_ROLLUP:6.000000");
    }

    private PlanRevisionSnapshot plan(List<PrescriptionSnapshot> prescriptions) {
        UUID revision = UUID.randomUUID(); UUID plan = UUID.randomUUID(); UUID cycle = UUID.randomUUID();
        UUID micro = UUID.randomUUID(); UUID session = UUID.randomUUID();
        return new PlanRevisionSnapshot(revision, plan, UUID.randomUUID(), 1, null, 0, "DRAFT",
                UUID.randomUUID(), "SPECIALIST", Instant.EPOCH, "NATIVE_V2", "NOT_ASSESSED",
                "phase", null, null, List.of(), List.of(new CycleSnapshot(cycle, 1, "cycle", null,
                null, "intent", "goal", List.of(new MicrocycleSnapshot(micro, 1, "micro", null,
                null, "intent", "goal", List.of(new SessionSnapshot(session, "session",
                LocalDate.of(2026, 8, 1), null, null, 60, "DRAFT", prescriptions)))))), List.of());
    }

    private PrescriptionSnapshot prescription(int position, String side, String type,
                                              Integer sets, Integer reps, Integer duration, Integer contacts) {
        return new PrescriptionSnapshot(UUID.randomUUID(), exerciseVersion, position, side, type,
                sets, reps, duration, null, contacts, null, null, null, null, null,
                null, null, null, null, null);
    }

    private PublishedExerciseVersionSnapshot exercise(List<ContributionSnapshot> contributions) {
        return new PublishedExerciseVersionSnapshot(UUID.randomUUID(), "exercise", exerciseVersion, 1, 2,
                Set.of(MovementPatternValue.SQUAT), Set.of(), contributions, List.of());
    }

    private ContributionSnapshot contribution(String channel, String calculationRole, String side,
                                               String low, String high) {
        return contributionAt(child, channel, calculationRole, side, low, high);
    }

    private ContributionSnapshot contributionAt(UUID structure, String channel, String calculationRole,
                                                 String side, String low, String high) {
        return new ContributionSnapshot(UUID.randomUUID(), structure, ContributionRoleValue.PRIMARY,
                LoadChannelValue.valueOf(channel), ContributionBandValue.MODERATE,
                new BigDecimal(low), new BigDecimal(high), "MEDIUM", "B",
                CalculationRoleValue.valueOf(calculationRole), null, SideRuleValue.valueOf(side), List.of());
    }
}
