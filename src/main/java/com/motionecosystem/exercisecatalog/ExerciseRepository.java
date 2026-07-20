package com.motionecosystem.exercisecatalog;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

interface ExerciseRepository extends JpaRepository<Exercise, UUID> {
}
