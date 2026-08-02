package com.motionecosystem.exercisesets.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.math.BigDecimal;
import java.util.UUID;
import com.motionecosystem.exercisecatalog.api.ExerciseCatalogQueryPort.*;
import com.motionecosystem.exercisesets.api.ExerciseSetDtos.*;
import com.motionecosystem.exercisesets.domain.ExerciseSetModel.*;
import org.junit.jupiter.api.Test;

class ExerciseSetAnalyzerTest {
    private final ExerciseSetAnalyzer analyzer = new ExerciseSetAnalyzer();

    @Test
    void isDeterministicAndSortsFindingsByStableCode() {
        var version = version(SetProfile.FULL_SELF_GUIDED, item(1, Phase.MAIN, new ExerciseSetEntities.StrengthDoseEntity(), "[\"band\"]"));
        var first = analyzer.analyze(version, true);
        var second = analyzer.analyze(version, true);
        assertThat(first).isEqualTo(second);
        assertThat(first.findings()).extracting(AnalysisFinding::code).isSorted();
        assertThat(first.analyzedAt()).isNull();
    }

    @Test
    void appliesRequiredPhasesOnlyToProfilesThatRequireThem() {
        assertBlocked(SetProfile.FULL_SELF_GUIDED, Phase.MAIN);
        assertBlocked(SetProfile.WARMUP_MODULE, Phase.MAIN);
        assertBlocked(SetProfile.MAIN_MODULE, Phase.PREPARATION);
        assertThat(analyzer.analyze(version(SetProfile.ACCESSORY_MODULE, item(1, Phase.MAIN, strength(), "[]")), true).status()).isEqualTo(AnalysisStatus.SUGGESTIONS_AVAILABLE);
        assertThat(analyzer.analyze(version(SetProfile.COOLDOWN_MODULE, item(1, Phase.MAIN, strength(), "[]")), true).status()).isEqualTo(AnalysisStatus.SUGGESTIONS_AVAILABLE);
        for (var profile : List.of(SetProfile.HOME, SetProfile.THERAPEUTIC, SetProfile.MOBILITY, SetProfile.STRETCHING, SetProfile.BREATHING))
            assertThat(analyzer.analyze(version(profile, item(1, Phase.ACCESSORY, strength(), "[]")), true).status()).isEqualTo(AnalysisStatus.NO_SUGGESTIONS);
    }

    @Test
    void estimatesEveryDoseKindAndReportsPartialAndUnavailableConfidence() {
        var v = version(SetProfile.HOME,
                item(1, Phase.MAIN, strength(), "[]"), item(2, Phase.MAIN, isometric(), "[]"), item(3, Phase.MAIN, mobility(), "[]"),
                item(4, Phase.MAIN, stretch(), "[]"), item(5, Phase.MAIN, breathing(), "[]"), item(6, Phase.MAIN, aerobic(), "[]"));
        var complete = analyzer.analyze(v, true);
        assertThat(complete.metrics().estimatedSeconds()).isPositive();
        assertThat(complete.metrics().timeConfidence()).isEqualTo(TimeConfidence.COMPLETE);
        v.items.get(0).dose = new ExerciseSetEntities.StrengthDoseEntity();
        assertThat(analyzer.analyze(v, true).metrics().timeConfidence()).isEqualTo(TimeConfidence.PARTIAL);
        assertThat(analyzer.analyze(version(SetProfile.HOME), true).metrics().timeConfidence()).isEqualTo(TimeConfidence.UNAVAILABLE);
    }

    @Test
    void reportsDuplicatesEquipmentSwitchesAndSnapshotDataLimitsWithoutBlocking() {
        UUID duplicate = UUID.randomUUID();
        var first = item(1, Phase.MAIN, strength(), "[\"band\"]"); first.exerciseVersionId = duplicate;
        var second = item(2, Phase.MAIN, mobility(), "[\"chair\"]"); second.exerciseVersionId = duplicate;
        var third = item(3, Phase.MAIN, aerobic(), "[\"mat\"]");
        var fourth = item(4, Phase.MAIN, stretch(), "[\"wall\"]");
        var analysis = analyzer.analyze(version(SetProfile.HOME, first, second, third, fourth), true);
        assertThat(analysis.status()).isEqualTo(AnalysisStatus.SUGGESTIONS_AVAILABLE);
        assertThat(analysis.metrics().equipmentTransitions()).isEqualTo(3);
        assertThat(analysis.findings()).extracting(AnalysisFinding::code).contains("DUPLICATE_EXACT_EXERCISE_VERSION", "CONSECUTIVE_DUPLICATE_EXERCISE", "EQUIPMENT_TRANSITIONS", "DOSE_KIND_SWITCHING");
    }

