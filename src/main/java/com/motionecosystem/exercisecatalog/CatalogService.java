package com.motionecosystem.exercisecatalog;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.motionecosystem.anatomyreference.api.AnatomyReferenceQueryPort;
import com.motionecosystem.anatomyreference.api.AnatomyReferenceQueryPort.StructureStatus;
import com.motionecosystem.audit.AuditRecorder;
import com.motionecosystem.exercisecatalog.api.ExerciseCatalogQueryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class CatalogService implements ExerciseCatalogQueryPort {

    private final ExerciseRepository exercises;
    private final ExerciseVersionRepository versions;
    private final ExerciseContributionRepository contributions;
    private final ExerciseLoadCharacteristicRepository loadCharacteristics;
    private final EvidenceSourceRepository evidenceSources;
    private final ExerciseContributionEvidenceRepository contributionEvidence;
    private final AnatomyReferenceQueryPort anatomy;
    private final AuditRecorder audit;
    private final Clock clock;

    @Transactional
    public VersionView create(String actorSubject, String canonicalName, VersionCommand requested) {
        String name = requiredText(canonicalName, 160, "canonical name");
        VersionCommand command = validate(requested);
        Instant now = clock.instant();
        Exercise exercise = exercises.save(new Exercise(name, actorSubject, now));
        ExerciseVersion version = versions.save(new ExerciseVersion(exercise.id, 1, command, now));
        audit.record(actorSubject, "EXERCISE_CREATED", "Exercise", exercise.id);
        return view(exercise, version);
    }

    @Transactional
    public VersionView createNextVersion(String actorSubject, UUID exerciseId, VersionCommand requested) {
        Exercise exercise = exercises.findLockedById(exerciseId)
                .orElseThrow(() -> notFound("exercise not found"));
        int number = versions.findFirstByExerciseIdOrderByVersionNumberDesc(exerciseId)
                .map(item -> item.versionNumber + 1)
                .orElse(1);
        try {
            ExerciseVersion version = versions.saveAndFlush(
                    new ExerciseVersion(exerciseId, number, validate(requested), clock.instant()));
            audit.record(actorSubject, "EXERCISE_VERSION_CREATED", "ExerciseVersion", version.id);
            return view(exercise, version);
        } catch (DataIntegrityViolationException | ObjectOptimisticLockingFailureException conflict) {
            throw conflict("another exercise version was created concurrently", conflict);
        }
    }

    @Transactional
    public VersionView updateDraft(String actorSubject, UUID versionId, VersionCommand requested) {
        ExerciseVersion version = lockedVersion(versionId);
        try {
            version.update(validate(requested));
        } catch (IllegalStateException immutable) {
            throw conflict(immutable.getMessage(), immutable);
        }
        audit.record(actorSubject, "EXERCISE_DRAFT_UPDATED", "ExerciseVersion", version.id);
        return view(exercise(version.exerciseId), version);
    }

    @Transactional
    public EditorView replaceLoadCharacteristics(String actorSubject, UUID versionId,
                                                 Collection<LoadCharacteristicCommand> requested) {
        ExerciseVersion version = editableVersion(versionId);
        List<LoadCharacteristicCommand> commands = validateCharacteristics(requested);
        loadCharacteristics.deleteAll(loadCharacteristics.findByExerciseVersionIdOrderById(versionId));
        loadCharacteristics.flush();
        Instant now = clock.instant();
        commands.stream().map(command -> new ExerciseLoadCharacteristic(versionId,
                        command.movementPlane(), command.contractionType(), command.rangeOfMotion(),
                        command.characteristicType(), actorSubject, now))
                .forEach(loadCharacteristics::save);
        audit.record(actorSubject, "EXERCISE_LOAD_PROFILE_UPDATED", "ExerciseVersion", version.id);
        return editorView(exercise(version.exerciseId), version);
    }

    @Transactional
    public EvidenceView addEvidence(String actorSubject, UUID versionId, EvidenceCommand requested) {
        editableVersion(versionId);
        if (requested == null) {
            throw badRequest("evidence is required");
        }
        EvidenceSource evidence = evidenceSources.save(new EvidenceSource(versionId,
                requiredText(requested.citation(), 500, "citation"),
                optionalText(requested.sourceUri(), 1_000, "source URI"),
                normalizedCode(requested.evidenceGrade(), 80, "evidence grade"),
                actorSubject, clock.instant()));
        audit.record(actorSubject, "EXERCISE_EVIDENCE_ADDED", "EvidenceSource", evidence.id);
        return evidenceView(evidence);
    }

    @Transactional
    public ContributionView addContribution(String actorSubject, UUID versionId,
                                            ContributionCommand requested) {
        editableVersion(versionId);
        ValidatedContribution command = validateContribution(versionId, requested);
        List<ExerciseContribution> candidate = new ArrayList<>(
                contributions.findByExerciseVersionIdOrderById(versionId));
        ExerciseContribution contribution = command.entity(actorSubject, clock.instant());
        candidate.add(contribution);
        validateAllocationBranches(candidate);
        contributions.save(contribution);
        command.evidenceIds().stream()
                .map(evidenceId -> new ExerciseContributionEvidence(contribution.id, evidenceId))
                .forEach(contributionEvidence::save);
        audit.record(actorSubject, "EXERCISE_CONTRIBUTION_ADDED", "ExerciseContribution", contribution.id);
        Map<UUID, EvidenceSource> evidence = evidenceSources.findByExerciseVersionIdAndIdIn(
                        versionId, command.evidenceIds()).stream()
                .collect(Collectors.toMap(item -> item.id, Function.identity()));
        return contributionView(contribution, command.evidenceIds().stream()
                .map(evidence::get).map(CatalogService::evidenceView).toList());
    }

    @Transactional
    public VersionView submitForReview(String actorSubject, UUID versionId) {
        ExerciseVersion version = lockedVersion(versionId);
        try {
            validateCompleteProfile(version);
            version.submitForReview();
        } catch (IllegalStateException invalid) {
            throw conflict(invalid.getMessage(), invalid);
        }
        audit.record(actorSubject, "EXERCISE_VERSION_SUBMITTED_FOR_REVIEW", "ExerciseVersion", version.id);
        return view(exercise(version.exerciseId), version);
    }

    @Transactional
    public VersionView requestChanges(String actorSubject, UUID versionId) {
        ExerciseVersion version = lockedVersion(versionId);
        try {
            version.requestChanges();
        } catch (IllegalStateException invalid) {
            throw conflict(invalid.getMessage(), invalid);
        }
        audit.record(actorSubject, "EXERCISE_VERSION_CHANGES_REQUESTED", "ExerciseVersion", version.id);
        return view(exercise(version.exerciseId), version);
    }

    @Transactional
    public VersionView approve(String actorSubject, UUID versionId) {
        ExerciseVersion version = lockedVersion(versionId);
        try {
            validateCompleteProfile(version);
            version.approve(actorSubject, clock.instant());
        } catch (IllegalStateException invalid) {
            throw conflict(invalid.getMessage(), invalid);
        }
        audit.record(actorSubject, "EXERCISE_VERSION_APPROVED", "ExerciseVersion", version.id);
        return view(exercise(version.exerciseId), version);
    }

    @Transactional
    public VersionView publish(String actorSubject, UUID versionId) {
        ExerciseVersion version = lockedVersion(versionId);
        try {
            validateCompleteProfile(version);
            version.publish(clock.instant());
        } catch (IllegalStateException invalidState) {
            throw conflict(invalidState.getMessage(), invalidState);
        }
        audit.record(actorSubject, "EXERCISE_VERSION_PUBLISHED", "ExerciseVersion", version.id);
        return view(exercise(version.exerciseId), version);
    }

    @Transactional
    public VersionView withdraw(String actorSubject, UUID versionId) {
        ExerciseVersion version = lockedVersion(versionId);
        try {
            version.withdraw(clock.instant());
        } catch (IllegalStateException invalidState) {
            throw conflict(invalidState.getMessage(), invalidState);
        }
        audit.record(actorSubject, "EXERCISE_VERSION_WITHDRAWN", "ExerciseVersion", version.id);
        return view(exercise(version.exerciseId), version);
    }

    @Transactional(readOnly = true)
    public CatalogPage listPublished(String query, MovementPattern movementPattern,
                                     TechnicalLevel technicalLevel, String equipment,
                                     int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw badRequest("page must be non-negative and size must be between 1 and 100");
        }
        String normalizedQuery = normalize(query);
        String normalizedEquipment = normalizeTag(equipment);
        Page<ExerciseVersionRepository.CatalogListProjection> result = versions.findCurrentPublished(
                normalizedQuery == null, normalizedQuery == null ? "" : "%" + normalizedQuery + "%",
                movementPattern == null, movementPattern == null ? MovementPattern.OTHER : movementPattern,
                technicalLevel == null,
                technicalLevel == null ? TechnicalLevel.FOUNDATIONAL : technicalLevel,
                normalizedEquipment == null, normalizedEquipment == null ? "" : normalizedEquipment,
                PageRequest.of(page, size));
        List<CatalogItem> content = result.stream().map(item -> new CatalogItem(
                item.getExerciseId(), item.getCanonicalName(), item.getVersionId(), item.getVersionNumber(),
                item.getPrimaryMovementPattern(), item.getTechnicalLevel(), item.getEnvironment())).toList();
        return new CatalogPage(content, result.getNumber(), result.getSize(),
                result.getTotalElements(), result.getTotalPages());
    }

    @Transactional(readOnly = true)
    public PublishedExerciseVersionSnapshot published(UUID versionId) {
        return findPublishedVersion(versionId)
                .orElseThrow(() -> notFound("published exercise version not found"));
    }

    @Transactional(readOnly = true)
    public EditorView editor(UUID versionId) {
        ExerciseVersion version = version(versionId);
        return editorView(exercise(version.exerciseId), version);
    }

    @Transactional(readOnly = true)
    public List<VersionView> allVersions(UUID exerciseId) {
        Exercise exercise = exercise(exerciseId);
        return versions.findByExerciseIdOrderByVersionNumber(exerciseId).stream()
                .map(version -> view(exercise, version))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<LegacyContraindicationReportItem> legacyContraindicationReport() {
        return versions.legacyContraindicationReport().stream()
                .map(item -> new LegacyContraindicationReportItem(item.getTag(), item.getVersionCount(),
                        "UNMAPPED_READ_ONLY"))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<PublishedExerciseVersionSnapshot> findPublishedVersion(UUID versionId) {
        return Optional.ofNullable(findPublishedVersions(Set.of(versionId)).get(versionId));
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, PublishedExerciseVersionSnapshot> findPublishedVersions(Set<UUID> versionIds) {
        if (versionIds == null || versionIds.isEmpty()) {
            return Map.of();
        }
        Set<UUID> ids = Set.copyOf(versionIds);
        List<ExerciseVersion> published = versions.findByIdInAndStatus(ids, ExerciseVersionStatus.PUBLISHED);
        if (published.isEmpty()) {
            return Map.of();
        }
        Set<UUID> foundIds = published.stream().map(item -> item.id).collect(Collectors.toSet());
        Map<UUID, Exercise> exerciseById = exercises.findAllById(
                        published.stream().map(item -> item.exerciseId).collect(Collectors.toSet())).stream()
                .collect(Collectors.toMap(item -> item.id, Function.identity()));
        Map<UUID, Set<MovementPattern>> patterns = groupPatterns(versions.findMovementPatterns(foundIds));
        Map<UUID, Set<String>> equipment = groupEquipment(versions.findEquipment(foundIds));
        List<ExerciseContribution> allContributions = contributions.findByExerciseVersionIdIn(foundIds);
        List<ExerciseLoadCharacteristic> allCharacteristics = loadCharacteristics
                .findByExerciseVersionIdIn(foundIds);
        Map<UUID, EvidenceSource> evidenceById = evidenceSources.findByExerciseVersionIdIn(foundIds).stream()
                .collect(Collectors.toMap(item -> item.id, Function.identity()));
        Set<UUID> contributionIds = allContributions.stream().map(item -> item.id).collect(Collectors.toSet());
        Map<UUID, List<EvidenceSource>> evidenceByContribution = contributionIds.isEmpty()
                ? Map.of() : linkedEvidence(contributionIds, evidenceById);
        return published.stream().collect(Collectors.toUnmodifiableMap(item -> item.id, item -> snapshot(
                exerciseById.get(item.exerciseId), item,
                patterns.getOrDefault(item.id, Set.of()), equipment.getOrDefault(item.id, Set.of()),
                allContributions.stream().filter(value -> value.exerciseVersionId.equals(item.id)).toList(),
                allCharacteristics.stream().filter(value -> value.exerciseVersionId.equals(item.id)).toList(),
                evidenceByContribution)));
    }

    private void validateCompleteProfile(ExerciseVersion version) {
        if (version.profileSchemaVersion != 2) {
            throw new IllegalStateException("legacy profile must be migrated to schema version 2 before review");
        }
        if (version.movementPatterns.isEmpty()) {
            throw new IllegalStateException("at least one movement pattern is required");
        }
        List<ExerciseLoadCharacteristic> characteristics = loadCharacteristics
                .findByExerciseVersionIdOrderById(version.id);
        List<ExerciseContribution> profileContributions = contributions
                .findByExerciseVersionIdOrderById(version.id);
        if (characteristics.isEmpty() || profileContributions.isEmpty()) {
            throw new IllegalStateException("load characteristics and contributions are required");
        }
        Set<UUID> contributionIds = profileContributions.stream().map(item -> item.id).collect(Collectors.toSet());
        Set<UUID> evidenced = contributionEvidence.findByContributionIdIn(contributionIds).stream()
                .map(item -> item.contributionId).collect(Collectors.toSet());
        if (!evidenced.containsAll(contributionIds)) {
            throw new IllegalStateException("every contribution must reference evidence");
        }
        for (ExerciseContribution contribution : profileContributions) {
            AnatomyReferenceQueryPort.AnatomicalStructureSnapshot structure = anatomy
                    .findStructure(contribution.anatomicalStructureId)
                    .orElseThrow(() -> new IllegalStateException("contribution references unknown anatomy"));
            if (structure.status() != StructureStatus.PUBLISHED) {
                throw new IllegalStateException("contributions must reference published anatomy");
            }
        }
        validateAllocationBranches(profileContributions);
    }

    private ValidatedContribution validateContribution(UUID versionId, ContributionCommand command) {
        if (command == null || command.anatomicalStructureId() == null || command.role() == null
                || command.loadChannel() == null || command.contributionBand() == null
                || command.calculationRole() == null || command.sideRule() == null) {
            throw badRequest("all contribution classification fields are required");
        }
        if (command.coefficientLow() == null || command.coefficientHigh() == null
                || command.coefficientLow().compareTo(BigDecimal.ZERO) < 0
                || command.coefficientLow().compareTo(command.coefficientHigh()) > 0
                || command.coefficientHigh().compareTo(BigDecimal.ONE) > 0
                || command.coefficientLow().scale() > 6 || command.coefficientHigh().scale() > 6) {
            throw badRequest("contribution interval must satisfy 0 <= low <= high <= 1");
        }
        if (anatomy.findStructure(command.anatomicalStructureId()).isEmpty()) {
            throw badRequest("anatomical structure does not exist");
        }
        Set<UUID> evidenceIds = command.evidenceSourceIds() == null
                ? Set.of() : Set.copyOf(command.evidenceSourceIds());
        if (evidenceIds.isEmpty() || evidenceSources.findByExerciseVersionIdAndIdIn(versionId, evidenceIds).size()
                != evidenceIds.size()) {
            throw badRequest("every contribution requires evidence belonging to the same exercise version");
        }
        return new ValidatedContribution(versionId, command.anatomicalStructureId(), command.role(),
                command.loadChannel(), command.contributionBand(), command.coefficientLow(),
                command.coefficientHigh(), normalizedCode(command.confidenceClass(), 80, "confidence class"),
                normalizedCode(command.evidenceGrade(), 80, "evidence grade"), command.calculationRole(),
                optionalCode(command.variantCondition(), 120, "variant condition"), command.sideRule(), evidenceIds);
    }

    private void validateAllocationBranches(List<ExerciseContribution> profileContributions) {
        List<ExerciseContribution> allocations = profileContributions.stream()
                .filter(item -> item.calculationRole == CalculationRole.ALLOCATION).toList();
        Map<UUID, Set<UUID>> ancestors = new HashMap<>();
        allocations.forEach(item -> ancestors.computeIfAbsent(item.anatomicalStructureId, structureId ->
                anatomy.ancestorPaths(structureId).stream()
                        .flatMap(path -> path.steps().stream())
                        .map(step -> step.structure().id())
                        .collect(Collectors.toSet())));
        for (int left = 0; left < allocations.size(); left++) {
            ExerciseContribution first = allocations.get(left);
            for (int right = left + 1; right < allocations.size(); right++) {
                ExerciseContribution second = allocations.get(right);
                if (!sameAllocationBranch(first, second)) {
                    continue;
                }
                if (first.anatomicalStructureId.equals(second.anatomicalStructureId)
                        || ancestors.get(first.anatomicalStructureId).contains(second.anatomicalStructureId)
                        || ancestors.get(second.anatomicalStructureId).contains(first.anatomicalStructureId)) {
                    throw conflict("allocation cannot include both parent and child in the same channel and variant",
                            null);
                }
            }
        }
    }

    private static boolean sameAllocationBranch(ExerciseContribution first, ExerciseContribution second) {
        return first.loadChannel == second.loadChannel
                && java.util.Objects.equals(first.variantCondition, second.variantCondition)
                && first.sideRule == second.sideRule;
    }

    private ExerciseVersion editableVersion(UUID id) {
        ExerciseVersion version = lockedVersion(id);
        try {
            version.requireEditable();
        } catch (IllegalStateException immutable) {
            throw conflict(immutable.getMessage(), immutable);
        }
        return version;
    }

    private Exercise exercise(UUID id) {
        return exercises.findById(id).orElseThrow(() -> notFound("exercise not found"));
    }

    private ExerciseVersion version(UUID id) {
        return versions.findById(id).orElseThrow(() -> notFound("exercise version not found"));
    }

    private ExerciseVersion lockedVersion(UUID id) {
        return versions.findLockedById(id).orElseThrow(() -> notFound("exercise version not found"));
    }

    private EditorView editorView(Exercise exercise, ExerciseVersion version) {
        List<EvidenceSource> evidence = evidenceSources.findByExerciseVersionIdOrderById(version.id);
        Map<UUID, EvidenceSource> evidenceById = evidence.stream()
                .collect(Collectors.toMap(item -> item.id, Function.identity()));
        List<ExerciseContribution> contributionItems = contributions
                .findByExerciseVersionIdOrderById(version.id);
        Set<UUID> contributionIds = contributionItems.stream().map(item -> item.id).collect(Collectors.toSet());
        Map<UUID, List<EvidenceSource>> linked = contributionIds.isEmpty()
                ? Map.of() : linkedEvidence(contributionIds, evidenceById);
        return new EditorView(view(exercise, version),
                loadCharacteristics.findByExerciseVersionIdOrderById(version.id).stream()
                        .map(CatalogService::characteristicView).toList(),
                evidence.stream().map(CatalogService::evidenceView).toList(),
                contributionItems.stream().map(item -> contributionView(item,
                        linked.getOrDefault(item.id, List.of()).stream()
                                .map(CatalogService::evidenceView).toList())).toList(),
                Set.copyOf(version.contraindicationTags));
    }

    private Map<UUID, List<EvidenceSource>> linkedEvidence(Set<UUID> contributionIds,
                                                           Map<UUID, EvidenceSource> evidenceById) {
        return contributionEvidence.findByContributionIdIn(contributionIds).stream()
                .filter(link -> evidenceById.containsKey(link.evidenceSourceId))
                .collect(Collectors.groupingBy(link -> link.contributionId,
                        Collectors.mapping(link -> evidenceById.get(link.evidenceSourceId), Collectors.toList())));
    }

    private static Map<UUID, Set<MovementPattern>> groupPatterns(
            List<ExerciseVersionRepository.MovementPatternProjection> values) {
        return values.stream().collect(Collectors.groupingBy(
                ExerciseVersionRepository.MovementPatternProjection::getVersionId,
                Collectors.mapping(ExerciseVersionRepository.MovementPatternProjection::getMovementPattern,
                        Collectors.toUnmodifiableSet())));
    }

    private static Map<UUID, Set<String>> groupEquipment(
            List<ExerciseVersionRepository.EquipmentProjection> values) {
        return values.stream().collect(Collectors.groupingBy(
                ExerciseVersionRepository.EquipmentProjection::getVersionId,
                Collectors.mapping(ExerciseVersionRepository.EquipmentProjection::getEquipment,
                        Collectors.toUnmodifiableSet())));
    }

    private static PublishedExerciseVersionSnapshot snapshot(
            Exercise exercise, ExerciseVersion version, Set<MovementPattern> patterns, Set<String> equipment,
            List<ExerciseContribution> contributionItems,
            List<ExerciseLoadCharacteristic> characteristicItems,
            Map<UUID, List<EvidenceSource>> evidenceByContribution) {
        return new PublishedExerciseVersionSnapshot(exercise.id, exercise.canonicalName,
                version.id, version.versionNumber,
                patterns.stream().map(value -> MovementPatternValue.valueOf(value.name()))
                        .collect(Collectors.toUnmodifiableSet()),
                equipment,
                contributionItems.stream().sorted(Comparator.comparing(item -> item.id))
                        .map(item -> contributionSnapshot(item,
                                evidenceByContribution.getOrDefault(item.id, List.of()))).toList(),
                characteristicItems.stream().sorted(Comparator.comparing(item -> item.id))
                        .map(CatalogService::characteristicSnapshot).toList());
    }

    private static ContributionSnapshot contributionSnapshot(ExerciseContribution item,
                                                               List<EvidenceSource> evidence) {
        return new ContributionSnapshot(item.id, item.anatomicalStructureId,
                ContributionRoleValue.valueOf(item.role.name()), LoadChannelValue.valueOf(item.loadChannel.name()),
                ContributionBandValue.valueOf(item.contributionBand.name()), item.coefficientLow,
                item.coefficientHigh, item.confidenceClass, item.evidenceGrade,
                CalculationRoleValue.valueOf(item.calculationRole.name()), item.variantCondition,
                SideRuleValue.valueOf(item.sideRule.name()), evidence.stream()
                        .sorted(Comparator.comparing(value -> value.id))
                        .map(CatalogService::evidenceSnapshot).toList());
    }

    private static LoadCharacteristicSnapshot characteristicSnapshot(ExerciseLoadCharacteristic item) {
        return new LoadCharacteristicSnapshot(item.id, MovementPlaneValue.valueOf(item.movementPlane.name()),
                ContractionTypeValue.valueOf(item.contractionType.name()),
                RangeOfMotionValue.valueOf(item.rangeOfMotion.name()),
                LoadCharacteristicValue.valueOf(item.characteristicType.name()));
    }

    private static VersionView view(Exercise exercise, ExerciseVersion version) {
        return new VersionView(exercise.id, exercise.canonicalName, version.id, version.versionNumber,
                version.status, Set.copyOf(version.movementPatterns), version.instruction,
                version.mediaReference, version.stimulusType, version.fatigueProfile,
                version.technicalLevel, version.environment, Set.copyOf(version.requiredEquipment),
                version.profileSchemaVersion, version.reviewedBySubject, version.reviewedAt,
                version.publishedAt, version.withdrawnAt);
    }

    private static ContributionView contributionView(ExerciseContribution item, List<EvidenceView> evidence) {
        return new ContributionView(item.id, item.anatomicalStructureId, item.role, item.loadChannel,
                item.contributionBand, item.coefficientLow, item.coefficientHigh, item.confidenceClass,
                item.evidenceGrade, item.calculationRole, item.variantCondition, item.sideRule, evidence);
    }

    private static EvidenceView evidenceView(EvidenceSource item) {
        return new EvidenceView(item.id, item.citation, item.sourceUri, item.evidenceGrade);
    }

    private static EvidenceSnapshot evidenceSnapshot(EvidenceSource item) {
        return new EvidenceSnapshot(item.id, item.citation, item.sourceUri, item.evidenceGrade);
    }

    private static LoadCharacteristicView characteristicView(ExerciseLoadCharacteristic item) {
        return new LoadCharacteristicView(item.id, item.movementPlane, item.contractionType,
                item.rangeOfMotion, item.characteristicType);
    }

    private static VersionCommand validate(VersionCommand command) {
        if (command == null || command.stimulusType() == null || command.fatigueProfile() == null
                || command.technicalLevel() == null || command.environment() == null
                || command.movementPatterns() == null || command.movementPatterns().isEmpty()) {
            throw badRequest("all classification fields and at least one movement pattern are required");
        }
        return new VersionCommand(requiredText(command.instruction(), 10_000, "instruction"),
                optionalText(command.mediaReference(), 2_000, "media reference"),
                Set.copyOf(command.movementPatterns()), command.stimulusType(), command.fatigueProfile(),
                command.technicalLevel(), command.environment(), normalizedTags(command.requiredEquipment()));
    }

    private static List<LoadCharacteristicCommand> validateCharacteristics(
            Collection<LoadCharacteristicCommand> requested) {
        if (requested == null || requested.isEmpty()) {
            throw badRequest("at least one load characteristic is required");
        }
        LinkedHashSet<LoadCharacteristicCommand> unique = new LinkedHashSet<>();
        for (LoadCharacteristicCommand command : requested) {
            if (command == null || command.movementPlane() == null || command.contractionType() == null
                    || command.rangeOfMotion() == null || command.characteristicType() == null) {
                throw badRequest("all load characteristic fields are required");
            }
            unique.add(command);
        }
        return List.copyOf(unique);
    }

    private static Set<String> normalizedTags(Collection<String> values) {
        if (values == null) {
            return Set.of();
        }
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        values.forEach(value -> normalized.add(normalizedCode(value, 80, "equipment")));
        return Set.copyOf(normalized);
    }

    private static String requiredText(String value, int max, String field) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty() || normalized.length() > max) {
            throw badRequest(field + " is required and too long values are rejected");
        }
        return normalized;
    }

    private static String optionalText(String value, int max, String field) {
        return value == null || value.isBlank() ? null : requiredText(value, max, field);
    }

    private static String normalizedCode(String value, int max, String field) {
        String normalized = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        if (normalized.isEmpty() || normalized.length() > max || !normalized.matches("[A-Z0-9_:-]+")) {
            throw badRequest(field + " must be a 1-" + max + " character stable code");
        }
        return normalized;
    }

    private static String optionalCode(String value, int max, String field) {
        return value == null || value.isBlank() ? null : normalizedCode(value, max, field);
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim().toLowerCase(Locale.ROOT);
    }

    private static String normalizeTag(String value) {
        return value == null || value.isBlank() ? null : value.trim().toUpperCase(Locale.ROOT);
    }

    private static ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    private static ResponseStatusException notFound(String message) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, message);
    }

    private static ResponseStatusException conflict(String message, Throwable cause) {
        return new ResponseStatusException(HttpStatus.CONFLICT, message, cause);
    }

    public record VersionCommand(String instruction, String mediaReference,
                                 Set<MovementPattern> movementPatterns, StimulusType stimulusType,
                                 FatigueProfile fatigueProfile, TechnicalLevel technicalLevel,
                                 ExerciseEnvironment environment, Set<String> requiredEquipment) {
    }

    public record LoadCharacteristicCommand(MovementPlane movementPlane, ContractionType contractionType,
                                            RangeOfMotion rangeOfMotion,
                                            LoadCharacteristicType characteristicType) {
    }

    public record EvidenceCommand(String citation, String sourceUri, String evidenceGrade) {
    }

    public record ContributionCommand(UUID anatomicalStructureId, ContributionRole role,
                                      LoadChannel loadChannel, ContributionBand contributionBand,
                                      BigDecimal coefficientLow, BigDecimal coefficientHigh,
                                      String confidenceClass, String evidenceGrade,
                                      CalculationRole calculationRole, String variantCondition,
                                      ContributionSideRule sideRule, Set<UUID> evidenceSourceIds) {
    }

    private record ValidatedContribution(UUID versionId, UUID anatomicalStructureId, ContributionRole role,
                                         LoadChannel loadChannel, ContributionBand contributionBand,
                                         BigDecimal coefficientLow, BigDecimal coefficientHigh,
                                         String confidenceClass, String evidenceGrade,
                                         CalculationRole calculationRole, String variantCondition,
                                         ContributionSideRule sideRule, Set<UUID> evidenceIds) {
        ExerciseContribution entity(String actorSubject, Instant now) {
            return new ExerciseContribution(versionId, anatomicalStructureId, role, loadChannel,
                    contributionBand, coefficientLow, coefficientHigh, confidenceClass, evidenceGrade,
                    calculationRole, variantCondition, sideRule, actorSubject, now);
        }
    }

    public record VersionView(UUID exerciseId, String canonicalName, UUID versionId, int versionNumber,
                              ExerciseVersionStatus status, Set<MovementPattern> movementPatterns,
                              String instruction, String mediaReference, StimulusType stimulusType,
                              FatigueProfile fatigueProfile, TechnicalLevel technicalLevel,
                              ExerciseEnvironment environment, Set<String> requiredEquipment,
                              int profileSchemaVersion, String reviewedBySubject, Instant reviewedAt,
                              Instant publishedAt, Instant withdrawnAt) {
    }

    public record CatalogItem(UUID exerciseId, String canonicalName, UUID versionId, int versionNumber,
                              MovementPattern primaryMovementPattern, TechnicalLevel technicalLevel,
                              ExerciseEnvironment environment) {
    }

    public record CatalogPage(List<CatalogItem> content, int page, int size,
                              long totalElements, int totalPages) {
        public CatalogPage {
            content = List.copyOf(content);
        }
    }

    public record EditorView(VersionView version, List<LoadCharacteristicView> loadCharacteristics,
                             List<EvidenceView> evidence, List<ContributionView> contributions,
                             Set<String> legacyContraindicationTags) {
    }

    public record LoadCharacteristicView(UUID id, MovementPlane movementPlane,
                                         ContractionType contractionType, RangeOfMotion rangeOfMotion,
                                         LoadCharacteristicType characteristicType) {
    }

    public record EvidenceView(UUID id, String citation, String sourceUri, String evidenceGrade) {
    }

    public record ContributionView(UUID id, UUID anatomicalStructureId, ContributionRole role,
                                   LoadChannel loadChannel, ContributionBand contributionBand,
                                   BigDecimal coefficientLow, BigDecimal coefficientHigh,
                                   String confidenceClass, String evidenceGrade,
                                   CalculationRole calculationRole, String variantCondition,
                                   ContributionSideRule sideRule, List<EvidenceView> evidence) {
    }

    public record LegacyContraindicationReportItem(String tag, long versionCount, String disposition) {
    }
}
