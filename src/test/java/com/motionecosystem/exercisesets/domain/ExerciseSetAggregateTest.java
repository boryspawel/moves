package com.motionecosystem.exercisesets.domain;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.UUID;
import com.motionecosystem.exercisesets.domain.ExerciseSetModel.VersionStatus;
import org.junit.jupiter.api.Test;

class ExerciseSetAggregateTest {
    @Test void onlyDraftCanBeEdited() { ExerciseSetAggregate.requireDraft(VersionStatus.DRAFT); assertThrows(IllegalStateException.class, () -> ExerciseSetAggregate.requireDraft(VersionStatus.PUBLISHED)); assertThrows(IllegalStateException.class, () -> ExerciseSetAggregate.requireDraft(VersionStatus.RETIRED)); }
    @Test void positionsAreStrictlyContiguousAndDeterministic() { ExerciseSetAggregate.requireContiguousPositions(List.of(1,2,3)); assertThrows(IllegalStateException.class, () -> ExerciseSetAggregate.requireContiguousPositions(List.of(1,3))); assertThrows(IllegalStateException.class, () -> ExerciseSetAggregate.requireContiguousPositions(List.of(0,1))); }
    @Test void variantCannotReferenceItselfOrCreateAnAncestorCycle() { UUID candidate=UUID.randomUUID(); UUID source=UUID.randomUUID(); ExerciseSetAggregate.requireVariantAcyclic(candidate,source,List.of(source)); assertThrows(IllegalStateException.class, () -> ExerciseSetAggregate.requireVariantAcyclic(candidate,candidate,List.of())); assertThrows(IllegalStateException.class, () -> ExerciseSetAggregate.requireVariantAcyclic(candidate,source,List.of(candidate))); }
}
