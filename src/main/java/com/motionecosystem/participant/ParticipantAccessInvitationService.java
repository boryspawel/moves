package com.motionecosystem.participant;

import com.motionecosystem.audit.AuditRecorder;
import com.motionecosystem.identityaccess.api.CurrentAccount;
import com.motionecosystem.identityaccess.api.CurrentAccountService;
import com.motionecosystem.identityaccess.api.ProfileType;
import com.motionecosystem.identityaccess.api.SpecialistAuthorizationPort;
import com.motionecosystem.identityaccess.api.SpecialistAuthorizationPort.ActingContext;
import com.motionecosystem.identityaccess.api.SpecialistAuthorizationPort.Capability;
import com.motionecosystem.identityaccess.api.SpecialistAuthorizationPort.ProfessionalRole;
import com.motionecosystem.identityaccess.api.SpecialistAuthorizationPort.Purpose;
import com.motionecosystem.participant.api.ParticipantInvitationDeliveryPort;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
class ParticipantAccessInvitationService {
    private static final Duration INVITATION_LIFETIME = Duration.ofHours(48);
    private static final Duration CONTEXT_LIFETIME = Duration.ofHours(1);

    private final ParticipantRecordRepository records;
    private final ParticipantProfileRepository profiles;
    private final ParticipantAccessLinkRepository links;
    private final ParticipantAccessInvitationRepository invitations;
    private final ParticipantClaimContextRepository contexts;
    private final CurrentAccountService accounts;
    private final SpecialistAuthorizationPort authorization;
    private final ParticipantInvitationDeliveryPort delivery;
    private final AuditRecorder audit;
    private final Clock clock;

    @Transactional
    IssueView issue(String subject, UUID participantId, String intendedEmail, ActingContext context) {
        CurrentAccount actor = authorize(subject, participantId, context);
        active(participantId);
        rejectExistingLink(participantId);
        invitations.findByParticipantIdAndStatus(participantId, ParticipantAccessInvitation.Status.PENDING)
                .ifPresent(invitation -> {
                    if (expired(invitation)) {
                        invitation.revoke();
                        invitations.flush();
                    } else {
                        throw problem(HttpStatus.CONFLICT, "INVITATION_PENDING", "a pending invitation already exists");
                    }
                });
        return create(subject, participantId, actor, email(intendedEmail), "PARTICIPANT_ACCESS_INVITATION_CREATED");
    }

    @Transactional
    IssueView reissue(String subject, UUID participantId, String intendedEmail, ActingContext context) {
        CurrentAccount actor = authorize(subject, participantId, context);
        active(participantId);
        rejectExistingLink(participantId);
        invitations.findByParticipantIdAndStatus(participantId, ParticipantAccessInvitation.Status.PENDING)
                .ifPresent(invitation -> {
                    invitation.revoke();
                    contexts.deleteByInvitationId(invitation.id());
                    invitations.flush();
                });
        return create(subject, participantId, actor, email(intendedEmail), "PARTICIPANT_ACCESS_INVITATION_REISSUED");
    }

    @Transactional
    void revoke(String subject, UUID participantId, ActingContext context) {
        authorize(subject, participantId, context);
        active(participantId);
        invitations.findByParticipantIdAndStatus(participantId, ParticipantAccessInvitation.Status.PENDING).ifPresent(invitation -> {
            invitation.revoke();
            contexts.deleteByInvitationId(invitation.id());
            audit.record(subject, "PARTICIPANT_ACCESS_INVITATION_REVOKED", "ParticipantAccessInvitation", invitation.id());
        });
    }

    @Transactional(readOnly = true)
    StatusView status(String subject, UUID participantId, ActingContext context) {
        authorize(subject, participantId, context);
        var invitation = invitations.findTopByParticipantIdOrderByCreatedAtDesc(participantId).orElse(null);
        if (invitation == null) {
            return new StatusView(null, null, null, "NONE", linkState(participantId));
        }
        return new StatusView(invitation.intendedEmail, invitation.createdAt, invitation.expiresAt,
                statusOf(invitation), linkState(participantId));
    }

    @Transactional
    ContextView bootstrap(String rawToken) {
        ParticipantAccessInvitationRepository.InvitationPointer initial = invitations.findPointerByTokenHash(hash(rawToken))
                .orElseThrow(() -> problem(HttpStatus.BAD_REQUEST, "INVITATION_INVALID", "invalid invitation"));
        active(initial.getParticipantId());
        ParticipantAccessInvitation invitation = invitations.lockByTokenHash(hash(rawToken))
                .orElseThrow(() -> problem(HttpStatus.BAD_REQUEST, "INVITATION_INVALID", "invalid invitation"));
        requireUsable(invitation);
        contexts.deleteByInvitationId(invitation.id());
        Instant now = clock.instant();
        Instant expiresAt = min(now.plus(CONTEXT_LIFETIME), invitation.expiresAt);
        String secret = token();
        try {
            contexts.saveAndFlush(new ParticipantClaimContext(invitation.id(), hash(secret), now, expiresAt));
        } catch (DataIntegrityViolationException exception) {
            throw problem(HttpStatus.CONFLICT, "CLAIM_CONTEXT_CONFLICT", "claim context could not be created");
        }
        return new ContextView(secret, expiresAt, "PENDING");
    }

