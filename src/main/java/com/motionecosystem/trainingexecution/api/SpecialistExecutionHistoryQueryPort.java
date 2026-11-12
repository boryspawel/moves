package com.motionecosystem.trainingexecution.api;

import com.motionecosystem.identityaccess.api.SpecialistAuthorizationPort.ActingContext;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Owner-authorized, provenance-preserving execution declarations for encounter history. */
public interface SpecialistExecutionHistoryQueryPort {
    List<ExecutionFact> executionCandidates(String subject, UUID participantId, ActingContext context,
                                            Instant fromInclusive, Instant toExclusive);

    List<ExecutionFact> executionsByPlannedSessionIds(String subject, UUID participantId, ActingContext context,
                                                       Set<UUID> plannedSessionIds);

    List<ExecutionFact> executionsByIds(String subject, UUID participantId, ActingContext context,
                                         Set<UUID> executionIds);

    List<ExecutionLink> executionLinksByPlannedSessionIds(String subject, UUID participantId, ActingContext context,
                                                           Set<UUID> plannedSessionIds);

    record ExecutionFact(UUID id, UUID participantId, UUID plannedSessionId, UUID attemptId,
                         String recordingSource, UUID recorderAccountId, String outcome, Instant recordedAt,
                         boolean declaredCompletion, long resultCount) { }

    record ExecutionLink(UUID id, UUID participantId, UUID plannedSessionId, UUID recorderAccountId,
                         String recordingSource) { }
}
