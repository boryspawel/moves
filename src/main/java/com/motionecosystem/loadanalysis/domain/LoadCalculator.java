package com.motionecosystem.loadanalysis.domain;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.motionecosystem.exercisecatalog.api.ExerciseCatalogQueryPort.CalculationRoleValue;
import com.motionecosystem.exercisecatalog.api.ExerciseCatalogQueryPort.ContributionSnapshot;
import com.motionecosystem.exercisecatalog.api.ExerciseCatalogQueryPort.PublishedExerciseVersionSnapshot;
import com.motionecosystem.exercisecatalog.api.ExerciseCatalogQueryPort.SideRuleValue;
import com.motionecosystem.loadanalysis.api.PlannedLoadCalculationPort.Aggregate;
import com.motionecosystem.loadanalysis.api.PlannedLoadCalculationPort.LoadCalculationVersion;
import com.motionecosystem.loadanalysis.api.PlannedLoadCalculationPort.LoadProfile;
import com.motionecosystem.loadanalysis.api.PlannedLoadCalculationPort.Observation;
import com.motionecosystem.trainingplanning.api.PlanRevisionQueryPort.PlanRevisionSnapshot;
import com.motionecosystem.trainingplanning.api.PlanRevisionQueryPort.PrescriptionSnapshot;

public final class LoadCalculator {
    public static final MathContext MATH = new MathContext(18, RoundingMode.HALF_UP);
    public static final int SCALE = 6;

    public LoadProfile calculate(PlanRevisionSnapshot plan,
                                 Map<UUID, PublishedExerciseVersionSnapshot> catalog,
                                 Map<UUID, Set<UUID>> ancestors,
                                 LoadCalculationVersion version, UUID snapshotId,
                                 String checksum, Instant calculatedAt) {
        List<Observation> observations = new ArrayList<>();
        Map<Key, Range> aggregates = new LinkedHashMap<>();
        for (var cycle : plan.cycles()) {
            for (var micro : cycle.microcycles()) {
                for (var session : micro.sessions()) {
                    for (var prescription : session.prescriptions()) {
                        PublishedExerciseVersionSnapshot exercise = catalog.get(prescription.exerciseVersionId());
                        if (exercise == null) throw new IllegalArgumentException("published exercise profile is missing");
                        for (ContributionSnapshot contribution : exercise.contributions()) {
                            if (contribution.calculationRole() != CalculationRoleValue.ALLOCATION
                                    || contribution.variantCondition() != null
                                    && !"STANDARD".equals(contribution.variantCondition())) continue;
                            Dose dose = dose(prescription, contribution.loadChannel().name());
                            if (dose == null) continue;
                            BigDecimal low = rounded(dose.value.multiply(contribution.coefficientLow(), MATH));
                            BigDecimal high = rounded(dose.value.multiply(contribution.coefficientHigh(), MATH));
                            String side = side(prescription.side(), contribution.sideRule());
                            Observation observation = new Observation(prescription.id(), prescription.exerciseVersionId(),
                                    contribution.id(), session.id(), micro.id(), cycle.id(),
                                    contribution.anatomicalStructureId(), side, dose.channel,
                                    "DIRECT_ALLOCATION", dose.unit, low, high, contribution.confidenceClass(),
                                    contribution.evidenceGrade(), dose.source, "CATALOG_INTERVAL");
                            observations.add(observation);
                            addScopes(aggregates, observation, session.scheduledDate() == null
                                    ? null : session.scheduledDate().toString(), plan.revisionId());
                            for (UUID ancestor : ancestors.getOrDefault(contribution.anatomicalStructureId(), Set.of())) {
                                Observation rollup = new Observation(observation.prescriptionId(),
                                        observation.exerciseVersionId(), observation.contributionId(),
                                        observation.sessionId(), observation.microcycleId(), observation.cycleId(),
                                        ancestor, side, dose.channel, "DESCENDANT_ROLLUP", dose.unit,
                                        low, high, observation.confidence(), observation.evidenceGrade(),
                                        dose.source, "ANATOMY_ROLLUP");
                                addScopes(aggregates, rollup, session.scheduledDate() == null
                                        ? null : session.scheduledDate().toString(), plan.revisionId());
                            }
                        }
                    }
                }
            }
        }
        observations.sort(Comparator.comparing((Observation item) -> item.sessionId().toString())
                .thenComparing(item -> item.prescriptionId().toString())
                .thenComparing(item -> item.contributionId().toString()));
        List<Aggregate> result = aggregates.entrySet().stream().map(entry -> new Aggregate(
                        entry.getKey().scope, entry.getKey().scopeKey, entry.getKey().structureId,
                        entry.getKey().side, entry.getKey().channel, entry.getKey().family,
                        entry.getKey().unit, rounded(entry.getValue().low), rounded(entry.getValue().high)))
                .sorted(Comparator.comparing(Aggregate::scope).thenComparing(Aggregate::scopeKey)
                        .thenComparing(item -> item.structureId().toString()).thenComparing(Aggregate::side)
                        .thenComparing(Aggregate::channel).thenComparing(Aggregate::observationFamily))
                .toList();
        String catalogVersion = catalog.values().stream().map(item -> "v" + item.profileSchemaVersion())
                .distinct().sorted().collect(java.util.stream.Collectors.joining(","));
        return new LoadProfile(snapshotId, plan.revisionId(), checksum, version.algorithmVersion(),
                version.configurationVersion(), catalogVersion, calculatedAt, observations, result);
    }

