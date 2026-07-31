package com.motionecosystem.exercisecatalog;

import org.springframework.http.HttpStatus;

final class ExerciseCatalogSearchProblem extends RuntimeException {
    private final HttpStatus status;
    private final String code;

    ExerciseCatalogSearchProblem(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    HttpStatus status() { return status; }
    String code() { return code; }
}
