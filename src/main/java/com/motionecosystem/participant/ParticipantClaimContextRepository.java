package com.motionecosystem.participant;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

interface ParticipantClaimContextRepository extends JpaRepository<ParticipantClaimContext, UUID> {
    Optional<ParticipantClaimContext> findBySecretHash(String secretHash);

    @Query("select context.invitationId as invitationId from ParticipantClaimContext context where context.secretHash = :secretHash")
    Optional<ContextPointer> findPointerBySecretHash(String secretHash);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select context from ParticipantClaimContext context where context.secretHash = :secretHash")
    Optional<ParticipantClaimContext> lockBySecretHash(String secretHash);

    void deleteByInvitationId(UUID invitationId);

    interface ContextPointer { UUID getInvitationId(); }
}
