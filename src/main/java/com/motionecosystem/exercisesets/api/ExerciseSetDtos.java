package com.motionecosystem.exercisesets.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.motionecosystem.exercisesets.domain.ExerciseSetModel.*;
import io.swagger.v3.oas.annotations.media.DiscriminatorMapping;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public final class ExerciseSetDtos {
    private ExerciseSetDtos() { }
    public record CreateRequest() { }
    public record MetadataRequest(SetProfile profile, String title, String description, String targetLevel, List<String> tags, long expectedVersion) { }
    public record ItemRequest(@NotNull UUID exerciseVersionId, @NotNull Phase phase, @NotNull @Valid Dose dose, String participantInstruction, String specialistInstruction, long expectedVersion) { }
    public record MoveRequest(@NotNull UUID itemId, int targetPosition, long expectedVersion) { }
    public record PublishRequest(@NotNull @PositiveOrZero Long expectedVersion) { }
    public record CreateVariantRequest(@NotNull VariantKind variantKind) { }
    @Schema(
            discriminatorProperty = "type",
            discriminatorMapping = {
                    @DiscriminatorMapping(value = "STRENGTH", schema = StrengthDose.class),
                    @DiscriminatorMapping(value = "ISOMETRIC", schema = IsometricDose.class),
                    @DiscriminatorMapping(value = "MOBILITY", schema = MobilityDose.class),
                    @DiscriminatorMapping(value = "STRETCH", schema = StretchDose.class),
                    @DiscriminatorMapping(value = "BREATHING", schema = BreathingDose.class),
                    @DiscriminatorMapping(value = "AEROBIC", schema = AerobicDose.class)
            },
            oneOf = { StrengthDose.class, IsometricDose.class, MobilityDose.class, StretchDose.class, BreathingDose.class, AerobicDose.class })
    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
    @JsonSubTypes({@JsonSubTypes.Type(value=StrengthDose.class,name="STRENGTH"), @JsonSubTypes.Type(value=IsometricDose.class,name="ISOMETRIC"), @JsonSubTypes.Type(value=MobilityDose.class,name="MOBILITY"), @JsonSubTypes.Type(value=StretchDose.class,name="STRETCH"), @JsonSubTypes.Type(value=BreathingDose.class,name="BREATHING"), @JsonSubTypes.Type(value=AerobicDose.class,name="AEROBIC")})
    public sealed interface Dose permits StrengthDose, IsometricDose, MobilityDose, StretchDose, BreathingDose, AerobicDose { String type(); }
    public record StrengthDose(Integer sets, Integer reps, Integer repMin, Integer repMax, Integer restSeconds, String tempo, BigDecimal loadValue, String loadUnit, BigDecimal rpe, Integer rir, Side side) implements Dose { public String type(){return "STRENGTH";} }
    public record IsometricDose(Integer sets, Integer holdSeconds, Integer restSeconds, String intensity, Side side) implements Dose { public String type(){return "ISOMETRIC";} }
    public record MobilityDose(Integer reps, Integer durationSeconds, String rangeTarget, Side side, String tempo) implements Dose { public String type(){return "MOBILITY";} }
    public record StretchDose(Integer holdSeconds, Integer repetitions, Side side, String intensity) implements Dose { public String type(){return "STRETCH";} }
    public record BreathingDose(Integer durationSeconds, Integer cycles, String rhythm) implements Dose { public String type(){return "BREATHING";} }
    public record AerobicDose(Integer durationSeconds, Integer distanceMeters, String intensity, String zone, BigDecimal rpe) implements Dose { public String type(){return "AEROBIC";} }
    public record SetView(UUID id, Visibility visibility, UUID ownerAccountId, Instant createdAt, List<VersionSummary> versions) { }
    public record VersionSummary(UUID id, int versionNumber, VersionStatus status, String title, SetProfile profile, VariantKind variantKind, UUID variantOfVersionId) { }
    public record VersionView(UUID id, UUID exerciseSetId, int versionNumber, VersionStatus status, SetProfile profile, String title, String description, String targetLevel, List<String> tags, VariantKind variantKind, UUID variantOfVersionId, Instant createdAt, Instant publishedAt, Instant retiredAt, long lockVersion, List<ItemView> items, AnalysisView analysis) { }
    public record ItemView(UUID id, UUID exerciseVersionId, Phase phase, int position, ExerciseSnapshot snapshot, Dose dose, String participantInstruction, String specialistInstruction) { }
    public record ExerciseSnapshot(String canonicalName, int versionNumber, int profileSchemaVersion, List<String> movementPatterns, List<String> requiredEquipment) { }
    public record AnalysisView(AnalysisStatus status, String policyVersion, long analyzedLockVersion, Instant analyzedAt,
                               boolean draft, boolean published, AnalysisMetrics metrics, List<AnalysisFinding> findings) { }
    public record AnalysisMetrics(Integer itemCount, Integer estimatedSeconds, TimeConfidence timeConfidence,
                                  Integer equipmentTransitions, Integer doseKindSwitches) { }
    public record AnalysisFinding(String code, String ruleVersion, FindingSeverity severity, FindingCategory category,
                                  String messageKey, String explanation, List<UUID> itemIds, Phase phase, String field,
                                  String action, boolean blocking) { }
    /** Immutable, item-snapshot-only anatomy analysis. Dose-derived values are deliberately separated by channel. */
    public record AnatomyAnalysisView(String policyVersion, long analyzedLockVersion, Instant analyzedAt, boolean draft,
                                      boolean published, AnatomyCompleteness completeness, List<AnatomyChannel> channels,
                                      List<AnatomyStructureExposure> directStructureExposures,
                                      List<AnatomyMovementPattern> movementPatterns,
                                      List<AnatomyFinding> findings, List<AnatomyMissingData> missing,
                                      List<AnatomyAggregatedExposure> aggregatedStructureExposures,
                                      AnatomyMappingCompleteness mappingCompleteness,
                                      List<AnatomyUnmappedStructure> unmappedStructures,
                                      int unmappedCount,
                                      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String visualMappingVersion,
                                      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) AnatomyMappingCompleteness visualMappingCompleteness,
                                      String visualConcentrationPolicyVersion,
                                      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<VisualRegionExposure> visualRegionExposures) {
        /** Normalizes pre-SET-06B JSONB snapshots to the non-null public contract. */
        public AnatomyAnalysisView {
            visualMappingVersion = visualMappingVersion == null ? "UNAVAILABLE" : visualMappingVersion;
            visualMappingCompleteness = visualMappingCompleteness == null ? AnatomyMappingCompleteness.UNAVAILABLE : visualMappingCompleteness;
            visualConcentrationPolicyVersion = visualConcentrationPolicyVersion == null ? "visual-region-concentration-policy-v1" : visualConcentrationPolicyVersion;
            visualRegionExposures = visualRegionExposures == null ? List.of() : List.copyOf(visualRegionExposures);
        }
        /** Source compatibility for persisted/fixture SET-06A payloads. */
        public AnatomyAnalysisView(String policyVersion, long analyzedLockVersion, Instant analyzedAt, boolean draft,
                                   boolean published, AnatomyCompleteness completeness, List<AnatomyChannel> channels,
                                   List<AnatomyStructureExposure> directStructureExposures,
                                   List<AnatomyMovementPattern> movementPatterns, List<AnatomyFinding> findings,
                                   List<AnatomyMissingData> missing, List<AnatomyAggregatedExposure> aggregatedStructureExposures,
                                   AnatomyMappingCompleteness mappingCompleteness, List<AnatomyUnmappedStructure> unmappedStructures) {
            this(policyVersion, analyzedLockVersion, analyzedAt, draft, published, completeness, channels, directStructureExposures,
                    movementPatterns, findings, missing, aggregatedStructureExposures, mappingCompleteness, unmappedStructures, unmappedStructures.size(),
                    "UNAVAILABLE", mappingCompleteness, "visual-region-concentration-policy-v1", List.of());
        }
        public AnatomyAnalysisView(String policyVersion, long analyzedLockVersion, Instant analyzedAt, boolean draft,
                                   boolean published, AnatomyCompleteness completeness, List<AnatomyChannel> channels,
                                   List<AnatomyStructureExposure> directStructureExposures,
                                   List<AnatomyMovementPattern> movementPatterns, List<AnatomyFinding> findings,
                                   List<AnatomyMissingData> missing) {
            this(policyVersion, analyzedLockVersion, analyzedAt, draft, published, completeness, channels,
                    directStructureExposures, movementPatterns, findings, missing, List.of(), AnatomyMappingCompleteness.UNAVAILABLE, List.of(), 0,
                    "UNAVAILABLE", AnatomyMappingCompleteness.UNAVAILABLE, "visual-region-concentration-policy-v1", List.of());
        }
    }
    public record AnatomyChannel(String loadChannel, List<AnatomyStructureExposure> structureExposures) { }
    public record AnatomyStructureExposure(UUID anatomicalStructureId, String anatomicalStructureCode, String anatomicalStructureType, String loadChannel, String laterality,
                                           java.math.BigDecimal coefficientLow, java.math.BigDecimal coefficientHigh,
                                           List<AnatomyContributionBreakdown> breakdowns) { }
    public record AnatomyContributionBreakdown(UUID itemId, UUID exerciseVersionId, UUID contributionId, String role,
                                               java.math.BigDecimal coefficientLow, java.math.BigDecimal coefficientHigh,
                                               String laterality, String confidenceClass, String evidenceGrade, List<AnatomyEvidence> evidence) { }
    public record AnatomyAggregatedExposure(UUID anatomicalStructureId, String anatomicalStructureCode,
                                            String anatomicalStructureType, String loadChannel, String laterality,
                                            BigDecimal coefficientLow, BigDecimal coefficientHigh,
                                            List<AnatomyAggregationBreakdown> breakdowns) { }
    public record AnatomyAggregationBreakdown(UUID itemId, UUID exerciseVersionId, UUID contributionId,
                                              boolean included, String reason, List<String> path) { }
    public record AnatomyUnmappedStructure(UUID anatomicalStructureId, String anatomicalStructureCode,
                                           String anatomicalStructureType) { }
    /** Backend projection consumed by the partial SVG; rawValue is summed coefficientHigh of unique direct contributions. */
    public record VisualRegionExposure(@Schema(requiredMode = Schema.RequiredMode.REQUIRED) String visualRegionCode,
                                       @Schema(requiredMode = Schema.RequiredMode.REQUIRED) VisualRegionView view,
                                       @Schema(requiredMode = Schema.RequiredMode.REQUIRED) VisualRegionLayer layer,
                                       @Schema(requiredMode = Schema.RequiredMode.REQUIRED) VisualRegionLaterality laterality,
                                       @Schema(requiredMode = Schema.RequiredMode.REQUIRED) VisualRegionChannel channel,
                                       @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long mappingVersion,
                                       @Schema(requiredMode = Schema.RequiredMode.REQUIRED) BigDecimal rawValue,
                                       @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String unit,
                                       @Schema(requiredMode = Schema.RequiredMode.REQUIRED) BigDecimal shareWithinChannel,
                                       @Schema(requiredMode = Schema.RequiredMode.REQUIRED) ConcentrationBand concentrationBand,
                                       @Schema(requiredMode = Schema.RequiredMode.REQUIRED) AnatomyMappingCompleteness completeness,
                                       BigDecimal coefficientLow, BigDecimal coefficientHigh,
                                       @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<VisualRegionStructureReference> sourceStructures,
                                       @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<VisualRegionBreakdown> breakdowns) {
        public VisualRegionExposure {
            sourceStructures = sourceStructures == null ? List.of() : List.copyOf(sourceStructures);
            breakdowns = breakdowns == null ? List.of() : List.copyOf(breakdowns);
        }
    }
    public record VisualRegionStructureReference(UUID anatomicalStructureId, String anatomicalStructureCode,
                                                 String anatomicalStructureType) { }
    public record VisualRegionBreakdown(UUID itemId, UUID exerciseVersionId, UUID contributionId,
                                        UUID anatomicalStructureId, String anatomicalStructureCode,
                                        BigDecimal rawValue, BigDecimal coefficientLow, BigDecimal coefficientHigh,
                                        String role, List<AnatomyEvidence> evidence) { }
    public record AnatomyMovementPattern(String pattern, List<UUID> itemIds) { }
    public record AnatomyEvidence(UUID id, String citation, String sourceUri, String evidenceGrade) { }
    public record AnatomyFinding(String code, String message, List<UUID> itemIds) { }
    public record AnatomyMissingData(UUID itemId, UUID exerciseVersionId, String code) { }
    public enum AnatomyCompleteness { COMPLETE, PARTIAL, UNAVAILABLE }
    public enum AnatomyMappingCompleteness { COMPLETE, PARTIAL, UNAVAILABLE }
    public enum VisualRegionView { FRONT, BACK }
    public enum VisualRegionLayer { BASE, MUSCLE }
    public enum VisualRegionLaterality { LEFT, RIGHT, CENTRAL }
    public enum VisualRegionChannel { DYN_EXU, ISO_SEC, IMPACT_CONTACTS, ENDURANCE_MIN_ZONE }
    public enum ConcentrationBand { NO_DATA, LOW, SIGNIFICANT, DOMINANT }
    public enum AnalysisStatus { NO_SUGGESTIONS, SUGGESTIONS_AVAILABLE, ANALYSIS_UNAVAILABLE }
    public enum TimeConfidence { COMPLETE, PARTIAL, UNAVAILABLE }
    public enum FindingSeverity { SUGGESTION, WARNING, BLOCKING }
    public enum FindingCategory { STRUCTURE, TIME, EQUIPMENT, DUPLICATE, DATA_LIMITATION }
}