    @Test
    void blocksInvalidOrderingAndMissingStructuralData() {
        var late = item(1, Phase.MAIN, strength(), "[]");
        var early = item(2, Phase.PREPARATION, strength(), "[]");
        early.exerciseVersionId = null;
        var analysis = analyzer.analyze(version(SetProfile.FULL_SELF_GUIDED, late, early), true);
        assertThat(analysis.status()).isEqualTo(AnalysisStatus.SUGGESTIONS_AVAILABLE);
        assertThat(analysis.findings()).filteredOn(f -> !f.blocking()).extracting(AnalysisFinding::code)
                .contains("INVALID_PHASE_ORDER", "INVALID_ITEM");
    }

    @Test
    void neverThrowsForMissingFieldsAndMarksTheDraftBlocked() {
        var malformed = item(1, Phase.MAIN, strength(), null);
        malformed.phase = null;
        malformed.exerciseVersionId = null;
        malformed.dose = null;
        var analysis = analyzer.analyze(version(SetProfile.HOME, malformed), true);
        assertThat(analysis.status()).isEqualTo(AnalysisStatus.SUGGESTIONS_AVAILABLE);
        assertThat(analysis.findings()).extracting(AnalysisFinding::code).contains("PHASE_REQUIRED", "INVALID_ITEM", "TIME_ESTIMATE_PARTIAL");
    }

    @Test
    void handlesUnrepresentableAndZeroTimeWithoutOverflow() {
        var huge = strength(); huge.sets = Integer.MAX_VALUE; huge.reps = Integer.MAX_VALUE; huge.restSeconds = Integer.MAX_VALUE;
        var overflow = analyzer.analyze(version(SetProfile.HOME, item(1, Phase.MAIN, huge, "[]")), true);
        assertThat(overflow.metrics().estimatedSeconds()).isNull();
        assertThat(overflow.metrics().timeConfidence()).isEqualTo(TimeConfidence.UNAVAILABLE);
        assertThat(overflow.findings()).extracting(AnalysisFinding::code).contains("TIME_ESTIMATE_OVERFLOW");
        var zero = strength(); zero.sets = 0; zero.reps = 0; zero.restSeconds = 0;
        assertThat(analyzer.analyze(version(SetProfile.HOME, item(1, Phase.MAIN, zero, "[]")), true).findings())
                .extracting(AnalysisFinding::code).contains("TIME_ESTIMATE_UNAVAILABLE");
    }

    @Test
    void returnsValidWhenTheSnapshotHasNoStructuralSignals() {
        var analysis = analyzer.analyze(version(SetProfile.HOME, item(1, Phase.MAIN, strength(), "[]")), true);
        assertThat(analysis.status()).isEqualTo(AnalysisStatus.NO_SUGGESTIONS);
        assertThat(analysis.findings()).isEmpty();
    }

