package com.motionecosystem.specialist;

import com.motionecosystem.specialist.api.SpecialistAuthorizationPort.ActingContext;
import com.motionecosystem.specialist.api.SpecialistAuthorizationPort.ProfessionalRole;
import com.motionecosystem.specialist.api.SpecialistAuthorizationPort.Purpose;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController @SecurityRequirement(name = "oidc") @RequiredArgsConstructor
public class SpecialistWorklistController {
    private final SpecialistWorklistService worklist;
    @GetMapping("/api/v1/specialist/worklist") @PreAuthorize("hasRole('SPECIALIST')")
    List<SpecialistWorklistService.WorklistItemView> listWorklist(@AuthenticationPrincipal Jwt jwt, @RequestParam ProfessionalRole actingContext, @RequestParam Purpose purpose) { return worklist.list(jwt.getSubject(), new ActingContext(actingContext), purpose); }
    @PostMapping("/api/v1/specialist/worklist/{itemId}/actions") @PreAuthorize("hasRole('SPECIALIST')")
    SpecialistWorklistService.WorklistItemView actOnWorklist(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID itemId, @RequestParam ProfessionalRole actingContext, @RequestParam Purpose purpose, @RequestBody SpecialistWorklistService.ActionCommand command) { return worklist.action(jwt.getSubject(), itemId, new ActingContext(actingContext), purpose, command); }
    @PostMapping("/api/v1/specialist/worklist/{itemId}/reply") @PreAuthorize("hasRole('SPECIALIST')")
    SpecialistWorklistService.ReplyView replyToIssue(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID itemId, @RequestParam ProfessionalRole actingContext, @RequestParam Purpose purpose, @RequestBody SpecialistWorklistService.ReplyCommand command) { return worklist.reply(jwt.getSubject(), itemId, new ActingContext(actingContext), purpose, command); }
    @PostMapping("/api/v1/participant/issues") @PreAuthorize("hasRole('PARTICIPANT')")
    SpecialistWorklistService.WorklistItemView reportParticipantIssue(@AuthenticationPrincipal Jwt jwt, @RequestBody SpecialistWorklistService.ParticipantIssueCommand command) { return worklist.reportIssue(jwt.getSubject(), command); }
}
