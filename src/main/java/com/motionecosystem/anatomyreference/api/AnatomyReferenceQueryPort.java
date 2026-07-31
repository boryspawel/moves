package com.motionecosystem.anatomyreference.api;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public interface AnatomyReferenceQueryPort {

    Optional<AnatomicalStructureSnapshot> findStructure(UUID structureId);

    Map<UUID, AnatomicalStructureSnapshot> findStructures(java.util.Collection<UUID> structureIds);

    List<AncestorPath> ancestorPaths(UUID structureId);

    /** A complete, approved visual mapping snapshot for the supplied source structures. */
    Map<UUID, VisualMappingSnapshot> visualMappings(java.util.Collection<UUID> structureIds);

    List<VisualRegionSnapshot> activeVisualRegions();

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

    record VisualRegionSnapshot(UUID id, String code, String displayName, String viewName, String layerName,
                                String labelKey, UUID parentRegionId, int displayOrder, String status) { }

    record VisualMappingSnapshot(long mappingVersion, List<VisualRegionSnapshot> regions) {
        public VisualMappingSnapshot { regions = List.copyOf(regions); }
    }

    enum StructureType { BODY_REGION, MUSCLE_GROUP, MUSCLE, TENDON_GROUP, JOINT }

    enum StructureSidePolicy { NONE, LEFT_RIGHT }

    enum StructureStatus { DRAFT, PUBLISHED, WITHDRAWN }

    enum StructureRelationType { PART_OF, MEMBER_OF, FUNCTIONALLY_GROUPED_AS }
}
