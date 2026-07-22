package com.motionecosystem.trainingexecution;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface SessionExecutionAttemptRepository extends JpaRepository<SessionExecutionAttempt, UUID> {
    Optional<SessionExecutionAttempt> findFirstByParticipantAccountIdAndPlannedSessionIdOrderByUpdatedAtDesc(
            UUID participantAccountId, UUID plannedSessionId);
    Optional<SessionExecutionAttempt> findByParticipantAccountIdAndStartIdempotencyKey(
            UUID participantAccountId, String startIdempotencyKey);
    List<SessionExecutionAttempt> findByParticipantAccountIdAndStartedAtGreaterThanEqualAndStartedAtLessThanOrderByStartedAtDesc(UUID participantAccountId, java.time.Instant fromInclusive, java.time.Instant toExclusive, org.springframework.data.domain.Pageable pageable);
    List<SessionExecutionAttempt> findByParticipantAccountIdAndPlannedSessionIdInOrderByUpdatedAtDesc(
            UUID participantAccountId, Collection<UUID> plannedSessionIds);
}
