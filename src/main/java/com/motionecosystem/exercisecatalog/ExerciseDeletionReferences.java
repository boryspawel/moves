package com.motionecosystem.exercisecatalog;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import org.hibernate.annotations.Immutable;
import org.springframework.data.jpa.repository.JpaRepository;

@Entity
@Immutable
@Table(name = "exercise_relation", schema = "exercise_catalog")
class ExerciseRelationReference {
    @Id UUID id;
    @Column(name = "source_exercise_id", nullable = false) UUID sourceExerciseId;
    @Column(name = "target_exercise_id", nullable = false) UUID targetExerciseId;
}

interface ExerciseRelationReferenceRepository extends JpaRepository<ExerciseRelationReference, UUID> {
    boolean existsBySourceExerciseIdOrTargetExerciseId(UUID sourceExerciseId, UUID targetExerciseId);
}