    @Test
    void keepsAnatomicalExposureChannelsSeparateAndReportsUnavailableItemSnapshots() {
        var exerciseVersionId = UUID.randomUUID();
        var item = item(1, Phase.MAIN, strength(), "[]"); item.exerciseVersionId = exerciseVersionId;
        var contribution = new ContributionSnapshot(UUID.randomUUID(), UUID.randomUUID(), "MUSCLE:TEST", "MUSCLE", ContributionRoleValue.PRIMARY,
                LoadChannelValue.DYN_EXU, ContributionBandValue.HIGH, new BigDecimal("0.2"), new BigDecimal("0.7"),
                "MODERATE", "EDITORIAL_REVIEW", CalculationRoleValue.ALLOCATION, "STANDARD", SideRuleValue.BILATERAL, List.of());
        var snapshot = new PublishedExerciseVersionSnapshot(UUID.randomUUID(), "Exercise", exerciseVersionId, 1, 1,
                Set.of(MovementPatternValue.PUSH), Set.of(), List.of(contribution), List.of());
        var itemSnapshot = new ExerciseSetAnalyzer.ItemAnatomySnapshot(exerciseVersionId, 1, 1, List.of("PUSH"), List.of(new ExerciseSetAnalyzer.ItemAnatomyContribution(contribution.id(), contribution.anatomicalStructureId(), contribution.anatomicalStructureCode(), contribution.anatomicalStructureType(), contribution.role().name(), contribution.loadChannel().name(), contribution.coefficientLow(), contribution.coefficientHigh(), contribution.confidenceClass(), contribution.evidenceGrade(), true, contribution.variantCondition(), contribution.sideRule().name(), List.of())));
        var complete = analyzer.analyzeAnatomy(version(SetProfile.HOME, item), true, Map.of(item.id, itemSnapshot));
        assertThat(complete.completeness()).isEqualTo(AnatomyCompleteness.COMPLETE);
        assertThat(complete.channels()).singleElement().extracting(AnatomyChannel::loadChannel).isEqualTo("DYN_EXU");
        assertThat(complete.directStructureExposures()).singleElement().extracting(AnatomyStructureExposure::coefficientHigh).isEqualTo(new BigDecimal("0.7"));
        var unavailable = analyzer.analyzeAnatomy(version(SetProfile.HOME, item), true, Map.of());
        assertThat(unavailable.completeness()).isEqualTo(AnatomyCompleteness.PARTIAL);
        assertThat(unavailable.missing()).singleElement().extracting(AnatomyMissingData::code).isEqualTo("PUBLISHED_EXERCISE_SNAPSHOT_UNAVAILABLE");
    }

    @Test
    void aggregatesHierarchyPathsOnceAndExcludesTheLessSpecificSource() {
        UUID itemId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID exerciseVersionId = UUID.fromString("00000000-0000-0000-0000-000000000002");
        UUID parentId = UUID.fromString("00000000-0000-0000-0000-000000000010");
        UUID childId = UUID.fromString("00000000-0000-0000-0000-000000000011");
        UUID branchId = UUID.fromString("00000000-0000-0000-0000-000000000012");
        var item = item(1, Phase.MAIN, strength(), "[]"); item.id = itemId; item.exerciseVersionId = exerciseVersionId;
        var parent = contribution(UUID.fromString("00000000-0000-0000-0000-000000000020"), parentId, "PARENT", "BILATERAL", List.of(), null);
        var child = contribution(UUID.fromString("00000000-0000-0000-0000-000000000021"), childId, "CHILD", "BILATERAL", List.of(
                path(new ExerciseSetAnalyzer.ItemAnatomyStructure(parentId, "PARENT", "MUSCLE_GROUP")),
                path(new ExerciseSetAnalyzer.ItemAnatomyStructure(branchId, "BRANCH", "MUSCLE_GROUP"), new ExerciseSetAnalyzer.ItemAnatomyStructure(parentId, "PARENT", "MUSCLE_GROUP"))), null);
        var result = analyzer.analyzeAnatomy(version(SetProfile.HOME, item), true,
                Map.of(itemId, snapshot(exerciseVersionId, List.of(parent, child))));

        assertThat(result.aggregatedStructureExposures()).filteredOn(exposure -> exposure.anatomicalStructureId().equals(parentId))
                .singleElement().satisfies(exposure -> {
                    assertThat(exposure.coefficientLow()).isEqualByComparingTo("0.2");
                    assertThat(exposure.coefficientHigh()).isEqualByComparingTo("0.7");
                    assertThat(exposure.breakdowns()).hasSize(2);
                    assertThat(exposure.breakdowns()).filteredOn(breakdown -> breakdown.contributionId().equals(parent.id()))
                            .singleElement().extracting(AnatomyAggregationBreakdown::reason).isEqualTo("EXCLUDED_MORE_SPECIFIC_SOURCE");
                });
    }

