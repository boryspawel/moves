package com.motionecosystem.trainingplanning.api;

import com.motionecosystem.identityaccess.api.SpecialistAuthorizationPort.ActingContext;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Owner-authorized, metadata-only planned-session facts for encounter-history composition. */
public interface SpecialistPlanningHistoryQueryPort {
    List<SessionSnapshot> sessionCandidates(String subject, UUID participantId, ActingContext context,
                                            LocalDate fromInclusive, LocalDate toExclusive);

    List<SessionSnapshot> sessionsByIds(String subject, UUID participantId, ActingContext context, Set<UUID> sessionIds);

    Set<UUID> visibleSessionIds(String subject, UUID participantId, ActingContext context, Set<UUID> sessionIds);

    record SessionSnapshot(UUID sessionId, UUID participantId, UUID planId, UUID revisionId,
                           String status, String kind, String name, LocalDate scheduledDate,
                           Instant availableFrom, Instant availableTo) { }
}
