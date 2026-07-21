package com.motionecosystem.consent;

import java.time.Instant;
import java.util.UUID;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(
        name = "consent_template_version",
        schema = "consent",
        uniqueConstraints = @UniqueConstraint(columnNames = {"template_code", "version_number"}))
class ConsentTemplateVersion {

    enum Status {
        DRAFT,
        PUBLISHED,
        WITHDRAWN
    }

    @Id
    UUID id;

    @Column(name = "template_code")
    String code;

    @Column(name = "version_number")
    int number;

    @Column(name = "content_reference")
    String contentReference;

    @Column(name = "legal_basis")
    String legalBasis;

    @Enumerated(EnumType.STRING)
    Status status;

    @Column(name = "published_at")
    Instant publishedAt;

    @Column(name = "created_at")
    Instant createdAt;

    protected ConsentTemplateVersion() {
    }

    ConsentTemplateVersion(String code, int number, String reference, String basis, Instant now) {
        id = UUID.randomUUID();
        this.code = code;
        this.number = number;
        contentReference = reference;
        legalBasis = basis;
        status = Status.PUBLISHED;
        publishedAt = now;
        createdAt = now;
    }
}
