package com.motionecosystem.participant;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

interface ParticipantAccessInvitationRepository extends JpaRepository<ParticipantAccessInvitation, UUID> {
    Optional<ParticipantAccessInvitation> findByTokenHash(String tokenHash);
    Optional<ParticipantAccessInvitation> findByParticipantIdAndStatus(UUID participantId, ParticipantAccessInvitation.Status status);
    Optional<ParticipantAccessInvitation> findTopByParticipantIdOrderByCreatedAtDesc(UUID participantId);

    @Query("select invitation.id as id, invitation.participantId as participantId from ParticipantAccessInvitation invitation where invitation.tokenHash = :tokenHash")
    Optional<InvitationPointer> findPointerByTokenHash(String tokenHash);

    @Query("select invitation.id as id, invitation.participantId as participantId from ParticipantAccessInvitation invitation where invitation.id = :id")
    Optional<InvitationPointer> findPointerById(UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select invitation from ParticipantAccessInvitation invitation where invitation.tokenHash = :tokenHash")
    Optional<ParticipantAccessInvitation> lockByTokenHash(String tokenHash);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select invitation from ParticipantAccessInvitation invitation where invitation.id = :id")
    Optional<ParticipantAccessInvitation> lockById(UUID id);

    interface InvitationPointer { UUID getId(); UUID getParticipantId(); }
}
