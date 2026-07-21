package com.motionecosystem.anatomyreference.infrastructure;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.motionecosystem.anatomyreference.application.AnatomyReferencePersistence;
import com.motionecosystem.anatomyreference.domain.AnatomicalStructure;
import com.motionecosystem.anatomyreference.domain.AnatomicalStructureRelation;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class JpaAnatomyReferenceAdapter implements AnatomyReferencePersistence {

    private final EntityManager entityManager;

    @Override
    public void create(AnatomicalStructure structure) {
        entityManager.persist(new AnatomicalStructureJpaEntity(structure));
        entityManager.flush();
    }

    @Override
    public Optional<AnatomicalStructure> find(UUID structureId) {
        return Optional.ofNullable(entityManager.find(AnatomicalStructureJpaEntity.class, structureId))
                .map(AnatomicalStructureJpaEntity::domain);
    }

    @Override
    public Map<UUID, AnatomicalStructure> findAll(Collection<UUID> structureIds) {
        if (structureIds.isEmpty()) {
            return Map.of();
        }
        return entityManager.createQuery("""
                SELECT structure FROM AnatomicalStructureJpaEntity structure
                WHERE structure.id IN :ids
                """, AnatomicalStructureJpaEntity.class)
                .setParameter("ids", structureIds)
                .getResultList().stream()
                .map(AnatomicalStructureJpaEntity::domain)
                .collect(Collectors.toMap(AnatomicalStructure::id, Function.identity()));
    }

    @Override
    public void update(AnatomicalStructure structure) {
        AnatomicalStructureJpaEntity entity = entityManager.find(
                AnatomicalStructureJpaEntity.class, structure.id());
        if (entity == null) {
            throw new IllegalStateException("anatomical structure disappeared during update");
        }
        entity.apply(structure);
        entityManager.flush();
    }

    @Override
    public void lockHierarchy() {
        HierarchyGuardJpaEntity guard = entityManager.find(
                HierarchyGuardJpaEntity.class, (short) 1, LockModeType.PESSIMISTIC_WRITE);
        if (guard == null) {
            throw new IllegalStateException("anatomy hierarchy guard is missing");
        }
    }

    @Override
    public void addRelation(AnatomicalStructureRelation relation) {
        entityManager.persist(new AnatomicalStructureRelationJpaEntity(relation));
        entityManager.flush();
    }

    @Override
    public List<ParentEdge> findParentEdges(Set<UUID> childIds) {
        if (childIds.isEmpty()) {
            return List.of();
        }
        return entityManager.createQuery("""
                SELECT relation FROM AnatomicalStructureRelationJpaEntity relation
                WHERE relation.childId IN :childIds
                ORDER BY relation.childId, relation.parentId, relation.relationType
                """, AnatomicalStructureRelationJpaEntity.class)
                .setParameter("childIds", childIds)
                .getResultList().stream()
                .map(item -> new ParentEdge(item.parentId(), item.childId(), item.relationType()))
                .toList();
    }
}
