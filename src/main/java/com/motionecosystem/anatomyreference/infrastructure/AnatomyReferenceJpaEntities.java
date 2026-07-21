package com.motionecosystem.anatomyreference.infrastructure;

import java.time.Instant;
import java.util.UUID;

import com.motionecosystem.anatomyreference.domain.AnatomicalStructure;
import com.motionecosystem.anatomyreference.domain.AnatomicalStructureRelation;
import com.motionecosystem.anatomyreference.domain.AnatomicalStructureType;
import com.motionecosystem.anatomyreference.domain.PublicationStatus;
import com.motionecosystem.anatomyreference.domain.RelationType;
import com.motionecosystem.anatomyreference.domain.SidePolicy;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity(name = "AnatomicalStructureJpaEntity")
@Table(name = "anatomical_structure", schema = "anatomy_reference")
class AnatomicalStructureJpaEntity {
    @Id UUID id;
    @Column(nullable = false, unique = true, length = 80) String code;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 32) AnatomicalStructureType type;
    @Column(name = "display_name", nullable = false, length = 160) String displayName;
    @Enumerated(EnumType.STRING) @Column(name = "side_policy", nullable = false, length = 24) SidePolicy sidePolicy;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 24) PublicationStatus status;
    @Column(name = "external_ontology", length = 120) String externalOntology;
    @Column(name = "external_ontology_id", length = 200) String externalOntologyId;
    @Column(name = "taxonomy_version", nullable = false) int taxonomyVersion;
    @Column(name = "created_by_subject", nullable = false, updatable = false, length = 255) String createdBySubject;
    @Column(name = "created_at", nullable = false, updatable = false) Instant createdAt;
    @Column(name = "published_at") Instant publishedAt;
    @Column(name = "withdrawn_at") Instant withdrawnAt;
    @Version long version;

    protected AnatomicalStructureJpaEntity() {
    }

    AnatomicalStructureJpaEntity(AnatomicalStructure structure) {
        id = structure.id();
        code = structure.code();
        type = structure.type();
        displayName = structure.displayName();
        sidePolicy = structure.sidePolicy();
        externalOntology = structure.externalOntology();
        externalOntologyId = structure.externalOntologyId();
        taxonomyVersion = structure.taxonomyVersion();
        createdBySubject = structure.createdBySubject();
        createdAt = structure.createdAt();
        apply(structure);
    }

    void apply(AnatomicalStructure structure) {
        status = structure.status();
        publishedAt = structure.publishedAt();
        withdrawnAt = structure.withdrawnAt();
    }

    AnatomicalStructure domain() {
        return AnatomicalStructure.restore(id, code, type, displayName, sidePolicy,
                externalOntology, externalOntologyId, taxonomyVersion, createdBySubject, createdAt,
                status, publishedAt, withdrawnAt);
    }
}

@Entity(name = "AnatomicalStructureRelationJpaEntity")
@Table(name = "anatomical_structure_relation", schema = "anatomy_reference")
class AnatomicalStructureRelationJpaEntity {
    @Id UUID id;
    @Column(name = "parent_id", nullable = false) UUID parentId;
    @Column(name = "child_id", nullable = false) UUID childId;
    @Enumerated(EnumType.STRING) @Column(name = "relation_type", nullable = false, length = 40) RelationType relationType;
    @Column(name = "created_at", nullable = false, updatable = false) Instant createdAt;
    @Column(name = "created_by_subject", nullable = false, updatable = false, length = 255) String createdBySubject;

    protected AnatomicalStructureRelationJpaEntity() {
    }

    AnatomicalStructureRelationJpaEntity(AnatomicalStructureRelation relation) {
        id = relation.id();
        parentId = relation.parentId();
        childId = relation.childId();
        relationType = relation.relationType();
        createdAt = relation.createdAt();
        createdBySubject = relation.createdBySubject();
    }

    UUID parentId() { return parentId; }
    UUID childId() { return childId; }
    RelationType relationType() { return relationType; }
}

@Entity(name = "HierarchyGuardJpaEntity")
@Table(name = "hierarchy_guard", schema = "anatomy_reference")
class HierarchyGuardJpaEntity {
    @Id short id;

    protected HierarchyGuardJpaEntity() {
    }
}
