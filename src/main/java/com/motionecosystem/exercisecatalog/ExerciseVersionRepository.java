package com.motionecosystem.exercisecatalog;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

interface ExerciseVersionRepository extends JpaRepository<ExerciseVersion, UUID> {
    List<ExerciseVersion> findByStatus(ExerciseVersionStatus status);
    List<ExerciseVersion> findByExerciseIdOrderByVersionNumber(UUID exerciseId);
    Optional<ExerciseVersion> findFirstByExerciseIdOrderByVersionNumberDesc(UUID exerciseId);
}
