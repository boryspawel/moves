package com.motionecosystem.exercisecatalog;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class ExerciseReviewQueryServiceTest {

    @Test
    void preservesPublicSortTokensAsTypedRepositorySorts() {
        assertThat(ExerciseReviewQueryService.orderBy("newest")).isEqualTo(ExerciseReviewQueueSort.NEWEST);
        assertThat(ExerciseReviewQueryService.orderBy("name")).isEqualTo(ExerciseReviewQueueSort.NAME);
        assertThat(ExerciseReviewQueryService.orderBy("action-needed")).isEqualTo(ExerciseReviewQueueSort.ACTION_NEEDED);
    }

    @Test
    void diffExposesSemanticHashesWithoutTheNormalizedImportPayload() {
        ExerciseEditorialWorkflowService.VersionDiff diff = new ExerciseEditorialWorkflowService.VersionDiff(
                UUID.randomUUID(), UUID.randomUUID(), 3, "APPROVED", "draft-hash", "published-hash");

        assertThat(diff.draftSemanticSha256()).isEqualTo("draft-hash");
        assertThat(diff.currentPublishedSemanticSha256()).isEqualTo("published-hash");
        assertThat(ExerciseEditorialWorkflowService.VersionDiff.class.getRecordComponents())
                .extracting(component -> component.getName()).doesNotContain("normalizedSource");
    }
}
