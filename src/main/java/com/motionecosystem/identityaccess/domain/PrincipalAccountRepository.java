package com.motionecosystem.identityaccess.domain;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PrincipalAccountRepository extends JpaRepository<PrincipalAccount, UUID> {
    Optional<PrincipalAccount> findByExternalSubject(String externalSubject);

    @Modifying
    @Query("UPDATE PrincipalAccount account SET account.lastSeenAt = :seenAt WHERE account.id = :id")
    void updateLastSeenAt(@Param("id") UUID id, @Param("seenAt") java.time.Instant seenAt);

    @Query("""
            SELECT account FROM PrincipalAccount account
            WHERE account.id = :id
              AND account.status = com.motionecosystem.identityaccess.domain.AccountStatus.ACTIVE
              AND account.profileType = com.motionecosystem.identityaccess.api.ProfileType.PARTICIPANT
            """)
    Optional<PrincipalAccount> findActiveParticipantById(UUID id);
}
