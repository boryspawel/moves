package com.motionecosystem.exercisecatalog;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ExerciseCatalogSearchServiceTest {

    @Test
    void foldsPolishCharactersWhitespaceAndCaseDeterministically() {
        assertThat(ExerciseCatalogSearchService.fold("  ŁÓDKA   z  ĆWICZENIEM "))
                .isEqualTo("lodka z cwiczeniem");
        assertThat(ExerciseCatalogSearchService.fold("   ")).isNull();
        assertThat(ExerciseCatalogSearchService.fold(null)).isNull();
    }
}
