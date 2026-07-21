package com.motionecosystem.exercisecatalog.api;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface PublishExerciseVersion {
    PublicationResult publish(UUID versionId, String actorSubject, Long expectedVersion);

    record PublicationResult(UUID exerciseVersionId, String status, Instant publishedAt,
                             long version, List<String> unmetRequirements) {
    }
}
