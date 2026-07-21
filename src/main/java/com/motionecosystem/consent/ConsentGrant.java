package com.motionecosystem.consent;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import com.motionecosystem.consent.api.ConsentDecisionPort.DataScope;
import com.motionecosystem.consent.api.ConsentDecisionPort.Purpose;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "consent_grant", schema = "consent")
class ConsentGrant {

    enum RecipientType {
        SPECIALIST,
        RELATIONSHIP,
        ORGANIZATION
    }

    enum Status {
        ACTIVE,
        REVOKED,
        EXPIRED
    }

    @Id
    UUID id;

    @Column(name = "grantor_account_id")
    UUID grantorId;

    @Column(name = "participant_account_id")
    UUID participantId;

    @Enumerated(EnumType.STRING)
    @Column(name = "recipient_type")
    RecipientType recipientType;

    @Column(name = "recipient_id")
    UUID recipientId;

    @Enumerated(EnumType.STRING)
    Purpose purpose;

    @Column(name = "template_version_id")
    UUID templateId;

    @Enumerated(EnumType.STRING)
    Status status;

    @Column(name = "granted_at")
    Instant grantedAt;

    @Column(name = "valid_from")
    Instant validFrom;

    @Column(name = "valid_to")
    Instant validTo;

    @Column(name = "revoked_at")
    Instant revokedAt;

    @Version
    long version;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
            name = "consent_grant_scope",
            schema = "consent",
            joinColumns = @JoinColumn(name = "grant_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "data_scope")
    Set<DataScope> scopes = new HashSet<>();

    protected ConsentGrant() {
    }

    ConsentGrant(
            UUID participant,
            RecipientType type,
            UUID recipient,
            Purpose purpose,
            UUID template,
            Set<DataScope> scopes,
            Instant from,
            Instant to,
            Instant now) {
        id = UUID.randomUUID();
        grantorId = participant;
        participantId = participant;
        recipientType = type;
        recipientId = recipient;
        this.purpose = purpose;
        templateId = template;
        this.scopes.addAll(scopes);
        status = Status.ACTIVE;
        grantedAt = now;
        validFrom = from;
        validTo = to;
    }

    boolean activeAt(Instant now) {
        return status == Status.ACTIVE
                && !now.isBefore(validFrom)
                && (validTo == null || !now.isAfter(validTo));
    }

    void revoke(Instant now) {
        if (status == Status.ACTIVE) {
            status = Status.REVOKED;
            revokedAt = now;
        }
    }
}
