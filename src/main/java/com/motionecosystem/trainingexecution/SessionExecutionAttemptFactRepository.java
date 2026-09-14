package com.motionecosystem.trainingexecution;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface SessionExecutionAttemptFactRepository extends JpaRepository<SessionExecutionAttemptFact, UUID> {
    List<SessionExecutionAttemptFact> findByAttemptIdOrderByExercisePrescriptionIdAscRevisionNumberDesc(UUID attemptId);
    Optional<SessionExecutionAttemptFact> findFirstByAttemptIdAndExercisePrescriptionIdOrderByRevisionNumberDesc(UUID attemptId, UUID prescriptionId);
}
