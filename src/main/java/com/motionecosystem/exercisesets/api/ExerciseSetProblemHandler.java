package com.motionecosystem.exercisesets.api;

import java.net.URI;
import com.motionecosystem.exercisesets.infrastructure.ExerciseSetService.ExerciseSetProblem;
import jakarta.persistence.OptimisticLockException;
import org.springframework.http.ProblemDetail;
import org.springframework.http.HttpStatus;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes=ExerciseSetController.class)
class ExerciseSetProblemHandler {
    @ExceptionHandler(ExerciseSetProblem.class)
    ProblemDetail handle(ExerciseSetProblem exception) {
        ProblemDetail detail=ProblemDetail.forStatusAndDetail(exception.getStatusCode(),exception.getReason());
        detail.setType(URI.create("urn:moves:exercise-set:"+exception.code)); detail.setProperty("code",exception.code);
        if(exception.field!=null) detail.setProperty("field",exception.field); return detail;
    }

    @ExceptionHandler({ObjectOptimisticLockingFailureException.class, OptimisticLockException.class})
    ProblemDetail optimisticLock() {
        ProblemDetail detail=ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,"draft was changed by another request");
        detail.setType(URI.create("urn:moves:exercise-set:OPTIMISTIC_LOCK")); detail.setProperty("code","OPTIMISTIC_LOCK");
        detail.setProperty("field","expectedVersion"); return detail;
    }
}
