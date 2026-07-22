package com.motionecosystem.trainingplanning.api;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Historical, revision-independent planned windows for adherence detection. */
public interface ParticipantPlanWindowHistoryQueryPort {
    List<PlanWindow> completedWindows(UUID participantAccountId, Instant before, int limit);
    List<UUID> participantsWithCompletedWindows(Instant before, int limit, int offset);
    record PlanWindow(UUID plannedSessionId, UUID planRevisionId, Instant endedAt, String status) { }
}