    @Transactional(readOnly = true)
    ContextStatus contextStatus(String secret) {
        ParticipantClaimContext context = contexts.findBySecretHash(hash(secret))
                .orElseThrow(() -> problem(HttpStatus.BAD_REQUEST, "CLAIM_CONTEXT_INVALID", "invalid claim context"));
        if (!clock.instant().isBefore(context.expiresAt)) {
            throw problem(HttpStatus.BAD_REQUEST, "CLAIM_CONTEXT_EXPIRED", "claim context has expired");
        }
        ParticipantAccessInvitation invitation = invitations.findById(context.invitationId).orElseThrow(() ->
                problem(HttpStatus.BAD_REQUEST, "CLAIM_CONTEXT_INVALID", "invalid claim context"));
        requireUsable(invitation);
        return new ContextStatus("PENDING");
    }

    @Transactional
    ClaimView claim(String subject, String verifiedEmail, String secret) {
        CurrentAccount account = accounts.requireActive(subject);
        if (!account.hasProfile(ProfileType.PARTICIPANT)) {
            throw problem(HttpStatus.FORBIDDEN, "PARTICIPANT_PROFILE_REQUIRED", "participant profile is required");
        }
        ParticipantClaimContextRepository.ContextPointer initialContext = contexts.findPointerBySecretHash(hash(secret)).orElseThrow(() ->
                problem(HttpStatus.BAD_REQUEST, "CLAIM_CONTEXT_INVALID", "invalid claim context"));
        ParticipantAccessInvitationRepository.InvitationPointer initialInvitation = invitations.findPointerById(initialContext.getInvitationId()).orElseThrow(() ->
                problem(HttpStatus.BAD_REQUEST, "CLAIM_CONTEXT_INVALID", "invalid claim context"));
        active(initialInvitation.getParticipantId());
        ParticipantClaimContext context = contexts.lockBySecretHash(hash(secret)).orElseThrow(() ->
                problem(HttpStatus.BAD_REQUEST, "CLAIM_CONTEXT_INVALID", "invalid claim context"));
        if (!clock.instant().isBefore(context.expiresAt)) {
            throw problem(HttpStatus.BAD_REQUEST, "CLAIM_CONTEXT_EXPIRED", "claim context has expired");
        }
        ParticipantAccessInvitation invitation = invitations.lockById(context.invitationId).orElseThrow(() ->
                problem(HttpStatus.BAD_REQUEST, "CLAIM_CONTEXT_INVALID", "invalid claim context"));

        if (invitation.status == ParticipantAccessInvitation.Status.CLAIMED) {
            if (account.id().equals(invitation.claimedAccountId) && activeSameLink(invitation.participantId, account.id())) {
                return new ClaimView(invitation.participantId, "CLAIMED");
            }
            throw problem(HttpStatus.CONFLICT, "ALREADY_CLAIMED_OTHER", "invitation was already claimed by another account");
        }
        requireUsable(invitation);
        if (!invitation.intendedEmail.equals(email(verifiedEmail))) {
            throw problem(HttpStatus.FORBIDDEN, "EMAIL_MISMATCH", "invitation email does not match verified account email");
        }

        var participantLink = links.findByParticipantId(invitation.participantId);
        var accountLink = links.findByPrincipalAccountId(account.id());
        if (participantLink.isPresent() || accountLink.isPresent()) {
            if (participantLink.filter(link -> link.principalAccountId().equals(account.id())
                    && link.accessStatus() == ParticipantAccessLink.Status.ACTIVE).isPresent()
                    && accountLink.filter(link -> link.participantId().equals(invitation.participantId)
                    && link.accessStatus() == ParticipantAccessLink.Status.ACTIVE).isPresent()) {
                invitation.claim(account.id(), clock.instant());
                audit.record(subject, "PARTICIPANT_ACCESS_CLAIMED", "ParticipantAccessInvitation", invitation.id());
                return new ClaimView(invitation.participantId, "CLAIMED");
            }
            throw problem(HttpStatus.CONFLICT, "ACCESS_LINK_CONFLICT", "participant access link already exists");
        }
        try {
            links.saveAndFlush(new ParticipantAccessLink(invitation.participantId, account.id()));
            records.findById(invitation.participantId).filter(record -> record.timeZoneId() == null)
                    .ifPresent(record -> profiles.findByAccountId(account.id())
                            .filter(profile -> profile.timeZoneId != null)
                            .ifPresent(profile -> record.updateTimeZone(java.time.ZoneId.of(profile.timeZoneId), clock.instant())));
            invitation.claim(account.id(), clock.instant());
        } catch (DataIntegrityViolationException exception) {
            throw problem(HttpStatus.CONFLICT, "ACCESS_LINK_CONFLICT", "participant access link already exists");
        }
        audit.record(subject, "PARTICIPANT_ACCESS_CLAIMED", "ParticipantAccessInvitation", invitation.id());
        return new ClaimView(invitation.participantId, "CLAIMED");
    }

