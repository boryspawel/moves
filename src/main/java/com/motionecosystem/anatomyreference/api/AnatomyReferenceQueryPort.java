package com.motionecosystem.anatomyreference.api;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AnatomyReferenceQueryPort {

    Optional<AnatomicalStructureSnapshot> findStructure(UUID structureId);

    List<AncestorPath> ancestorPaths(UUID structureId);

    record AnatomicalStructureSnapshot(UUID id, String code, StructureType type,
                                       String displayName, StructureSidePolicy sidePolicy,
                                       StructureStatus status, String externalOntology,
                                       String externalOntologyId, int taxonomyVersion,
                                       Instant createdAt, Instant publishedAt, Instant withdrawnAt) {
    }

    record AncestorStep(AnatomicalStructureSnapshot structure, StructureRelationType relationType) {
    }

    record AncestorPath(List<AncestorStep> steps) {
        public AncestorPath {
            steps = List.copyOf(steps);
        }
    }

    enum StructureType { BODY_REGION, MUSCLE_GROUP, MUSCLE, TENDON_GROUP, JOINT }

    enum StructureSidePolicy { NONE, LEFT_RIGHT }

    enum StructureStatus { DRAFT, PUBLISHED, WITHDRAWN }

    enum StructureRelationType { PART_OF, MEMBER_OF, FUNCTIONALLY_GROUPED_AS }
}
