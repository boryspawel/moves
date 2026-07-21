package com.motionecosystem.identityaccess.domain;

import java.time.Instant;
import java.util.UUID;

import com.motionecosystem.identityaccess.api.ProfileType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "principal_account", schema = "identity_access")
public class PrincipalAccount {

    @Id
    private UUID id;

    @Column(name = "external_subject", nullable = false, unique = true)
    private String externalSubject;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AccountStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "profile_type")
    private ProfileType profileType;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "last_seen_at")
    private Instant lastSeenAt;

    @Version
    private long version;

    protected PrincipalAccount() {
    }

    public static PrincipalAccount create(String subject, Instant now) {
        PrincipalAccount account = new PrincipalAccount();
        account.id = UUID.randomUUID();
        account.externalSubject = subject;
        account.status = AccountStatus.ACTIVE;
        account.createdAt = now;
        account.lastSeenAt = now;
        return account;
    }

    public boolean isActive() {
        return status == AccountStatus.ACTIVE;
    }

    public void seenAt(Instant now) {
        lastSeenAt = now;
    }

    public void selectProfileType(ProfileType selected) {
        if (profileType == null) {
            profileType = selected;
        }
    }

    public UUID id() {
        return id;
    }

    public String externalSubject() {
        return externalSubject;
    }

    public ProfileType profileType() {
        return profileType;
    }
}
