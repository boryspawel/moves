package com.motionecosystem.anatomyreference.application;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.motionecosystem.anatomyreference.domain.AnatomicalStructure;
import com.motionecosystem.anatomyreference.domain.AnatomicalStructureRelation;
import com.motionecosystem.anatomyreference.domain.RelationType;

public interface AnatomyReferencePersistence {

    void create(AnatomicalStructure structure);

    Optional<AnatomicalStructure> find(UUID structureId);

    Map<UUID, AnatomicalStructure> findAll(Collection<UUID> structureIds);

    void update(AnatomicalStructure structure);

    void lockHierarchy();

    void addRelation(AnatomicalStructureRelation relation);

    List<ParentEdge> findParentEdges(Set<UUID> childIds);

    List<VisualMapping> findApprovedVisualMappings(Collection<UUID> structureIds);

    List<VisualRegion> findActiveVisualRegions();

    record ParentEdge(UUID parentId, UUID childId, RelationType relationType) {
    }

    record VisualMapping(UUID structureId, long mappingVersion, UUID regionId, String regionCode,
                         String regionDisplayName, String viewName, String layerName, String labelKey,
                         UUID parentRegionId, int displayOrder, String regionStatus) { }
    record VisualRegion(UUID id, String code, String displayName, String viewName, String layerName,
                        String labelKey, UUID parentRegionId, int displayOrder, String status) { }
}