    private static void addScopes(Map<Key, Range> values, Observation item, String day, UUID revisionId) {
        add(values, key("SESSION", item.sessionId().toString(), item), item);
        add(values, key("MICROCYCLE", item.microcycleId().toString(), item), item);
        add(values, key("CYCLE", item.cycleId().toString(), item), item);
        add(values, key("REVISION_WINDOW", revisionId.toString(), item), item);
        if (day != null) add(values, key("DAY", day, item), item);
    }

    private static Key key(String scope, String scopeKey, Observation item) {
        return new Key(scope, scopeKey, item.structureId(), item.side(), item.channel(),
                item.observationFamily(), item.unit());
    }

    private static void add(Map<Key, Range> values, Key key, Observation item) {
        values.merge(key, new Range(item.low(), item.high()),
                (left, right) -> new Range(left.low.add(right.low, MATH), left.high.add(right.high, MATH)));
    }

    private static Dose dose(PrescriptionSnapshot item, String channel) {
        return switch (item.doseType()) {
            case "DYNAMIC_RESISTANCE" -> "DYN_EXU".equals(channel)
                    ? new Dose(channel, "EXU", BigDecimal.valueOf((long) item.sets() * item.repetitions()),
                    "SETS_X_REPETITIONS") : null;
            case "ISOMETRIC" -> "ISO_SEC".equals(channel)
                    ? new Dose(channel, "s", BigDecimal.valueOf((long) item.sets() * item.durationSeconds()),
                    "SETS_X_DURATION_SECONDS") : null;
            case "IMPACT" -> "IMPACT_CONTACTS".equals(channel)
                    ? new Dose(channel, "contacts", BigDecimal.valueOf((long) item.sets() * item.contacts()),
                    "SETS_X_CONTACTS") : null;
            case "ENDURANCE" -> "ENDURANCE_MIN_ZONE".equals(channel) && item.durationSeconds() != null
                    && "ZONE".equals(item.intensityType()) && item.intensityZone() != null
                    ? new Dose(channel, "min", BigDecimal.valueOf(item.durationSeconds())
                    .divide(BigDecimal.valueOf(60), MATH), "DURATION_MINUTES_" + item.intensityZone()) : null;
            default -> null;
        };
    }

    private static String side(String prescribed, SideRuleValue rule) {
        return switch (rule) {
            case AS_PRESCRIBED -> prescribed;
            case BILATERAL -> "BILATERAL";
            case LEFT -> "LEFT";
            case RIGHT -> "RIGHT";
            case NOT_APPLICABLE -> "NOT_APPLICABLE";
        };
    }

    private static BigDecimal rounded(BigDecimal value) {
        return value.setScale(SCALE, RoundingMode.HALF_UP);
    }

    private record Dose(String channel, String unit, BigDecimal value, String source) { }
    private record Key(String scope, String scopeKey, UUID structureId, String side,
                       String channel, String family, String unit) { }
    private record Range(BigDecimal low, BigDecimal high) { }
}