    @Test
    void ordersAnatomyOutputDeterministicallyAndPreservesLateralityRules() {
        var first = item(1, Phase.MAIN, strength(), "[]");
        var second = item(2, Phase.MAIN, strength(), "[]"); second.dose.side = Side.LEFT;
        var third = item(3, Phase.MAIN, strength(), "[]");
        var fourth = item(4, Phase.MAIN, strength(), "[]"); fourth.dose = null;
        var snapshots = Map.of(
                first.id, snapshot(first.exerciseVersionId, List.of(contribution(UUID.randomUUID(), UUID.fromString("00000000-0000-0000-0000-000000000004"), "RIGHT", "RIGHT", List.of(), null))),
                second.id, snapshot(second.exerciseVersionId, List.of(contribution(UUID.randomUUID(), UUID.fromString("00000000-0000-0000-0000-000000000003"), "LEFT", "SAME_AS_EXECUTION", List.of(), null))),
                third.id, snapshot(third.exerciseVersionId, List.of(contribution(UUID.randomUUID(), UUID.fromString("00000000-0000-0000-0000-000000000002"), "BILATERAL", "BILATERAL", List.of(), null))),
                fourth.id, snapshot(fourth.exerciseVersionId, List.of(contribution(UUID.randomUUID(), UUID.fromString("00000000-0000-0000-0000-000000000001"), "UNSPECIFIED", "SAME_AS_EXECUTION", List.of(), null))));
        var version = version(SetProfile.HOME, first, second, third, fourth);
        var firstResult = analyzer.analyzeAnatomy(version, true, snapshots);
        var secondResult = analyzer.analyzeAnatomy(version, true, snapshots);

        assertThat(firstResult).isEqualTo(secondResult);
        assertThat(firstResult.directStructureExposures()).extracting(AnatomyStructureExposure::anatomicalStructureCode)
                .containsExactly("UNSPECIFIED", "BILATERAL", "LEFT", "RIGHT");
        assertThat(firstResult.directStructureExposures()).extracting(AnatomyStructureExposure::laterality)
                .containsExactly("UNSPECIFIED", "BILATERAL", "LEFT", "RIGHT");
        assertThat(firstResult.findings()).extracting(AnatomyFinding::code).containsExactly("ANATOMY_LATERALITY_CONFLICT");
    }

    @Test
    void reportsCompletePartialAndUnavailableVisualMappingCoverage() {
        var item = item(1, Phase.MAIN, strength(), "[]");
        var mapped = contribution(UUID.randomUUID(), UUID.randomUUID(), "MAPPED", "BILATERAL", List.of(), mapping());
        var unmapped = contribution(UUID.randomUUID(), UUID.randomUUID(), "UNMAPPED", "BILATERAL", List.of(), null);

        assertThat(analyzer.analyzeAnatomy(version(SetProfile.HOME, item), true, Map.of(item.id, snapshot(item.exerciseVersionId, List.of(mapped)))).mappingCompleteness())
                .isEqualTo(AnatomyMappingCompleteness.COMPLETE);
        assertThat(analyzer.analyzeAnatomy(version(SetProfile.HOME, item), true, Map.of(item.id, snapshot(item.exerciseVersionId, List.of(mapped, unmapped)))).mappingCompleteness())
                .isEqualTo(AnatomyMappingCompleteness.PARTIAL);
        assertThat(analyzer.analyzeAnatomy(version(SetProfile.HOME, item), true, Map.of(item.id, snapshot(item.exerciseVersionId, List.of()))).mappingCompleteness())
                .isEqualTo(AnatomyMappingCompleteness.UNAVAILABLE);
    }

