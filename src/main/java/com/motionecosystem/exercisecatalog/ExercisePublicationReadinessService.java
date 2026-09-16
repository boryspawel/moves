package com.motionecosystem.exercisecatalog;

import com.motionecosystem.anatomyreference.api.AnatomyReferenceQueryPort;
import com.motionecosystem.anatomyreference.api.AnatomyReferenceQueryPort.StructureStatus;
import com.motionecosystem.exercisecatalog.api.ExerciseDraftReferenceQueryPort;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Objective publication requirements; review history is intentionally not a requirement. */
@Service
@RequiredArgsConstructor
class ExercisePublicationReadinessService {
    private final ExerciseLoadCharacteristicRepository loadCharacteristics;
    private final ExerciseContributionRepository contributions;
    private final ExerciseContributionEvidenceRepository contributionEvidence;
    private final AnatomyReferenceQueryPort anatomy;
    private final ExerciseDraftReferenceQueryPort importReferences;

    @Transactional(readOnly = true)
    List<String> unmet(ExerciseVersion version) {
        List<String> result = new ArrayList<>();
        if (version.profileSchemaVersion != 2) result.add("PROFILE_SCHEMA_V2_REQUIRED");
        if (version.movementPatterns.isEmpty()) result.add("MOVEMENT_PATTERN_REQUIRED");
        List<ExerciseLoadCharacteristic> characteristics = loadCharacteristics.findByExerciseVersionIdOrderById(version.id);
        List<ExerciseContribution> profile = contributions.findByExerciseVersionIdOrderById(version.id);
        if (characteristics.isEmpty()) result.add("LOAD_CHARACTERISTIC_REQUIRED");
        if (profile.isEmpty()) result.add("ANATOMY_CONTRIBUTION_REQUIRED");
        Set<UUID> ids = profile.stream().map(item -> item.id).collect(Collectors.toSet());
        Set<UUID> evidenced = ids.isEmpty() ? Set.of() : contributionEvidence.findByContributionIdIn(ids).stream()
                .map(item -> item.contributionId).collect(Collectors.toSet());
        if (!evidenced.containsAll(ids)) result.add("CONTRIBUTION_EVIDENCE_REQUIRED");
        for (ExerciseContribution contribution : profile) {
            var structure = anatomy.findStructure(contribution.anatomicalStructureId).orElse(null);
            if (structure == null || structure.status() != StructureStatus.PUBLISHED) {
                result.add("PUBLISHED_ANATOMY_REQUIRED");
                break;
            }
        }
        if (!allocationBranchesValid(profile)) result.add("ALLOCATION_BRANCH_CONFLICT");
        if (importReferences.references(version.exerciseId, version.id).unresolvedImportIssues()) {
            result.add("UNRESOLVED_IMPORT_ISSUES");
        }
        return List.copyOf(result);
    }

    private boolean allocationBranchesValid(List<ExerciseContribution> all) {
        List<ExerciseContribution> allocations = all.stream()
                .filter(item -> item.calculationRole == CalculationRole.ALLOCATION).toList();
        Map<UUID, Set<UUID>> ancestors = new HashMap<>();
        for (ExerciseContribution item : allocations) {
            ancestors.put(item.anatomicalStructureId, anatomy.ancestorPaths(item.anatomicalStructureId).stream()
                    .flatMap(path -> path.steps().stream()).map(step -> step.structure().id()).collect(Collectors.toSet()));
        }
        for (int left = 0; left < allocations.size(); left++) for (int right = left + 1; right < allocations.size(); right++) {
            ExerciseContribution first = allocations.get(left), second = allocations.get(right);
            boolean same = first.loadChannel == second.loadChannel
                    && java.util.Objects.equals(first.variantCondition, second.variantCondition)
                    && first.sideRule == second.sideRule;
            if (same && (first.anatomicalStructureId.equals(second.anatomicalStructureId)
                    || ancestors.get(first.anatomicalStructureId).contains(second.anatomicalStructureId)
                    || ancestors.get(second.anatomicalStructureId).contains(first.anatomicalStructureId))) return false;
        }
        return true;
    }
}
