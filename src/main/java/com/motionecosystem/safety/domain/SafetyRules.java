package com.motionecosystem.safety.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Explicit Java strategies for Safety V2. No medical inference or diagnosis is performed here. */
public final class SafetyRules {

    public static final String RULESET_CODE = "SAFETY_V2";
    public static final int RULESET_VERSION = 1;

    public Evaluation evaluate(
            List<RestrictionFact> restrictions,
            List<ObservationFact> observations,
            Map<UUID, Set<UUID>> ancestors,
            Map<UUID, LocalDate> sessionDates) {
        List<Factor> factors = new ArrayList<>();
        for (RestrictionFact restriction : restrictions) {
            List<ObservationFact> matching = observations.stream()
                    .filter(observation -> matches(restriction.target(), observation, ancestors))
                    .toList();
            if (matching.isEmpty()) {
                if (restriction.target().movementPattern() != null
                        || restriction.target().rangeOfMotion() != null
                        || restriction.target().contractionType() != null) {
                    factors.add(new Factor(
                            Result.WARNING,
                            "LOW_CONFIDENCE_MAPPING",
                            targetRef(restriction.target()),
                            restriction.target().channel(),
                            null,
                            null,
                            null,
                            null,
                            restriction.target().unit(),
                            "SAFETY_TARGET_NOT_PRESENT_IN_LOAD_SNAPSHOT",
                            "UNMAPPED",
                            false));
                }
                continue;
            }
            addIntersectionFactor(restriction, matching.getFirst(), factors);
            addLimitFactors(restriction, matching, factors);
            addRecoveryFactor(restriction, matching, sessionDates, factors);
        }
        observations.stream()
                .filter(observation -> "LOW".equalsIgnoreCase(observation.confidence()))
                .forEach(observation -> factors.add(new Factor(
                        Result.WARNING,
                        "LOW_CONFIDENCE_MAPPING",
                        observation.structureId().toString(),
                        observation.channel(),
                        observation.low(),
                        observation.high(),
                        null,
                        null,
                        observation.unit(),
                        "SAFETY_MAPPING_REVIEW_REQUIRED",
                        observation.evidenceGrade(),
                        false)));
        Result result = factors.stream()
                .map(Factor::result)
                .max(Comparator.comparingInt(Result::ordinal))
                .orElse(Result.PASS);
        return new Evaluation(result, factors);
    }

    private static void addIntersectionFactor(
            RestrictionFact restriction, ObservationFact observation, List<Factor> factors) {
        Result result;
        String rule;
        boolean overridable = false;
        if (restriction.sourceType() == SourceType.PARTICIPANT_DECLARED) {
            result = restriction.semanticType() == SemanticType.MONITOR ? Result.INFO : Result.WARNING;
            rule = "PARTICIPANT_DECLARATION";
        } else if (restriction.semanticType() == SemanticType.CONTRAINDICATION) {
            result = Result.HARD_BLOCK;
            rule = "HARD_RESTRICTION_INTERSECTION";
            overridable = restriction.sourceType() == SourceType.PHYSIOTHERAPIST;
        } else if (restriction.semanticType() == SemanticType.MONITOR) {
            result = Result.INFO;
            rule = "RESTRICTION_MONITOR";
        } else {
            result = Result.WARNING;
            rule = "RESTRICTION_CAUTION";
        }
        factors.add(factor(restriction, observation, result, rule,
                "SAFETY_RESTRICTION_INTERSECTION", overridable, null, null));
    }

    private static void addLimitFactors(
            RestrictionFact restriction, List<ObservationFact> observations, List<Factor> factors) {
        TargetFact target = restriction.target();
        if (target.limitHigh() == null || target.unit() == null) {
            return;
        }
        observations.stream()
                .filter(observation -> target.unit().equalsIgnoreCase(observation.unit()))
                .filter(observation -> observation.high().compareTo(target.limitHigh()) > 0)
                .forEach(observation -> factors.add(factor(
                        restriction,
                        observation,
                        Result.WARNING,
                        "EXPLICIT_LIMIT",
                        "SAFETY_EXPLICIT_LIMIT_EXCEEDED",
                        false,
                        target.limitLow(),
                        target.limitHigh())));
    }

