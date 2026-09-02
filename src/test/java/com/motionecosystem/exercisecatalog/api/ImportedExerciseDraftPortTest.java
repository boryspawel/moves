package com.motionecosystem.exercisecatalog.api;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ImportedExerciseDraftPortTest {
    @Test
    void copiesCollectionInputsDefensively() {
        var purposes = new ArrayList<>(List.of("STRENGTH"));
        var draft = draft(purposes);

        purposes.add("MOBILITY");

        assertThat(draft.purposes()).containsExactly("STRENGTH");
        assertThatThrownBy(() -> draft.purposes().add("MOBILITY"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void rejectsNullCollectionsAndElements() {
        assertThatThrownBy(() -> draft(null)).isInstanceOf(NullPointerException.class)
                .hasMessage("purposes must not be null");
        assertThatThrownBy(() -> draft(java.util.Arrays.asList("STRENGTH", null)))
                .isInstanceOf(NullPointerException.class);
    }

    private ImportedExerciseDraftPort.ImportedExerciseDraft draft(List<String> purposes) {
        return new ImportedExerciseDraftPort.ImportedExerciseDraft(null, UUID.randomUUID(), UUID.randomUUID(),
                "importer", "Squat", "en", "hash", "Brace and squat", "SQUAT", "STRENGTH",
                "MODERATE", "BEGINNER", "GYM", purposes, List.of(), List.of("SQUAT"), List.of(),
                List.of(), List.of(), List.of(), List.of(), List.of());
    }
}
