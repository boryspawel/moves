package com.motionecosystem.exercisesets.domain;

import java.util.List;
import java.util.UUID;
import com.motionecosystem.exercisesets.domain.ExerciseSetModel.VersionStatus;

/** Framework-free structural invariants shared by all persistence adapters. */
public final class ExerciseSetAggregate {
    private ExerciseSetAggregate() { }
    public static void requireDraft(VersionStatus status) { if (status != VersionStatus.DRAFT) throw new IllegalStateException("only a draft can be edited"); }
    public static void requireContiguousPositions(List<Integer> positions) { for (int index = 0; index < positions.size(); index++) if (positions.get(index) == null || positions.get(index) != index + 1) throw new IllegalStateException("item positions must be contiguous"); }
    public static void requireVariantAcyclic(UUID candidate, UUID source, List<UUID> ancestry) { if (candidate.equals(source) || ancestry.contains(candidate)) throw new IllegalStateException("variant provenance must not contain a cycle"); }
}
