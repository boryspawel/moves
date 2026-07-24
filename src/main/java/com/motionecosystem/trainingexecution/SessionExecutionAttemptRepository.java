package com.motionecosystem.trainingexecution;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface SessionExecutionAttemptRepository extends JpaRepository<SessionExecutionAttempt, UUID> {
    Optional<SessionExecutionAttempt> findFirstByParticipantAccountIdAndPlannedSessionIdOrderByUpdatedAtDesc(
            UUID participantAccountId, UUID plannedSessionId);
    Optional<SessionExecutionAttempt> findByParticipantAccountIdAndStartIdempotencyKey(
            UUID participantAccountId, String startIdempotencyKey);
    List<SessionExecutionAttempt> findByParticipantAccountIdAndStartedAtGreaterThanEqualAndStartedAtLessThanOrderByStartedAtDesc(UUID participantAccountId, java.time.Instant fromInclusive, java.time.Instant toExclusive, org.springframework.data.domain.Pageable pageable);
    List<SessionExecutionAttempt> findByParticipantAccountIdAndPlannedSessionIdInOrderByUpdatedAtDesc(
            UUID participantAccountId, Collection<UUID> plannedSessionIds);
    @Query("""
            select attempt from SessionExecutionAttempt attempt
            where attempt.participantAccountId = :participant
              and coalesce(attempt.completedAt, attempt.abandonedAt, attempt.startedAt) >= :from
              and coalesce(attempt.completedAt, attempt.abandonedAt, attempt.startedAt) < :to
            order by coalesce(attempt.completedAt, attempt.abandonedAt, attempt.startedAt) desc, attempt.startedAt desc, attempt.id asc
            """)
    List<SessionExecutionAttempt> findTimelineInitial(@Param("participant") UUID participant, @Param("from") java.time.Instant from,
            @Param("to") java.time.Instant to, org.springframework.data.domain.Pageable pageable);
    @Query("""
            select attempt from SessionExecutionAttempt attempt
            where attempt.participantAccountId = :participant
              and coalesce(attempt.completedAt, attempt.abandonedAt, attempt.startedAt) >= :from
              and coalesce(attempt.completedAt, attempt.abandonedAt, attempt.startedAt) < :to
              and (coalesce(attempt.completedAt, attempt.abandonedAt, attempt.startedAt) < :effective
                or (coalesce(attempt.completedAt, attempt.abandonedAt, attempt.startedAt) = :effective
                  and (attempt.startedAt < :recorded or (attempt.startedAt = :recorded and attempt.id > :id))))
            order by coalesce(attempt.completedAt, attempt.abandonedAt, attempt.startedAt) desc, attempt.startedAt desc, attempt.id asc
            """)
    List<SessionExecutionAttempt> findTimelineAfter(@Param("participant") UUID participant, @Param("from") java.time.Instant from,
            @Param("to") java.time.Instant to, @Param("effective") java.time.Instant effective,
            @Param("recorded") java.time.Instant recorded, @Param("id") UUID id,
            org.springframework.data.domain.Pageable pageable);
}
