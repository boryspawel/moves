package com.motionecosystem.trainingexecution;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface SessionExecutionAttemptProgressRepository extends JpaRepository<SessionExecutionAttemptProgress, UUID> {
    Optional<SessionExecutionAttemptProgress> findByAttemptIdAndExercisePrescriptionId(UUID attemptId, UUID exercisePrescriptionId);
    List<SessionExecutionAttemptProgress> findByAttemptIdOrderByUpdatedAt(UUID attemptId);
}
