package com.motionecosystem.anatomyreference.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * A taxonomy entry assigned to one positive taxonomy release number.
 * Once published, its semantic fields and relations cannot change; a correction is represented
 * by a new structure with a new stable code in a later taxonomy release.
 */
public final class AnatomicalStructure {

    private final UUID id;
    private final String code;
    private final AnatomicalStructureType type;
    private final String displayName;
    private final SidePolicy sidePolicy;
    private final String externalOntology;
    private final String externalOntologyId;
    private final int taxonomyVersion;
    private final String createdBySubject;
    private final Instant createdAt;
    private PublicationStatus status;
    private Instant publishedAt;
    private Instant withdrawnAt;

    private AnatomicalStructure(UUID id, String code, AnatomicalStructureType type,
                                String displayName, SidePolicy sidePolicy,
                                String externalOntology, String externalOntologyId,
                                int taxonomyVersion, String createdBySubject, Instant createdAt,
                                PublicationStatus status, Instant publishedAt, Instant withdrawnAt) {
        this.id = Objects.requireNonNull(id, "id");
        this.code = required(code, 80, "code");
        this.type = Objects.requireNonNull(type, "type");
        this.displayName = required(displayName, 160, "displayName");
        this.sidePolicy = Objects.requireNonNull(sidePolicy, "sidePolicy");
        if ((externalOntology == null) != (externalOntologyId == null)) {
            throw new IllegalArgumentException("external ontology and identifier must be provided together");
        }
        this.externalOntology = optional(externalOntology, 120, "externalOntology");
        this.externalOntologyId = optional(externalOntologyId, 200, "externalOntologyId");
        if (taxonomyVersion <= 0) {
            throw new IllegalArgumentException("taxonomyVersion must be positive");
        }
        this.taxonomyVersion = taxonomyVersion;
        this.createdBySubject = required(createdBySubject, 255, "createdBySubject");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.status = Objects.requireNonNull(status, "status");
        this.publishedAt = publishedAt;
        this.withdrawnAt = withdrawnAt;
    }

    public static AnatomicalStructure draft(UUID id, String code, AnatomicalStructureType type,
                                            String displayName, SidePolicy sidePolicy,
                                            String externalOntology, String externalOntologyId,
                                            int taxonomyVersion, String createdBySubject, Instant createdAt) {
        return new AnatomicalStructure(id, code, type, displayName, sidePolicy,
                externalOntology, externalOntologyId, taxonomyVersion, createdBySubject, createdAt,
                PublicationStatus.DRAFT, null, null);
    }

    public static AnatomicalStructure restore(UUID id, String code, AnatomicalStructureType type,
                                              String displayName, SidePolicy sidePolicy,
                                              String externalOntology, String externalOntologyId,
                                              int taxonomyVersion, String createdBySubject, Instant createdAt,
                                              PublicationStatus status, Instant publishedAt, Instant withdrawnAt) {
        return new AnatomicalStructure(id, code, type, displayName, sidePolicy,
                externalOntology, externalOntologyId, taxonomyVersion, createdBySubject, createdAt,
                status, publishedAt, withdrawnAt);
    }

    public void publish(Instant now) {
        requireDraft();
        status = PublicationStatus.PUBLISHED;
        publishedAt = Objects.requireNonNull(now, "now");
    }

    public void withdraw(Instant now) {
        if (status != PublicationStatus.PUBLISHED) {
            throw new IllegalStateException("only a published anatomical structure can be withdrawn");
        }
        status = PublicationStatus.WITHDRAWN;
        withdrawnAt = Objects.requireNonNull(now, "now");
    }

    public void requireRelationMutable() {
        requireDraft();
    }

    private void requireDraft() {
        if (status != PublicationStatus.DRAFT) {
            throw new IllegalStateException("published or withdrawn anatomical structures are semantically immutable");
        }
    }

    private static String required(String value, int max, String field) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty() || normalized.length() > max) {
            throw new IllegalArgumentException(field + " is required and must not exceed " + max + " characters");
        }
        return normalized;
    }

    private static String optional(String value, int max, String field) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return required(value, max, field);
    }

    public UUID id() { return id; }
    public String code() { return code; }
    public AnatomicalStructureType type() { return type; }
    public String displayName() { return displayName; }
    public SidePolicy sidePolicy() { return sidePolicy; }
    public PublicationStatus status() { return status; }
    public String externalOntology() { return externalOntology; }
    public String externalOntologyId() { return externalOntologyId; }
    public int taxonomyVersion() { return taxonomyVersion; }
    public String createdBySubject() { return createdBySubject; }
    public Instant createdAt() { return createdAt; }
    public Instant publishedAt() { return publishedAt; }
    public Instant withdrawnAt() { return withdrawnAt; }
}
