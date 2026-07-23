package com.motionecosystem.anatomyreference.application;

import java.time.Clock;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import com.motionecosystem.anatomyreference.api.AnatomyReferenceQueryPort;
import com.motionecosystem.anatomyreference.domain.AnatomicalStructure;
import com.motionecosystem.anatomyreference.domain.AnatomicalStructureRelation;
import com.motionecosystem.anatomyreference.domain.AnatomicalStructureType;
import com.motionecosystem.anatomyreference.domain.RelationType;
import com.motionecosystem.anatomyreference.domain.SidePolicy;
import com.motionecosystem.audit.AuditRecorder;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class AnatomyReferenceService implements AnatomyReferenceQueryPort {

    private final AnatomyReferencePersistence persistence;
    private final AuditRecorder audit;
    private final Clock clock;

    @Transactional
    public AnatomicalStructureSnapshot createDraft(String actorSubject, CreateStructureCommand command) {
        if (command == null || command.type() == null || command.sidePolicy() == null) {
            throw badRequest("type and sidePolicy are required");
        }
        AnatomicalStructure structure;
        try {
            structure = AnatomicalStructure.draft(UUID.randomUUID(), normalizeCode(command.code()), command.type(),
                    command.displayName(), command.sidePolicy(), command.externalOntology(),
                    command.externalOntologyId(), command.taxonomyVersion(), actorSubject, clock.instant());
            persistence.create(structure);
        } catch (IllegalArgumentException invalid) {
            throw badRequest(invalid.getMessage());
        } catch (DataIntegrityViolationException duplicate) {
            throw conflict("an anatomical structure with this code already exists", duplicate);
        }
        audit.record(actorSubject, "ANATOMICAL_STRUCTURE_DRAFTED", "AnatomicalStructure", structure.id());
        return snapshot(structure);
    }

    @Transactional
    public AnatomicalStructureSnapshot publish(String actorSubject, UUID structureId) {
        persistence.lockHierarchy();
        AnatomicalStructure structure = requireStructure(structureId);
        try {
            structure.publish(clock.instant());
            persistence.update(structure);
        } catch (IllegalStateException invalidState) {
            throw conflict(invalidState.getMessage(), invalidState);
        }
        audit.record(actorSubject, "ANATOMICAL_STRUCTURE_PUBLISHED", "AnatomicalStructure", structure.id());
        return snapshot(structure);
    }

    @Transactional
    public AnatomicalStructureSnapshot withdraw(String actorSubject, UUID structureId) {
        AnatomicalStructure structure = requireStructure(structureId);
        try {
            structure.withdraw(clock.instant());
            persistence.update(structure);
        } catch (IllegalStateException invalidState) {
            throw conflict(invalidState.getMessage(), invalidState);
        }
        audit.record(actorSubject, "ANATOMICAL_STRUCTURE_WITHDRAWN", "AnatomicalStructure", structure.id());
        return snapshot(structure);
    }

    @Transactional
    public RelationSnapshot addRelation(String actorSubject, AddRelationCommand command) {
        if (command == null || command.parentId() == null || command.childId() == null
                || command.relationType() == null) {
            throw badRequest("parentId, childId and relationType are required");
        }
        if (command.parentId().equals(command.childId())) {
            throw badRequest("an anatomical structure cannot be related to itself");
        }
        persistence.lockHierarchy();
        AnatomicalStructure parent = requireStructure(command.parentId());
        AnatomicalStructure child = requireStructure(command.childId());
        try {
            parent.requireRelationMutable();
            child.requireRelationMutable();
        } catch (IllegalStateException immutable) {
            throw conflict(immutable.getMessage(), immutable);
        }
        boolean closesCycle = ancestorPathsInternal(parent.id()).stream()
                .flatMap(path -> path.stream())
                .anyMatch(edge -> edge.parentId().equals(child.id()));
        if (closesCycle) {
            throw conflict("anatomical structure relation would create a cycle", null);
        }
        AnatomicalStructureRelation relation;
        try {
            relation = new AnatomicalStructureRelation(UUID.randomUUID(), parent.id(), child.id(),
                    command.relationType(), clock.instant(), actorSubject);
            persistence.addRelation(relation);
        } catch (IllegalArgumentException invalid) {
            throw badRequest(invalid.getMessage());
        } catch (DataIntegrityViolationException duplicate) {
            throw conflict("anatomical structure relation already exists", duplicate);
        }
        audit.record(actorSubject, "ANATOMICAL_STRUCTURE_RELATION_ADDED",
                "AnatomicalStructureRelation", relation.id());
        return new RelationSnapshot(relation.id(), relation.parentId(), relation.childId(), relation.relationType());
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<AnatomicalStructureSnapshot> findStructure(UUID structureId) {
        return persistence.find(structureId).map(AnatomyReferenceService::snapshot);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, AnatomicalStructureSnapshot> findStructures(Collection<UUID> structureIds) {
        if (structureIds == null || structureIds.isEmpty()) {
            return Map.of();
        }
        return persistence.findAll(Set.copyOf(structureIds)).values().stream()
                .collect(Collectors.toUnmodifiableMap(AnatomicalStructure::id, AnatomyReferenceService::snapshot));
    }

    @Override
    @Transactional(readOnly = true)
    public List<AncestorPath> ancestorPaths(UUID structureId) {
        requireStructure(structureId);
        List<List<AnatomyReferencePersistence.ParentEdge>> paths = ancestorPathsInternal(structureId);
        Set<UUID> ids = paths.stream().flatMap(Collection::stream)
                .map(AnatomyReferencePersistence.ParentEdge::parentId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Map<UUID, AnatomicalStructure> structures = persistence.findAll(ids);
        return paths.stream().map(path -> new AncestorPath(path.stream()
                        .map(edge -> new AncestorStep(snapshot(required(structures, edge.parentId())),
                                StructureRelationType.valueOf(edge.relationType().name())))
                        .toList()))
                .sorted(Comparator.comparing(AnatomyReferenceService::pathKey))
                .toList();
    }

    private List<List<AnatomyReferencePersistence.ParentEdge>> ancestorPathsInternal(UUID structureId) {
        List<List<AnatomyReferencePersistence.ParentEdge>> active = persistence.findParentEdges(Set.of(structureId))
                .stream().map(List::of).collect(Collectors.toCollection(ArrayList::new));
        List<List<AnatomyReferencePersistence.ParentEdge>> complete = new ArrayList<>();
        while (!active.isEmpty()) {
            Set<UUID> frontier = active.stream().map(List::getLast)
                    .map(AnatomyReferencePersistence.ParentEdge::parentId)
                    .collect(Collectors.toSet());
            Map<UUID, List<AnatomyReferencePersistence.ParentEdge>> parents = persistence.findParentEdges(frontier)
                    .stream().collect(Collectors.groupingBy(AnatomyReferencePersistence.ParentEdge::childId));
            List<List<AnatomyReferencePersistence.ParentEdge>> next = new ArrayList<>();
            for (List<AnatomyReferencePersistence.ParentEdge> path : active) {
                UUID current = path.getLast().parentId();
                List<AnatomyReferencePersistence.ParentEdge> incoming = parents.getOrDefault(current, List.of());
                if (incoming.isEmpty()) {
                    complete.add(path);
                    continue;
                }
                Set<UUID> visited = new HashSet<>();
                visited.add(structureId);
                path.forEach(edge -> visited.add(edge.parentId()));
                for (AnatomyReferencePersistence.ParentEdge edge : incoming) {
                    if (!visited.add(edge.parentId())) {
                        throw new IllegalStateException("stored anatomy hierarchy contains a cycle");
                    }
                    List<AnatomyReferencePersistence.ParentEdge> extended = new ArrayList<>(path);
                    extended.add(edge);
                    next.add(List.copyOf(extended));
                    visited.remove(edge.parentId());
                }
            }
            active = next;
        }
        return complete;
    }

    private AnatomicalStructure requireStructure(UUID structureId) {
        if (structureId == null) {
            throw badRequest("structure id is required");
        }
        return persistence.find(structureId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "anatomical structure not found"));
    }

    private static AnatomicalStructure required(Map<UUID, AnatomicalStructure> structures, UUID id) {
        AnatomicalStructure structure = structures.get(id);
        if (structure == null) {
            throw new IllegalStateException("anatomy hierarchy references a missing structure");
        }
        return structure;
    }

    private static String normalizeCode(String value) {
        String code = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        if (!code.matches("[A-Z][A-Z0-9_:-]{1,79}")) {
            throw new IllegalArgumentException(
                    "code must contain 2-80 uppercase letters, digits, underscore, colon or hyphen");
        }
        return code;
    }

    private static AnatomicalStructureSnapshot snapshot(AnatomicalStructure structure) {
        return new AnatomicalStructureSnapshot(structure.id(), structure.code(),
                StructureType.valueOf(structure.type().name()), structure.displayName(),
                StructureSidePolicy.valueOf(structure.sidePolicy().name()),
                StructureStatus.valueOf(structure.status().name()), structure.externalOntology(),
                structure.externalOntologyId(), structure.taxonomyVersion(), structure.createdAt(),
                structure.publishedAt(), structure.withdrawnAt());
    }

    private static String pathKey(AncestorPath path) {
        return path.steps().stream().map(step -> step.structure().code() + ":" + step.relationType())
                .collect(Collectors.joining("/"));
    }

    private static ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    private static ResponseStatusException conflict(String message, Throwable cause) {
        return new ResponseStatusException(HttpStatus.CONFLICT, message, cause);
    }

    public record CreateStructureCommand(String code, AnatomicalStructureType type, String displayName,
                                         SidePolicy sidePolicy, String externalOntology,
                                         String externalOntologyId, int taxonomyVersion) {
    }

    public record AddRelationCommand(UUID parentId, UUID childId, RelationType relationType) {
    }

    public record RelationSnapshot(UUID id, UUID parentId, UUID childId, RelationType relationType) {
    }
}
