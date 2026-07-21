package com.motionecosystem.identityaccess.domain;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.motionecosystem.identityaccess.api.ProfileType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PrincipalAccountRepository extends JpaRepository<PrincipalAccount, UUID> {
    Optional<PrincipalAccount> findByExternalSubject(String externalSubject);

    @Modifying
    @Query("UPDATE PrincipalAccount account SET account.lastSeenAt = :seenAt WHERE account.id = :id")
    void updateLastSeenAt(@Param("id") UUID id, @Param("seenAt") java.time.Instant seenAt);

    @Query(value = """
            SELECT account.* FROM identity_access.principal_account account
            WHERE account.id = :id AND account.status = 'ACTIVE'
              AND (EXISTS (SELECT 1 FROM identity_access.account_domain_profile profile
                           WHERE profile.account_id = account.id
                             AND profile.profile_type = 'PARTICIPANT'
                             AND profile.status = 'ACTIVE')
                   OR account.profile_type = 'PARTICIPANT')
            """, nativeQuery = true)
    Optional<PrincipalAccount> findActiveParticipantById(UUID id);

    @Query(value = """
            SELECT profile_type FROM identity_access.account_domain_profile
            WHERE account_id = :accountId AND status = 'ACTIVE'
            """, nativeQuery = true)
    Set<ProfileType> findActiveProfileTypes(UUID accountId);

    @Modifying
    @Query(value = """
            INSERT INTO identity_access.account_domain_profile
                (account_id, profile_type, status, created_at)
            VALUES (:accountId, :#{#profileType.name()}, 'ACTIVE', :createdAt)
            ON CONFLICT (account_id, profile_type)
            DO UPDATE SET status = 'ACTIVE'
            """, nativeQuery = true)
    void activateProfile(UUID accountId, ProfileType profileType, java.time.Instant createdAt);
}
