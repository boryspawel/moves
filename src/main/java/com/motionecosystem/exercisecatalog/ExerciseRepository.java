package com.motionecosystem.exercisecatalog;

import java.util.UUID;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface ExerciseRepository extends JpaRepository<Exercise, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT exercise FROM Exercise exercise WHERE exercise.id = :id")
    java.util.Optional<Exercise> findLockedById(@Param("id") UUID id);
}
