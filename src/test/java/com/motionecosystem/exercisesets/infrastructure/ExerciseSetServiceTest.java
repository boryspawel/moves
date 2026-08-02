package com.motionecosystem.exercisesets.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verifyNoInteractions;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;
import com.motionecosystem.exercisecatalog.api.ExerciseCatalogQueryPort;
import com.motionecosystem.anatomyreference.api.AnatomyReferenceQueryPort;
import com.motionecosystem.exercisesets.api.ExerciseSetDtos.AerobicDose;
import com.motionecosystem.exercisesets.api.ExerciseSetDtos.AnatomyCompleteness;
import com.motionecosystem.exercisesets.api.ExerciseSetDtos.AnatomyAggregatedExposure;
import com.motionecosystem.exercisesets.api.ExerciseSetDtos.AnatomyAnalysisView;
import com.motionecosystem.exercisesets.api.ExerciseSetDtos.AnatomyMappingCompleteness;
import com.motionecosystem.exercisesets.api.ExerciseSetDtos.AnalysisStatus;
import com.motionecosystem.exercisesets.api.ExerciseSetDtos.BreathingDose;
import com.motionecosystem.exercisesets.api.ExerciseSetDtos.Dose;
import com.motionecosystem.exercisesets.api.ExerciseSetDtos.IsometricDose;
import com.motionecosystem.exercisesets.api.ExerciseSetDtos.ItemRequest;
import com.motionecosystem.exercisesets.api.ExerciseSetDtos.MetadataRequest;
import com.motionecosystem.exercisesets.api.ExerciseSetDtos.MobilityDose;
import com.motionecosystem.exercisesets.api.ExerciseSetDtos.MoveRequest;
import com.motionecosystem.exercisesets.api.ExerciseSetDtos.StretchDose;
import com.motionecosystem.exercisesets.api.ExerciseSetDtos.StrengthDose;
import com.motionecosystem.exercisesets.domain.ExerciseSetModel.Phase;
import com.motionecosystem.exercisesets.domain.ExerciseSetModel.SetProfile;
import com.motionecosystem.exercisesets.domain.ExerciseSetModel.Side;
import com.motionecosystem.exercisesets.domain.ExerciseSetModel.VariantKind;
import com.motionecosystem.exercisesets.domain.ExerciseSetModel.VersionStatus;
import com.motionecosystem.identityaccess.api.CurrentAccount;
import com.motionecosystem.identityaccess.api.CurrentAccountService;
import com.motionecosystem.identityaccess.api.ProfileType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ExerciseSetServiceTest {

    private static final String SUBJECT = "set-owner";
    private final UUID ownerId = UUID.randomUUID();
    private final UUID setId = UUID.randomUUID();
    private final UUID draftId = UUID.randomUUID();
    private ExerciseSetRepository sets;
    private ExerciseSetVersionRepository versions;
    private ExerciseSetAnalysisRunRepository analysisRuns;
    private ExerciseSetAnatomyAnalysisRunRepository anatomyAnalysisRuns;
    private ExerciseCatalogQueryPort catalog;
    private AnatomyReferenceQueryPort anatomyReference;
    private ExerciseSetService service;
    private ExerciseSetEntities.ExerciseSetEntity set;
    private ExerciseSetEntities.ExerciseSetVersionEntity draft;

    @BeforeEach
    void setUp() {
        sets = org.mockito.Mockito.mock(ExerciseSetRepository.class);
        versions = org.mockito.Mockito.mock(ExerciseSetVersionRepository.class);
        analysisRuns = org.mockito.Mockito.mock(ExerciseSetAnalysisRunRepository.class);
        anatomyAnalysisRuns = org.mockito.Mockito.mock(ExerciseSetAnatomyAnalysisRunRepository.class);
        catalog = org.mockito.Mockito.mock(ExerciseCatalogQueryPort.class);
        anatomyReference = org.mockito.Mockito.mock(AnatomyReferenceQueryPort.class);
        CurrentAccountService accounts = org.mockito.Mockito.mock(CurrentAccountService.class);
        when(accounts.requireActive(SUBJECT)).thenReturn(new CurrentAccount(ownerId, SUBJECT, ProfileType.SPECIALIST));
        service = new ExerciseSetService(sets, versions, analysisRuns, anatomyAnalysisRuns, catalog, anatomyReference, accounts,
                Clock.fixed(Instant.parse("2026-07-28T12:00:00Z"), ZoneOffset.UTC), new ObjectMapper());
        set = set();
        draft = version(draftId, VersionStatus.DRAFT, 1);
        when(sets.findById(setId)).thenReturn(Optional.of(set));
        when(versions.findWithItemsById(draftId)).thenReturn(Optional.of(draft));
        when(catalog.findPublishedVersion(any())).thenAnswer(invocation -> Optional.of(snapshot(invocation.getArgument(0))));
        when(anatomyReference.visualMappings(any())).thenReturn(Map.of());
    }

    @Test
    void createsIndependentSetWithFirstDraftAndNoParticipantOrDate() {
        when(versions.findByExerciseSetIdOrderByVersionNumberDesc(any())).thenReturn(List.of());
        var result = service.create(SUBJECT);
        assertThat(result.ownerAccountId()).isEqualTo(ownerId);
        ArgumentCaptor<ExerciseSetEntities.ExerciseSetVersionEntity> saved = ArgumentCaptor.forClass(ExerciseSetEntities.ExerciseSetVersionEntity.class);
        org.mockito.Mockito.verify(versions).save(saved.capture());
        assertThat(saved.getValue().status).isEqualTo(VersionStatus.DRAFT);
        assertThat(saved.getValue().versionNumber).isOne();
    }

    @Test
    void analyzesDraftAnatomyOnlyFromPersistedItemSnapshot() {
        draft.items.clear();
        var item = new ExerciseSetEntities.ExerciseSetItemEntity();
        item.id = UUID.randomUUID(); item.exerciseVersionId = UUID.randomUUID(); item.position = 1;
        item.anatomySnapshot = "{\"sourceExerciseVersionId\":\"%s\",\"sourceExerciseVersionNumber\":1,\"sourceProfileSchemaVersion\":1,\"movementPatterns\":[\"PUSH\"],\"contributions\":[]}".formatted(item.exerciseVersionId);
        draft.items.add(item);
        var result = service.anatomy(SUBJECT, setId, draftId);
        assertThat(result.completeness()).isEqualTo(AnatomyCompleteness.COMPLETE);
        org.mockito.Mockito.verifyNoInteractions(catalog);
    }

    @Test
    void returnsTheImmutablePublishedAnatomySnapshotWithoutCatalogLookup() throws Exception {
        draft.status = VersionStatus.PUBLISHED;
        UUID structureId = UUID.randomUUID();
        var stored = new AnatomyAnalysisView("exercise-set-anatomy-policy-v1", 1, Instant.parse("2026-07-28T12:00:00Z"), false, true,
                AnatomyCompleteness.COMPLETE, List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(new AnatomyAggregatedExposure(structureId, "QUADRICEPS", "MUSCLE_GROUP", "DYN_EXU", "LEFT",
                        new BigDecimal("0.2"), new BigDecimal("0.7"), List.of())), AnatomyMappingCompleteness.PARTIAL,
                List.of(new com.motionecosystem.exercisesets.api.ExerciseSetDtos.AnatomyUnmappedStructure(structureId, "QUADRICEPS", "MUSCLE_GROUP")));
        var run = new ExerciseSetEntities.ExerciseSetAnatomyAnalysisRunEntity();
        run.result = new ObjectMapper().writeValueAsString(stored);
        when(anatomyAnalysisRuns.findByVersionId(draftId)).thenReturn(Optional.of(run));

        var result = service.anatomy(SUBJECT, setId, draftId);

        assertThat(result.aggregatedStructureExposures()).singleElement().satisfies(exposure -> {
            assertThat(exposure.anatomicalStructureCode()).isEqualTo("QUADRICEPS");
            assertThat(exposure.laterality()).isEqualTo("LEFT");
        });
        assertThat(result.mappingCompleteness()).isEqualTo(AnatomyMappingCompleteness.PARTIAL);
        assertThat(result.unmappedStructures()).singleElement().extracting(unmapped -> unmapped.anatomicalStructureCode()).isEqualTo("QUADRICEPS");
        verifyNoInteractions(catalog, anatomyReference);
    }

    @Test
    void returnsPersistedVisualProjectionAfterLiveMappingCouldHaveChanged() throws Exception {
        draft.status = VersionStatus.PUBLISHED;
        var exposure = new com.motionecosystem.exercisesets.api.ExerciseSetDtos.VisualRegionExposure(
                "ANATOMY_VISUAL_MAP_V1:FRONT:THIGH", "Udo", com.motionecosystem.exercisesets.api.ExerciseSetDtos.VisualRegionView.FRONT,
                com.motionecosystem.exercisesets.api.ExerciseSetDtos.VisualRegionLayer.MUSCLE,
                com.motionecosystem.exercisesets.api.ExerciseSetDtos.VisualRegionLaterality.LEFT,
                com.motionecosystem.exercisesets.api.ExerciseSetDtos.VisualRegionChannel.DYN_EXU, 1L,
                new BigDecimal("0.7"), "COEFFICIENT_HIGH_SUM", BigDecimal.ONE,
                com.motionecosystem.exercisesets.api.ExerciseSetDtos.ConcentrationBand.DOMINANT,
                AnatomyMappingCompleteness.COMPLETE, new BigDecimal("0.2"), new BigDecimal("0.7"), List.of(), List.of());
        var stored = new AnatomyAnalysisView("exercise-set-anatomy-policy-v1", 1, Instant.parse("2026-07-28T12:00:00Z"), false, true,
                AnatomyCompleteness.COMPLETE, List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                AnatomyMappingCompleteness.COMPLETE, List.of(), 0, "1", AnatomyMappingCompleteness.COMPLETE,
                "visual-region-concentration-policy-v1", List.of(exposure));
        var run = new ExerciseSetEntities.ExerciseSetAnatomyAnalysisRunEntity();
        run.result = new ObjectMapper().writeValueAsString(stored);
        when(anatomyAnalysisRuns.findByVersionId(draftId)).thenReturn(Optional.of(run));

        var result = service.anatomy(SUBJECT, setId, draftId);

        assertThat(result.visualMappingVersion()).isEqualTo("1");
        assertThat(result.visualRegionExposures()).singleElement().satisfies(saved -> {
            assertThat(saved.visualRegionCode()).isEqualTo("ANATOMY_VISUAL_MAP_V1:FRONT:THIGH");
            assertThat(saved.displayName()).isEqualTo("Udo");
            assertThat(saved.mappingVersion()).isEqualTo(1L);
        });
        verifyNoInteractions(catalog, anatomyReference);
    }

    @Test
    void normalizesPreSet06bPublishedJsonWithoutVisualFields() {
        draft.status = VersionStatus.PUBLISHED;
        var legacy = new AnatomyAnalysisView("exercise-set-anatomy-policy-v1", 1, Instant.parse("2026-07-28T12:00:00Z"), false, true,
                AnatomyCompleteness.COMPLETE, List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                AnatomyMappingCompleteness.PARTIAL, List.of(), 0, "1", AnatomyMappingCompleteness.PARTIAL,
                "visual-region-concentration-policy-v1", List.of());
        var json = new ObjectMapper();
        var legacyJson = (ObjectNode) json.valueToTree(legacy);
        legacyJson.remove(List.of("visualMappingVersion", "visualMappingCompleteness", "visualConcentrationPolicyVersion", "visualRegionExposures"));
        var run = new ExerciseSetEntities.ExerciseSetAnatomyAnalysisRunEntity();
        run.result = json.writeValueAsString(legacyJson);
        when(anatomyAnalysisRuns.findByVersionId(draftId)).thenReturn(Optional.of(run));

        var result = service.anatomy(SUBJECT, setId, draftId);

        assertThat(result.visualMappingVersion()).isEqualTo("UNAVAILABLE");
        assertThat(result.visualMappingCompleteness()).isEqualTo(AnatomyMappingCompleteness.UNAVAILABLE);
        assertThat(result.visualRegionExposures()).isEmpty();
        verifyNoInteractions(catalog, anatomyReference);
    }

    @Test
    void readsLegacyVisualExposureWithoutDisplayName() throws Exception {
        var legacy = new ObjectMapper().readValue("""
                {
                  "visualRegionCode":"ANATOMY_VISUAL_MAP_V1:FRONT:THIGH",
                  "view":"FRONT",
                  "layer":"MUSCLE",
                  "laterality":"LEFT",
                  "channel":"DYN_EXU",
                  "mappingVersion":1,
                  "rawValue":0,
                  "unit":"COEFFICIENT_HIGH_SUM",
                  "shareWithinChannel":0,
                  "concentrationBand":"NO_DATA",
                  "completeness":"COMPLETE",
                  "sourceStructures":[],
                  "breakdowns":[]
                }
                """, com.motionecosystem.exercisesets.api.ExerciseSetDtos.VisualRegionExposure.class);

        assertThat(legacy.visualRegionCode()).isEqualTo("ANATOMY_VISUAL_MAP_V1:FRONT:THIGH");
        assertThat(legacy.displayName()).isNull();
        assertThat(legacy.laterality()).isEqualTo(com.motionecosystem.exercisesets.api.ExerciseSetDtos.VisualRegionLaterality.LEFT);
        assertThat(legacy.sourceStructures()).isEmpty();
        assertThat(legacy.breakdowns()).isEmpty();
    }

    @Test
    void acceptsAllTypedDosesAndPreservesTheirTypes() {
        List<Dose> doses = List.of(
                new StrengthDose(3, 8, null, null, 60, "3010", null, null, new BigDecimal("7"), 2, Side.BILATERAL),
                new IsometricDose(3, 20, 30, "MODERATE", Side.LEFT),
                new MobilityDose(8, null, "COMFORTABLE", Side.RIGHT, "slow"),
                new StretchDose(30, 2, Side.LEFT, "GENTLE"),
                new BreathingDose(120, null, "4-4"),
                new AerobicDose(600, 1000, "EASY", "Z2", new BigDecimal("4")));

        for (Dose dose : doses) {
            var view = service.addItem(SUBJECT, setId, draftId, request(UUID.randomUUID(), dose));
            assertThat(view.items().getLast().dose()).isInstanceOf(dose.getClass());
        }
        assertThat(draft.items).hasSize(6).extracting(item -> item.position).containsExactly(1, 2, 3, 4, 5, 6);
    }

    @Test
    void rejectsInvalidShapeForEveryTypedDose() {
        List<Dose> invalid = List.of(
                new StrengthDose(0, null, null, null, null, null, null, null, null, null, null),
                new IsometricDose(1, 0, null, null, null),
                new MobilityDose(null, null, "", null, null),
                new StretchDose(10, 1, null, null),
                new BreathingDose(null, null, null),
                new AerobicDose(0, null, null, null, null));
        for (Dose dose : invalid) {
            assertThatThrownBy(() -> service.addItem(SUBJECT, setId, draftId, request(UUID.randomUUID(), dose)))
                    .isInstanceOf(ExerciseSetService.ExerciseSetProblem.class)
                    .extracting(error -> ((ExerciseSetService.ExerciseSetProblem) error).code)
                    .isEqualTo("INVALID_DOSE");
        }
    }

    @Test
    void movesAndRemovesDraftItemsUsingContiguousPositions() {
        service.addItem(SUBJECT, setId, draftId, request(UUID.randomUUID(), strength()));
        service.addItem(SUBJECT, setId, draftId, request(UUID.randomUUID(), strength()));
        UUID first = draft.items.getFirst().id;
        UUID second = draft.items.getLast().id;

        var moved = service.moveItem(SUBJECT, setId, draftId, new MoveRequest(second, 1, 0));
        assertThat(moved.items()).extracting(item -> item.id()).containsExactly(second, first);
        var remaining = service.removeItem(SUBJECT, setId, draftId, second, 0);
        assertThat(remaining.items()).singleElement().satisfies(item -> assertThat(item.position()).isOne());
    }

    @Test
    void publicationAllowsMissingMetadataAndItemsAndFreezesPublishedDraft() {
        assertThat(service.publish(SUBJECT, setId, draftId, 0).status()).isEqualTo(VersionStatus.PUBLISHED);
        assertProblem(() -> service.addItem(SUBJECT, setId, draftId, request(UUID.randomUUID(), strength())), "VERSION_NOT_EDITABLE");
    }

    @Test
    void readsLegacyBlockedAnalysisAsSuggestionsWithoutChangingTheSnapshot() {
        draft.status = VersionStatus.PUBLISHED;
        var legacy = new ExerciseSetEntities.ExerciseSetAnalysisRunEntity();
        legacy.status = "BLOCKED";
        legacy.policyVersion = "exercise-set-policy-v1";
        legacy.analyzedAt = Instant.parse("2026-07-28T12:00:00Z");
        legacy.analyzedLockVersion = 0;
        legacy.timeConfidence = com.motionecosystem.exercisesets.api.ExerciseSetDtos.TimeConfidence.UNAVAILABLE;
        when(analysisRuns.findByVersionId(draftId)).thenReturn(Optional.of(legacy));

        assertThat(service.analysis(SUBJECT, setId, draftId).status()).isEqualTo(AnalysisStatus.SUGGESTIONS_AVAILABLE);
        assertThat(legacy.status).isEqualTo("BLOCKED");
    }

    @Test
    void publicationPersistsAnatomyAggregateAndMappingCompletenessFields() {
        readyPublishedDraft();
        assertThat(draft.anatomyAnalysisRun.result)
                .contains("aggregatedStructureExposures", "mappingCompleteness", "unmappedStructures");
    }

    @Test
    void nextDraftIsAFullSnapshotAndRetiredVersionRemainsReadable() {
        readyPublishedDraft();
        when(versions.countByExerciseSetId(setId)).thenReturn(1L);
        ArgumentCaptor<ExerciseSetEntities.ExerciseSetVersionEntity> saved = ArgumentCaptor.forClass(ExerciseSetEntities.ExerciseSetVersionEntity.class);
        var copied = service.nextDraft(SUBJECT, setId, draftId);
        org.mockito.Mockito.verify(versions).save(saved.capture());
        assertThat(copied.versionNumber()).isEqualTo(2);
        assertThat(copied.items()).hasSize(1);
        assertThat(saved.getValue().items.getFirst().id).isNotEqualTo(draft.items.getFirst().id);

        assertThat(service.retire(SUBJECT, setId, draftId).status()).isEqualTo(VersionStatus.RETIRED);
        assertThat(service.version(SUBJECT, setId, draftId).status()).isEqualTo(VersionStatus.RETIRED);
    }

    @Test
    void variantDraftRequiresShortOrMinimumAndCopiesPublishedBase() {
        readyPublishedDraft();
        when(versions.countByExerciseSetId(setId)).thenReturn(1L);
        assertProblem(() -> service.variantDraft(SUBJECT, setId, draftId,
                new com.motionecosystem.exercisesets.api.ExerciseSetDtos.CreateVariantRequest(VariantKind.BASE)), "INVALID_VARIANT");

        ArgumentCaptor<ExerciseSetEntities.ExerciseSetVersionEntity> saved = ArgumentCaptor.forClass(ExerciseSetEntities.ExerciseSetVersionEntity.class);
        var shortDraft = service.variantDraft(SUBJECT, setId, draftId,
                new com.motionecosystem.exercisesets.api.ExerciseSetDtos.CreateVariantRequest(VariantKind.SHORT));
        org.mockito.Mockito.verify(versions).save(saved.capture());
        assertThat(shortDraft.variantKind()).isEqualTo(VariantKind.SHORT);
        assertThat(shortDraft.variantOfVersionId()).isEqualTo(draftId);
        assertThat(saved.getValue().items).hasSize(1);
    }

    @Test
    void refusesToReanalyzeHistoricalVersionWithoutStoredSnapshot() {
        draft.status = VersionStatus.RETIRED;
        assertProblem(() -> service.analysis(SUBJECT, setId, draftId), "ANALYSIS_NOT_AVAILABLE");
    }

    private void readyPublishedDraft() {
        service.updateMetadata(SUBJECT, setId, draftId, new MetadataRequest(SetProfile.HOME, "Home routine", null, null, List.of(), 0));
        service.addItem(SUBJECT, setId, draftId, request(UUID.randomUUID(), strength()));
        service.publish(SUBJECT, setId, draftId, 0);
    }

    private static ItemRequest request(UUID exerciseVersionId, Dose dose) {
        return new ItemRequest(exerciseVersionId, Phase.MAIN, dose, null, null, 0);
    }

    private static StrengthDose strength() {
        return new StrengthDose(3, 8, null, null, 60, null, null, null, null, null, Side.BILATERAL);
    }

    private ExerciseSetEntities.ExerciseSetEntity set() {
        var result = new ExerciseSetEntities.ExerciseSetEntity();
        result.id = setId;
        result.ownerAccountId = ownerId;
        result.createdAt = Instant.parse("2026-07-28T12:00:00Z");
        result.updatedAt = result.createdAt;
        return result;
    }

    private ExerciseSetEntities.ExerciseSetVersionEntity version(UUID id, VersionStatus status, int number) {
        var result = new ExerciseSetEntities.ExerciseSetVersionEntity();
        result.id = id;
        result.exerciseSetId = setId;
        result.versionNumber = number;
        result.status = status;
        result.authorAccountId = ownerId;
        result.createdAt = Instant.parse("2026-07-28T12:00:00Z");
        result.updatedAt = result.createdAt;
        result.variantKind = VariantKind.BASE;
        return result;
    }

    private static ExerciseCatalogQueryPort.PublishedExerciseVersionSnapshot snapshot(UUID versionId) {
        return new ExerciseCatalogQueryPort.PublishedExerciseVersionSnapshot(UUID.randomUUID(), "Squat", versionId, 1, 1,
                Set.of(ExerciseCatalogQueryPort.MovementPatternValue.SQUAT), Set.of("band"), List.of(), List.of());
    }

    private static void assertProblem(org.assertj.core.api.ThrowableAssert.ThrowingCallable callable, String code) {
        assertThatThrownBy(callable).isInstanceOf(ExerciseSetService.ExerciseSetProblem.class)
                .extracting(error -> ((ExerciseSetService.ExerciseSetProblem) error).code).isEqualTo(code);
    }
}
