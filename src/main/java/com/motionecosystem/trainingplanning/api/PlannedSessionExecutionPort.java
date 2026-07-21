package com.motionecosystem.trainingplanning.api;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PlannedSessionExecutionPort {

    Optional<PlannedSessionSnapshot> lockOwnedSession(UUID sessionId, UUID participantAccountId);

    Optional<PlannedSessionSnapshot> findSession(UUID sessionId);

    void markCompleted(UUID sessionId);

    record PlannedSessionSnapshot(UUID id, UUID participantAccountId, SessionState state,
                                  List<PrescriptionSnapshot> prescriptions) {
    }

    record PrescriptionSnapshot(UUID id, UUID exerciseVersionId, int position) {
    }

    enum SessionState { ASSIGNED, COMPLETED, CANCELLED }
}