    private IssueView create(String subject, UUID participantId, CurrentAccount actor, String intendedEmail, String auditEvent) {
        Instant now = clock.instant();
        String rawToken = token();
        ParticipantAccessInvitation invitation = new ParticipantAccessInvitation(participantId, actor.id(), actor.externalSubject(),
                intendedEmail, hash(rawToken), now, now.plus(INVITATION_LIFETIME));
        try {
            invitations.saveAndFlush(invitation);
        } catch (DataIntegrityViolationException exception) {
            throw problem(HttpStatus.CONFLICT, "INVITATION_CONFLICT", "a pending invitation already exists");
        }
        delivery.deliver(invitation.id(), intendedEmail, rawToken, invitation.expiresAt);
        audit.record(subject, auditEvent, "ParticipantAccessInvitation", invitation.id());
        return new IssueView(invitation.id(), invitation.expiresAt, "PENDING");
    }

    private void active(UUID participantId) {
        ParticipantRecord record = records.lockById(participantId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "participant not found"));
        if (record.recordStatus() != ParticipantRecord.Status.ACTIVE) {
            throw problem(HttpStatus.CONFLICT, "PARTICIPANT_ARCHIVED", "participant is archived");
        }
    }

    private CurrentAccount authorize(String subject, UUID participantId, ActingContext context) {
        CurrentAccount account = accounts.requireActive(subject);
        if (!account.hasProfile(ProfileType.SPECIALIST)) {
            throw problem(HttpStatus.FORBIDDEN, "SPECIALIST_PROFILE_REQUIRED", "specialist profile is required");
        }
        if (context == null || context.role() == null) {
            throw problem(HttpStatus.BAD_REQUEST, "ACTING_CONTEXT_REQUIRED", "acting context is required");
        }
        authorization.requireCapabilities(account.id(), participantId, context, Set.of(Capability.MANAGE_PARTICIPANT_RECORDS),
                context.role() == ProfessionalRole.TRAINER ? Purpose.PERFORMANCE_PLANNING : Purpose.FUNCTIONAL_RECOVERY);
        if (records.findById(participantId).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "participant not found");
        }
        return account;
    }

    private void rejectExistingLink(UUID participantId) {
        if (links.findByParticipantId(participantId).isPresent()) {
            throw problem(HttpStatus.CONFLICT, "ACCESS_LINK_CONFLICT", "participant already has an access link");
        }
    }

    private boolean activeSameLink(UUID participantId, UUID accountId) {
        return links.findByParticipantId(participantId)
                .filter(link -> link.principalAccountId().equals(accountId) && link.accessStatus() == ParticipantAccessLink.Status.ACTIVE)
                .isPresent();
    }

    private void requireUsable(ParticipantAccessInvitation invitation) {
        if (invitation.status == ParticipantAccessInvitation.Status.REVOKED) {
            throw problem(HttpStatus.BAD_REQUEST, "INVITATION_REVOKED", "invitation was revoked");
        }
        if (expired(invitation)) {
            throw problem(HttpStatus.BAD_REQUEST, "INVITATION_EXPIRED", "invitation has expired");
        }
        if (invitation.status != ParticipantAccessInvitation.Status.PENDING) {
            throw problem(HttpStatus.BAD_REQUEST, "INVITATION_INVALID", "invalid invitation");
        }
    }

    private boolean expired(ParticipantAccessInvitation invitation) {
        return !clock.instant().isBefore(invitation.expiresAt);
    }

    private String statusOf(ParticipantAccessInvitation invitation) {
        return expired(invitation) && invitation.status == ParticipantAccessInvitation.Status.PENDING ? "EXPIRED" : invitation.status.name();
    }

    private String linkState(UUID participantId) {
        return links.findByParticipantId(participantId).map(link -> link.accessStatus().name()).orElse("NONE");
    }

    private static Instant min(Instant first, Instant second) { return first.isBefore(second) ? first : second; }
    private static String token() { byte[] bytes = new byte[32]; new SecureRandom().nextBytes(bytes); return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes); }
    private static String hash(String value) { try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); } catch (Exception exception) { throw new IllegalStateException(exception); } }
    private static String email(String value) { if (value == null || !value.contains("@")) throw problem(HttpStatus.BAD_REQUEST, "EMAIL_INVALID", "invalid invitation email"); return value.trim().toLowerCase(Locale.ROOT); }
    private static ParticipantInvitationProblem problem(HttpStatus status, String code, String detail) { return new ParticipantInvitationProblem(status, code, detail); }

    record IssueView(UUID invitationId, Instant expiresAt, String status) { }
    record StatusView(String intendedEmail, Instant createdAt, Instant expiresAt, String status, String linkState) { }
    record ContextView(String secret, Instant expiresAt, String status) { }
    record ContextStatus(String status) { }
    record ClaimView(UUID participantId, String status) { }
}