    @Test
    void projectsOnlyUniqueDirectContributionsIntoVersionedVisualRegions() {
        UUID itemId = UUID.fromString("00000000-0000-0000-0000-000000000101");
        UUID exerciseVersionId = UUID.fromString("00000000-0000-0000-0000-000000000102");
        var item = item(1, Phase.MAIN, strength(), "[]"); item.id = itemId; item.exerciseVersionId = exerciseVersionId;
        var first = contribution(UUID.fromString("00000000-0000-0000-0000-000000000103"), UUID.randomUUID(), "STRUCTURE:ONE", "LEFT", List.of(), mapping());
        var second = contribution(UUID.fromString("00000000-0000-0000-0000-000000000104"), UUID.randomUUID(), "STRUCTURE:TWO", "LEFT", List.of(path(new ExerciseSetAnalyzer.ItemAnatomyStructure(UUID.randomUUID(), "PARENT", "MUSCLE_GROUP"))), mapping());
        var result = analyzer.analyzeAnatomy(version(SetProfile.HOME, item), true,
                Map.of(itemId, snapshot(exerciseVersionId, List.of(first, second, first))));

        assertThat(result.visualMappingVersion()).isEqualTo("1");
        assertThat(result.visualConcentrationPolicyVersion()).isEqualTo("visual-region-concentration-policy-v1");
        assertThat(result.visualRegionExposures()).singleElement().satisfies(exposure -> {
            assertThat(exposure.visualRegionCode()).isEqualTo("REGION");
            assertThat(exposure.displayName()).isEqualTo("Udo");
            assertThat(exposure.view()).isEqualTo(VisualRegionView.FRONT);
            assertThat(exposure.layer()).isEqualTo(VisualRegionLayer.MUSCLE);
            assertThat(exposure.laterality()).isEqualTo(VisualRegionLaterality.LEFT);
            assertThat(exposure.channel()).isEqualTo(VisualRegionChannel.DYN_EXU);
            assertThat(exposure.mappingVersion()).isEqualTo(1L);
            assertThat(exposure.rawValue()).isEqualByComparingTo("1.4");
            assertThat(exposure.shareWithinChannel()).isEqualByComparingTo("1");
            assertThat(exposure.concentrationBand()).isEqualTo(ConcentrationBand.DOMINANT);
            assertThat(exposure.sourceStructures()).hasSize(2);
            assertThat(exposure.breakdowns()).hasSize(2);
        });
    }

    @Test
    void preservesBilateralVisualExposureWithoutDuplicatingItsContribution() {
        var item = item(1, Phase.MAIN, strength(), "[]");
        item.dose.side = Side.BILATERAL;
        var bilateral = contribution(UUID.randomUUID(), UUID.randomUUID(), "STRUCTURE:BILATERAL", "BILATERAL", List.of(), mapping());

        var result = analyzer.analyzeAnatomy(version(SetProfile.HOME, item), true,
                Map.of(item.id, snapshot(item.exerciseVersionId, List.of(bilateral))));

        assertThat(result.visualRegionExposures()).singleElement().satisfies(exposure -> {
            assertThat(exposure.laterality()).isEqualTo(VisualRegionLaterality.BILATERAL);
            assertThat(exposure.rawValue()).isEqualByComparingTo("0.7");
            assertThat(exposure.shareWithinChannel()).isEqualByComparingTo("1");
            assertThat(exposure.concentrationBand()).isEqualTo(ConcentrationBand.DOMINANT);
            assertThat(exposure.breakdowns()).singleElement();
        });
    }

    @Test
    void reportsMixedTopLevelVersionAndKeepsEveryVisualExposureVersionExplicit() {
        var item = item(1, Phase.MAIN, strength(), "[]");
        var v1 = contribution(UUID.randomUUID(), UUID.randomUUID(), "ONE", "LEFT", List.of(), mapping(1, "REGION:ONE"));
        var v2 = contribution(UUID.randomUUID(), UUID.randomUUID(), "TWO", "RIGHT", List.of(), mapping(2, "REGION:TWO"));

        var result = analyzer.analyzeAnatomy(version(SetProfile.HOME, item), true,
                Map.of(item.id, snapshot(item.exerciseVersionId, List.of(v1, v2))));

        assertThat(result.visualMappingVersion()).isEqualTo("MIXED");
        assertThat(result.visualRegionExposures()).extracting(VisualRegionExposure::mappingVersion).containsExactly(1L, 2L);
        assertThat(result.visualRegionExposures()).extracting(VisualRegionExposure::laterality)
                .containsExactly(VisualRegionLaterality.LEFT, VisualRegionLaterality.RIGHT);
    }

