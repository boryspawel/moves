package com.motionecosystem.participant;

import com.motionecosystem.identityaccess.api.SpecialistAuthorizationPort.ActingContext;
import com.motionecosystem.identityaccess.api.SpecialistAuthorizationPort.ProfessionalRole;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Clock;
import java.time.Duration;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequiredArgsConstructor
class ParticipantAccessInvitationController {
    private static final String COOKIE = "participant_claim_context";
    private final ParticipantAccessInvitationService invitations;
    private final Clock clock;
    @Value("${participant-access.trusted-frontend-url:https://localhost:4200}") private String trustedFrontendUrl;
    @Value("${participant-access.cookie-secure:true}") private boolean cookieSecure;

    @Operation(operationId = "issueParticipantAccessInvitation")
    @PostMapping("/api/v1/specialist/clients/{participantId}/access-invitations") @PreAuthorize("hasRole('SPECIALIST')")
    ParticipantAccessInvitationService.IssueView issue(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID participantId, @RequestBody IssueCommand command) {
        return invitations.issue(jwt.getSubject(), participantId, command.intendedEmail(), command.actingContext());
    }
    @Operation(operationId = "reissueParticipantAccessInvitation")
    @PostMapping("/api/v1/specialist/clients/{participantId}/access-invitations/reissue") @PreAuthorize("hasRole('SPECIALIST')")
    ParticipantAccessInvitationService.IssueView reissue(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID participantId, @RequestBody IssueCommand command) { return invitations.reissue(jwt.getSubject(), participantId, command.intendedEmail(), command.actingContext()); }
    @Operation(operationId = "revokeParticipantAccessInvitation")
    @DeleteMapping("/api/v1/specialist/clients/{participantId}/access-invitations") @PreAuthorize("hasRole('SPECIALIST')")
    ResponseEntity<Void> revoke(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID participantId, @RequestBody RevokeCommand command) { invitations.revoke(jwt.getSubject(), participantId, command.actingContext()); return ResponseEntity.noContent().build(); }
    @Operation(operationId = "readParticipantAccessInvitationStatus")
    @GetMapping("/api/v1/specialist/clients/{participantId}/access-invitations") @PreAuthorize("hasRole('SPECIALIST')")
    ParticipantAccessInvitationService.StatusView invitationStatus(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID participantId,
            @RequestParam ProfessionalRole role) {
        return invitations.status(jwt.getSubject(), participantId, new ActingContext(role));
    }
    @Operation(operationId = "bootstrapParticipantAccessClaim")
    @PostMapping("/api/v1/participant-access/context")
    ResponseEntity<ContextStatus> bootstrap(HttpServletRequest request, @RequestBody BootstrapCommand command) {
        requireTrustedOrigin(request); var context = invitations.bootstrap(command.token());
        return ResponseEntity.ok().header(HttpHeaders.SET_COOKIE, cookie(context.secret(), context.expiresAt()).toString()).body(new ContextStatus(context.status()));
    }
    @Operation(operationId = "readParticipantAccessClaimContext")
    @GetMapping("/api/v1/participant-access/context")
    ContextStatus status(@CookieValue(value = COOKIE, required = false) String secret) {
        if (secret == null || secret.isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid claim context");
        return new ContextStatus(invitations.contextStatus(secret).status());
    }
    @Operation(operationId = "claimParticipantAccess")
    @PostMapping("/api/v1/participant-access/claim")
    ResponseEntity<ParticipantAccessInvitationService.ClaimView> claim(HttpServletRequest request, @AuthenticationPrincipal Jwt jwt, @CookieValue(value = COOKIE, required = false) String secret) {
        requireTrustedOrigin(request); if (secret == null || secret.isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid claim context");
        if (!Boolean.TRUE.equals(jwt.getClaim("email_verified"))) throw new ParticipantInvitationProblem(HttpStatus.FORBIDDEN, "VERIFICATION_REQUIRED", "verified email is required");
        var result = invitations.claim(jwt.getSubject(), jwt.getClaimAsString("email"), secret);
        return ResponseEntity.ok().header(HttpHeaders.SET_COOKIE, clearCookie().toString()).body(result);
    }
    private ResponseCookie cookie(String secret, java.time.Instant expiresAt) { Duration age = Duration.between(clock.instant(), expiresAt); return ResponseCookie.from(COOKIE, secret).httpOnly(true).secure(cookieSecure).sameSite("Lax").path("/api/v1/participant-access").maxAge(age.isNegative() ? Duration.ZERO : age).build(); }
    private ResponseCookie clearCookie() { return ResponseCookie.from(COOKIE, "").httpOnly(true).secure(cookieSecure).sameSite("Lax").path("/api/v1/participant-access").maxAge(Duration.ZERO).build(); }
    private void requireTrustedOrigin(HttpServletRequest request) { String origin = request.getHeader(HttpHeaders.ORIGIN); if (origin == null || !normalizedOrigin(trustedFrontendUrl).equals(origin)) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "untrusted origin"); }
    private static String normalizedOrigin(String configured) { java.net.URI uri = java.net.URI.create(configured); if (uri.getScheme() == null || uri.getHost() == null || uri.getUserInfo() != null || (uri.getPath() != null && !uri.getPath().isBlank() && !"/".equals(uri.getPath())) || uri.getQuery() != null || uri.getFragment() != null) throw new IllegalStateException("participant-access.trusted-frontend-url must be an origin"); return uri.getScheme() + "://" + uri.getHost() + (uri.getPort() < 0 ? "" : ":" + uri.getPort()); }
    @ExceptionHandler(ParticipantInvitationProblem.class)
    ProblemDetail invitationProblem(ParticipantInvitationProblem exception) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(exception.getStatusCode(), exception.getReason());
        detail.setProperty("code", exception.code());
        return detail;
    }
    record IssueCommand(String intendedEmail, ActingContext actingContext) { } record RevokeCommand(ActingContext actingContext) { } record BootstrapCommand(String token) { } record ContextStatus(String status) { }
}
