package com.motionecosystem.trainingplanning;

import com.motionecosystem.trainingplanning.TrainingPlanningV2Service.EditorView;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Practical plan editing that resolves the default planning stage on the server. */
@RestController
@RequestMapping("/api/v2/training-plans/{planId}/revisions/{revisionId}")
@RequiredArgsConstructor
class PracticalPlanResourceController {
    private final SpecialistPlanFacadeService plans;

    @PostMapping("/sessions")
    @PreAuthorize("hasAnyRole('PARTICIPANT', 'SPECIALIST')")
    EditorView addSession(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID planId, @PathVariable UUID revisionId,
                          @RequestBody SpecialistPlanFacadeService.SessionCommand command) {
        return plans.addSession(jwt.getSubject(), planId, revisionId, command);
    }

    @PutMapping("/sessions")
    @PreAuthorize("hasAnyRole('PARTICIPANT', 'SPECIALIST')")
    EditorView updateSession(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID planId, @PathVariable UUID revisionId,
                             @RequestBody SpecialistPlanFacadeService.SessionUpdateCommand command) {
        return plans.updateSession(jwt.getSubject(), planId, revisionId, command);
    }

    @DeleteMapping("/sessions/{sessionId}")
    @PreAuthorize("hasAnyRole('PARTICIPANT', 'SPECIALIST')")
    EditorView deleteSession(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID planId, @PathVariable UUID revisionId,
                             @PathVariable UUID sessionId, @RequestParam long expectedVersion) {
        return plans.deleteSession(jwt.getSubject(), planId, revisionId, expectedVersion, sessionId);
    }

    @PutMapping("/period")
    @PreAuthorize("hasAnyRole('PARTICIPANT', 'SPECIALIST')")
    EditorView updatePeriod(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID planId, @PathVariable UUID revisionId,
                            @RequestBody SpecialistPlanFacadeService.PeriodCommand command) {
        return plans.updatePeriod(jwt.getSubject(), planId, revisionId, command);
    }
}
