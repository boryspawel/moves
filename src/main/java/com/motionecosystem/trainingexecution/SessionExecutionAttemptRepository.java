package com.motionecosystem.trainingexecution;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface SessionExecutionAttemptRepository extends JpaRepository<SessionExecutionAttempt, UUID> {
    Optional<SessionExecutionAttempt> findFirstByParticipantAccountIdAndPlannedSessionIdOrderByUpdatedAtDesc(
            UUID participantAccountId, UUID plannedSessionId);
    List<SessionExecutionAttempt> findByParticipantAccountIdAndPlannedSessionIdInOrderByUpdatedAtDesc(
            UUID participantAccountId, Collection<UUID> plannedSessionIds);
}
