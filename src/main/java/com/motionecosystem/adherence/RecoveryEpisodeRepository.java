package com.motionecosystem.adherence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface RecoveryEpisodeRepository extends JpaRepository<RecoveryEpisode, UUID> {
    Optional<RecoveryEpisode> findFirstByParticipantAccountIdAndStatusInOrderByOpenedAtDesc(UUID participant, java.util.Collection<String> states);
    Optional<RecoveryEpisode> findByIdAndParticipantAccountId(UUID id, UUID participant);
    Optional<RecoveryEpisode> findFirstByParticipantAccountIdAndReturnAttemptId(UUID participant, UUID attemptId);
    Optional<RecoveryEpisode> findFirstByParticipantAccountIdAndTargetPlannedSessionIdAndStatusIn(UUID participant, UUID sessionId, java.util.Collection<String> states);
}
