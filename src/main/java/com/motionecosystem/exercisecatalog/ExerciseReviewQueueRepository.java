package com.motionecosystem.exercisecatalog;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface ExerciseReviewQueueRepository extends JpaRepository<ExerciseVersion, UUID>, ExerciseReviewQueueRepositoryCustom {
}

interface ExerciseReviewQueueRepositoryCustom {
    ExerciseReviewQueryService.QueuePage findQueue(ExerciseReviewQueueFilter filter);
}

record ExerciseReviewQueueFilter(UUID batchId, String namePattern, ExerciseVersionStatus status,
                                 Boolean readyToPublish, Boolean actionNeeded, Boolean hasErrors,
                                 Boolean hasBlockers, String missingReviewArea, ExerciseReviewQueueSort sort, int page, int size) {
}

enum ExerciseReviewQueueSort {
    ACTION_NEEDED,
    NEWEST,
    NAME
}
