package com.motionecosystem.exercisecatalog.api;

import java.util.UUID;

/** Import-owned durable references and publication blockers for a catalog draft. */
public interface ExerciseDraftReferenceQueryPort {
    DraftReferenceState references(UUID exerciseId, UUID exerciseVersionId);

    record DraftReferenceState(boolean importRecord, boolean sourceReference, boolean matchCandidate,
                               boolean unresolvedImportIssues) {
        public boolean hasDeletionReference() {
            return importRecord || sourceReference || matchCandidate;
        }
    }
}
