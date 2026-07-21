package com.motionecosystem.safety.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.motionecosystem.safety.domain.SafetyRules.ObservationFact;
import com.motionecosystem.safety.domain.SafetyRules.RestrictionFact;
import com.motionecosystem.safety.domain.SafetyRules.Result;
import com.motionecosystem.safety.domain.SafetyRules.SemanticType;
import com.motionecosystem.safety.domain.SafetyRules.SourceType;
import com.motionecosystem.safety.domain.SafetyRules.TargetFact;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SafetyRulesTest {

    private final SafetyRules rules = new SafetyRules();
    private final UUID session = UUID.randomUUID();
    private final UUID structure = UUID.randomUUID();

    @Test
    void appliesExplicitSourceAndSemanticResultMatrix() {
        for (SourceType source : SourceType.values()) {
            for (SemanticType semantic : SemanticType.values()) {
                Result expected = switch (source) {
                    case PARTICIPANT_DECLARED -> semantic == SemanticType.MONITOR
                            ? Result.INFO : Result.WARNING;
                    case PHYSIOTHERAPIST, SYSTEM_OPERATIONAL -> switch (semantic) {
                        case CONTRAINDICATION -> Result.HARD_BLOCK;
                        case MONITOR -> Result.INFO;
                        case CAUTION, LIMIT -> Result.WARNING;
                    };
                };
                var result = rules.evaluate(
                        List.of(restriction(source, semantic, target(structure, "LEFT", "VOLUME"))),
                        List.of(observation(session, structure, "LEFT", "VOLUME", "HIGH")),
                        Map.of(),
                        Map.of(session, LocalDate.of(2026, 1, 1)));
                assertThat(result.result())
                        .as("%s × %s", source, semantic)
                        .isEqualTo(expected);
            }
        }
    }

    @Test
    void matchesParentStructureSideAndChannelAndRejectsOtherDimensions() {
        UUID parent = UUID.randomUUID();
        RestrictionFact restriction = restriction(
                SourceType.PHYSIOTHERAPIST,
                SemanticType.CONTRAINDICATION,
                target(parent, "LEFT", "VOLUME"));

        assertThat(rules.evaluate(
                        List.of(restriction),
                        List.of(observation(session, structure, "LEFT", "VOLUME", "HIGH")),
                        Map.of(structure, Set.of(parent)),
                        Map.of()).result())
                .isEqualTo(Result.HARD_BLOCK);
        assertThat(rules.evaluate(
                        List.of(restriction),
                        List.of(observation(session, structure, "RIGHT", "VOLUME", "HIGH")),
                        Map.of(structure, Set.of(parent)),
                        Map.of()).result())
                .isEqualTo(Result.PASS);
        assertThat(rules.evaluate(
                        List.of(restriction),
                        List.of(observation(session, structure, "LEFT", "INTENSITY", "HIGH")),
                        Map.of(structure, Set.of(parent)),
                        Map.of()).result())
                .isEqualTo(Result.PASS);
    }

    @Test
    void evaluatesExplicitUnitLimitAndMinimumRecoveryOnlyWhenConfigured() {
        UUID secondSession = UUID.randomUUID();
        TargetFact target = new TargetFact(
                structure, null, "VOLUME", "EXTERNAL_LOAD", "LEFT", null, null,
                BigDecimal.ZERO, BigDecimal.TEN, "KG_REPETITION", 48);
        var result = rules.evaluate(
                List.of(restriction(SourceType.PHYSIOTHERAPIST, SemanticType.LIMIT, target)),
                List.of(
                        observation(session, structure, "LEFT", "VOLUME", "HIGH"),
                        observation(secondSession, structure, "LEFT", "VOLUME", "HIGH")),
                Map.of(),
                Map.of(
                        session, LocalDate.of(2026, 1, 1),
                        secondSession, LocalDate.of(2026, 1, 2)));

        assertThat(result.factors())
                .extracting(SafetyRules.Factor::ruleCode)
                .contains("EXPLICIT_LIMIT", "MINIMUM_RECOVERY");
        assertThat(result.factors().stream()
                        .filter(factor -> factor.ruleCode().equals("EXPLICIT_LIMIT"))
                        .findFirst().orElseThrow().thresholdHigh())
                .isEqualByComparingTo("10");
    }

    @Test
    void reportsUnavailableMovementMappingWithoutInventingAClinicalBlock() {
        TargetFact movementOnly = new TargetFact(
                null, "SQUAT", null, null, null, null, null,
                null, null, null, null);

        var result = rules.evaluate(
                List.of(restriction(
                        SourceType.PHYSIOTHERAPIST,
                        SemanticType.CONTRAINDICATION,
                        movementOnly)),
                List.of(observation(session, structure, "LEFT", "VOLUME", "LOW")),
                Map.of(),
                Map.of());

        assertThat(result.result()).isEqualTo(Result.WARNING);
        assertThat(result.factors())
                .allMatch(factor -> factor.ruleCode().equals("LOW_CONFIDENCE_MAPPING"));
    }

    private RestrictionFact restriction(
            SourceType source, SemanticType semantic, TargetFact target) {
        return new RestrictionFact(UUID.randomUUID(), source, semantic, target);
    }

    private static TargetFact target(UUID structure, String side, String channel) {
        return new TargetFact(
                structure, null, channel, null, side, null, null,
                null, null, null, null);
    }

    private static ObservationFact observation(
            UUID session,
            UUID structure,
            String side,
            String channel,
            String confidence) {
        return new ObservationFact(
                session,
                structure,
                side,
                channel,
                "EXTERNAL_LOAD",
                "KG_REPETITION",
                BigDecimal.valueOf(12),
                BigDecimal.valueOf(12),
                confidence,
                "CURATED");
    }
}
