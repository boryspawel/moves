package com.motionecosystem.exercisecatalog;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

interface ExerciseContributionRepository extends JpaRepository<ExerciseContribution, UUID> {
    List<ExerciseContribution> findByExerciseVersionIdOrderById(UUID exerciseVersionId);
    List<ExerciseContribution> findByExerciseVersionIdIn(Set<UUID> exerciseVersionIds);
}

interface ExerciseLoadCharacteristicRepository extends JpaRepository<ExerciseLoadCharacteristic, UUID> {
    List<ExerciseLoadCharacteristic> findByExerciseVersionIdOrderById(UUID exerciseVersionId);
    List<ExerciseLoadCharacteristic> findByExerciseVersionIdIn(Set<UUID> exerciseVersionIds);
}

interface EvidenceSourceRepository extends JpaRepository<EvidenceSource, UUID> {
    List<EvidenceSource> findByExerciseVersionIdOrderById(UUID exerciseVersionId);
    List<EvidenceSource> findByExerciseVersionIdIn(Set<UUID> exerciseVersionIds);
    List<EvidenceSource> findByExerciseVersionIdAndIdIn(UUID exerciseVersionId, Set<UUID> ids);
}

interface ExerciseContributionEvidenceRepository extends JpaRepository<ExerciseContributionEvidence, UUID> {
    List<ExerciseContributionEvidence> findByContributionIdIn(Set<UUID> contributionIds);
}
