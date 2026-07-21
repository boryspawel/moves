package com.motionecosystem.anatomyreference.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record AnatomicalStructureRelation(UUID id, UUID parentId, UUID childId,
                                          RelationType relationType, Instant createdAt,
                                          String createdBySubject) {
    public AnatomicalStructureRelation {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(parentId, "parentId");
        Objects.requireNonNull(childId, "childId");
        Objects.requireNonNull(relationType, "relationType");
        Objects.requireNonNull(createdAt, "createdAt");
        if (parentId.equals(childId)) {
            throw new IllegalArgumentException("an anatomical structure cannot be related to itself");
        }
        String normalized = createdBySubject == null ? "" : createdBySubject.trim();
        if (normalized.isEmpty() || normalized.length() > 255) {
            throw new IllegalArgumentException("createdBySubject is required and must not exceed 255 characters");
        }
        createdBySubject = normalized;
    }
}