    private static void addRecoveryFactor(
            RestrictionFact restriction,
            List<ObservationFact> observations,
            Map<UUID, LocalDate> sessionDates,
            List<Factor> factors) {
        Integer minimumHours = restriction.target().minimumRecoveryHours();
        if (minimumHours == null) {
            return;
        }
        List<ObservationFact> ordered = observations.stream()
                .filter(item -> sessionDates.get(item.sessionId()) != null)
                .sorted(Comparator.comparing(item -> sessionDates.get(item.sessionId())))
                .toList();
        for (int index = 1; index < ordered.size(); index++) {
            long observedHours = ChronoUnit.DAYS.between(
                    sessionDates.get(ordered.get(index - 1).sessionId()),
                    sessionDates.get(ordered.get(index).sessionId())) * 24;
            if (observedHours < minimumHours) {
                ObservationFact observation = ordered.get(index);
                factors.add(new Factor(
                        Result.WARNING,
                        "MINIMUM_RECOVERY",
                        targetRef(restriction.target()),
                        observation.channel(),
                        BigDecimal.valueOf(observedHours),
                        BigDecimal.valueOf(observedHours),
                        BigDecimal.valueOf(minimumHours),
                        BigDecimal.valueOf(minimumHours),
                        "HOUR",
                        "SAFETY_MINIMUM_RECOVERY_NOT_MET",
                        observation.evidenceGrade(),
                        false));
                return;
            }
        }
    }

    private static Factor factor(
            RestrictionFact restriction,
            ObservationFact observation,
            Result result,
            String rule,
            String explanation,
            boolean overridable,
            BigDecimal thresholdLow,
            BigDecimal thresholdHigh) {
        return new Factor(
                result,
                rule,
                targetRef(restriction.target()),
                observation.channel(),
                observation.low(),
                observation.high(),
                thresholdLow,
                thresholdHigh,
                observation.unit(),
                explanation,
                observation.evidenceGrade(),
                overridable);
    }

    private static boolean matches(
            TargetFact target,
            ObservationFact observation,
            Map<UUID, Set<UUID>> ancestors) {
        boolean structure = target.structureId() == null
                || target.structureId().equals(observation.structureId())
                || ancestors.getOrDefault(observation.structureId(), Set.of()).contains(target.structureId());
        boolean side = target.side() == null
                || "ANY".equalsIgnoreCase(target.side())
                || target.side().equalsIgnoreCase(observation.side());
        boolean channel = target.channel() == null
                || target.channel().equalsIgnoreCase(observation.channel());
        boolean characteristic = target.loadCharacteristic() == null
                || target.loadCharacteristic().equalsIgnoreCase(observation.observationFamily());
        boolean dimensionsAvailable = target.movementPattern() == null
                && target.rangeOfMotion() == null
                && target.contractionType() == null;
        return structure && side && channel && characteristic && dimensionsAvailable;
    }

    private static String targetRef(TargetFact target) {
        return "structure=" + target.structureId()
                + ";pattern=" + target.movementPattern()
                + ";channel=" + target.channel()
                + ";characteristic=" + target.loadCharacteristic()
                + ";side=" + target.side();
    }

    public enum SourceType {
        PARTICIPANT_DECLARED,
        PHYSIOTHERAPIST,
        SYSTEM_OPERATIONAL
    }

    public enum SemanticType {
        CONTRAINDICATION,
        CAUTION,
        LIMIT,
        MONITOR
    }

    public enum Result {
        PASS,
        INFO,
        WARNING,
        HARD_BLOCK
    }

    public record TargetFact(
            UUID structureId,
            String movementPattern,
            String channel,
            String loadCharacteristic,
            String side,
            String rangeOfMotion,
            String contractionType,
            BigDecimal limitLow,
            BigDecimal limitHigh,
            String unit,
            Integer minimumRecoveryHours) {
    }

    public record RestrictionFact(
            UUID id,
            SourceType sourceType,
            SemanticType semanticType,
            TargetFact target) {
    }

    public record ObservationFact(
            UUID sessionId,
            UUID structureId,
            String side,
            String channel,
            String observationFamily,
            String unit,
            BigDecimal low,
            BigDecimal high,
            String confidence,
            String evidenceGrade) {
    }

    public record Factor(
            Result result,
            String ruleCode,
            String targetRef,
            String channel,
            BigDecimal observedLow,
            BigDecimal observedHigh,
            BigDecimal thresholdLow,
            BigDecimal thresholdHigh,
            String unit,
            String explanationCode,
            String evidenceGrade,
            boolean overridable) {
    }

    public record Evaluation(Result result, List<Factor> factors) {
        public Evaluation {
            factors = List.copyOf(factors);
        }
    }
}
