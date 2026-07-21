package com.motionecosystem.exercisecatalog.api;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface ReviewExerciseVersion {
    ReviewResult review(UUID versionId, String actorSubject, ReviewCommand command);

    record ReviewCommand(String area, String decision, String comment, Long expectedVersion) {
    }
    record ReviewItem(UUID id, String area, String decision, String comment,
                      String reviewerSubject, Instant reviewedAt, long version) {
    }
    record ReviewResult(UUID exerciseVersionId, String status, long version,
                        List<ReviewItem> reviews, List<String> unmetRequirements) {
    }
}
