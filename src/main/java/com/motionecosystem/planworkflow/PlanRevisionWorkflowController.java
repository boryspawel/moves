package com.motionecosystem.planworkflow;

import com.motionecosystem.planworkflow.PlanRevisionWorkflowService.AcknowledgeWarningCommand;
import com.motionecosystem.planworkflow.PlanRevisionWorkflowService.AcknowledgementView;
import com.motionecosystem.planworkflow.PlanRevisionWorkflowService.ActivateWorkflowCommand;
import com.motionecosystem.planworkflow.PlanRevisionWorkflowService.SafetyBlockException;
import com.motionecosystem.planworkflow.PlanRevisionWorkflowService.ValidateWorkflowCommand;
import com.motionecosystem.planworkflow.PlanRevisionWorkflowService.ValidationView;
import com.motionecosystem.planworkflow.PlanRevisionWorkflowService.WorkflowView;
import com.motionecosystem.trainingplanning.api.PlanRevisionWorkflowPersistence.ActivationOutcome;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import java.net.URI;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestController
@RequestMapping("/api/v2/training-plans/revisions/{revisionId}/workflow")
@SecurityRequirement(name = "oidc")
@RequiredArgsConstructor
class PlanRevisionWorkflowController {

    private final PlanRevisionWorkflowService workflow;

    @PostMapping("/validation")
    @PreAuthorize("hasAnyRole('PARTICIPANT', 'SPECIALIST')")
    @Operation(summary = "Validate load and safety for a plan revision")
    ValidationView validate(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID revisionId,
            @RequestBody ValidateWorkflowCommand command) {
        return workflow.validate(jwt.getSubject(), revisionId, command);
    }

    @PostMapping("/warning-acknowledgements")
    @PreAuthorize("hasAnyRole('PARTICIPANT', 'SPECIALIST')")
    @Operation(summary = "Acknowledge warning factors from the current assessment")
    AcknowledgementView acknowledge(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID revisionId,
            @RequestBody AcknowledgeWarningCommand command) {
        return workflow.acknowledge(jwt.getSubject(), revisionId, command);
    }

    @PostMapping("/activation")
    @PreAuthorize("hasAnyRole('PARTICIPANT', 'SPECIALIST')")
    @Operation(summary = "Activate a validated plan revision")
    ActivationOutcome activate(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID revisionId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestBody ActivateWorkflowCommand command) {
        return workflow.activate(jwt.getSubject(), revisionId, idempotencyKey, command);
    }

    @PostMapping("/status")
    @PreAuthorize("hasAnyRole('PARTICIPANT', 'SPECIALIST')")
    @Operation(summary = "Read plan revision workflow status and current assessment")
    WorkflowView status(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID revisionId,
            @RequestBody ActivateWorkflowCommand command) {
        return workflow.workflow(jwt.getSubject(), revisionId, command.actingContext());
    }
}

@RestControllerAdvice(assignableTypes = PlanRevisionWorkflowController.class)
class PlanRevisionWorkflowProblemAdvice {

    @ExceptionHandler(SafetyBlockException.class)
    ResponseEntity<ProblemDetail> safetyBlock(SafetyBlockException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.CONFLICT, "Plan activation is blocked by the current safety assessment.");
        problem.setTitle("Safety assessment blocks activation");
        problem.setType(URI.create("urn:moves:problem:plan-safety-block"));
        problem.setProperty("explanationCodes", exception.explanationCodes());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(problem);
    }
}
