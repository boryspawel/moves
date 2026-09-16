package com.motionecosystem.exerciseimport;

import com.motionecosystem.exercisecatalog.api.ExerciseDraftReferenceQueryPort;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** Keeps import-table inspection behind the catalog public API. */
@Repository
@RequiredArgsConstructor
class ExerciseImportDraftReferenceAdapter implements ExerciseDraftReferenceQueryPort {
    private final ExerciseImportRecordRepository records;
    private final ExerciseImportSourceReferenceRepository sourceReferences;
    private final ExerciseImportMatchCandidateRepository candidates;
    private final ExerciseImportIssueRepository issues;

    @Override
    @Transactional(readOnly = true)
    public DraftReferenceState references(UUID exerciseId, UUID exerciseVersionId) {
        boolean importRecord = records.existsByDraftVersionId(exerciseVersionId);
        boolean sourceReference = sourceReferences.existsByExerciseIdOrLatestExerciseVersionId(exerciseId, exerciseVersionId);
        boolean matchCandidate = candidates.existsByExerciseId(exerciseId);
        boolean unresolvedIssues = false;
        if (importRecord) {
            unresolvedIssues = records.findByDraftVersionId(exerciseVersionId)
                    .map(record -> issues.countByRecordIdAndResolvedAtIsNullAndSeverityIn(
                            record.id, List.of("ERROR", "BLOCKER")) > 0)
                    .orElse(false);
        }
        return new DraftReferenceState(importRecord, sourceReference, matchCandidate, unresolvedIssues);
    }
}
