package com.motionecosystem.exercisecatalog;

import java.net.URI;

import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = ExerciseCatalogSearchController.class)
class ExerciseCatalogSearchProblemHandler {
    @ExceptionHandler(ExerciseCatalogSearchProblem.class)
    ProblemDetail handle(ExerciseCatalogSearchProblem exception) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(exception.status(), exception.getMessage());
        detail.setType(URI.create("urn:moves:exercise-catalog-search:" + exception.code().toLowerCase()));
        detail.setProperty("code", exception.code());
        return detail;
    }
}