    private static ExerciseSetAnalyzer.ItemAnatomySnapshot snapshot(UUID exerciseVersionId, List<ExerciseSetAnalyzer.ItemAnatomyContribution> contributions) {
        return new ExerciseSetAnalyzer.ItemAnatomySnapshot(exerciseVersionId, 1, 1, List.of("PUSH"), contributions);
    }
    private static ExerciseSetAnalyzer.ItemAnatomyContribution contribution(UUID id, UUID structureId, String code, String sideRule,
                                                                              List<ExerciseSetAnalyzer.ItemAnatomyPath> paths, ExerciseSetAnalyzer.ItemVisualMapping mapping) {
        return new ExerciseSetAnalyzer.ItemAnatomyContribution(id, structureId, code, "MUSCLE_GROUP", "PRIMARY", "DYN_EXU",
                new BigDecimal("0.2"), new BigDecimal("0.7"), "MODERATE", "EDITORIAL_REVIEW", true, null, sideRule, List.of(), paths, mapping);
    }
    private static ExerciseSetAnalyzer.ItemAnatomyPath path(ExerciseSetAnalyzer.ItemAnatomyStructure... steps) {
        return new ExerciseSetAnalyzer.ItemAnatomyPath(List.of(steps));
    }
    private static ExerciseSetAnalyzer.ItemVisualMapping mapping() {
        return mapping(1, "REGION");
    }
    private static ExerciseSetAnalyzer.ItemVisualMapping mapping(long version, String code) {
        return new ExerciseSetAnalyzer.ItemVisualMapping(version, List.of(new ExerciseSetAnalyzer.ItemVisualRegion(UUID.randomUUID(), code, "Udo", "FRONT", "MUSCLE", "region", null, 1, "ACTIVE")));
    }

    private void assertBlocked(SetProfile profile, Phase phase) { assertThat(analyzer.analyze(version(profile, item(1, phase, strength(), "[]")), true).status()).isEqualTo(AnalysisStatus.SUGGESTIONS_AVAILABLE); }
    private static ExerciseSetEntities.ExerciseSetVersionEntity version(SetProfile profile, ExerciseSetEntities.ExerciseSetItemEntity... items) { var v=new ExerciseSetEntities.ExerciseSetVersionEntity(); v.id=UUID.randomUUID();v.profile=profile;v.title="Routine";v.version=7; for(var item:items){item.version=v;v.items.add(item);}return v; }
    private static ExerciseSetEntities.ExerciseSetItemEntity item(int position, Phase phase, ExerciseSetEntities.DoseEntity dose, String equipment) { var i=new ExerciseSetEntities.ExerciseSetItemEntity();i.id=UUID.randomUUID();i.position=position;i.phase=phase;i.exerciseVersionId=UUID.randomUUID();i.requiredEquipment=equipment;i.dose=dose;dose.item=i;return i; }
    private static ExerciseSetEntities.StrengthDoseEntity strength() { var d=new ExerciseSetEntities.StrengthDoseEntity();d.sets=3;d.reps=8;d.restSeconds=60;return d; }
    private static ExerciseSetEntities.IsometricDoseEntity isometric() { var d=new ExerciseSetEntities.IsometricDoseEntity();d.sets=2;d.holdSeconds=20;return d; }
    private static ExerciseSetEntities.MobilityDoseEntity mobility() { var d=new ExerciseSetEntities.MobilityDoseEntity();d.reps=8;return d; }
    private static ExerciseSetEntities.StretchDoseEntity stretch() { var d=new ExerciseSetEntities.StretchDoseEntity();d.holdSeconds=30;d.repetitions=2;return d; }
    private static ExerciseSetEntities.BreathingDoseEntity breathing() { var d=new ExerciseSetEntities.BreathingDoseEntity();d.cycles=5;return d; }
    private static ExerciseSetEntities.AerobicDoseEntity aerobic() { var d=new ExerciseSetEntities.AerobicDoseEntity();d.durationSeconds=300;return d; }
}
