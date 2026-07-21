package com.motionecosystem.anatomyreference.api;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.motionecosystem.anatomyreference.domain.AnatomicalStructureType;
import com.motionecosystem.anatomyreference.domain.PublicationStatus;
import com.motionecosystem.anatomyreference.domain.RelationType;
import com.motionecosystem.anatomyreference.domain.SidePolicy;

public interface AnatomyReferenceQueryPort {

    Optional<AnatomicalStructureSnapshot> findStructure(UUID structureId);

    List<AncestorPath> ancestorPaths(UUID structureId);

    record AnatomicalStructureSnapshot(UUID id, String code, AnatomicalStructureType type,
                                       String displayName, SidePolicy sidePolicy,
                                       PublicationStatus status, String externalOntology,
                                       String externalOntologyId, int taxonomyVersion,
                                       Instant createdAt, Instant publishedAt, Instant withdrawnAt) {
    }

    record AncestorStep(AnatomicalStructureSnapshot structure, RelationType relationType) {
    }

    record AncestorPath(List<AncestorStep> steps) {
        public AncestorPath {
            steps = List.copyOf(steps);
        }
    }
}
