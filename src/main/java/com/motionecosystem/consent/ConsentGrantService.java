package com.motionecosystem.consent;

import java.time.Clock;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import com.motionecosystem.audit.AuditRecorder;
import com.motionecosystem.consent.api.ConsentDecisionPort;
import com.motionecosystem.identityaccess.api.CurrentAccountService;
import com.motionecosystem.identityaccess.api.ProfileType;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class ConsentGrantService implements ConsentDecisionPort {

    private final ConsentTemplateRepository templates;
    private final ConsentGrantRepository grants;
    private final CurrentAccountService accounts;
    private final AuditRecorder audit;
    private final Clock clock;

    @Transactional
    public TemplateView publishTemplate(String code, int number, String reference, String basis) {
        var item = templates.save(new ConsentTemplateVersion(
                text(code), number, text(reference), text(basis), clock.instant()));
        return new TemplateView(item.id, item.code, item.number);
    }

    @Transactional
    public GrantView grant(String subject, GrantCommand command) {
        var participant = accounts.requireActive(subject);
        if (!participant.hasProfile(ProfileType.PARTICIPANT)) {
            throw forbidden("participant profile is required");
        }
        if (command == null
                || command.recipientId() == null
                || command.purpose() == null
                || command.templateVersionId() == null
                || command.dataScopes() == null
                || command.dataScopes().isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "complete consent grant is required");
        }
        templates.findByIdAndStatus(
                        command.templateVersionId(), ConsentTemplateVersion.Status.PUBLISHED)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "published template is required"));
        Instant from = command.validFrom() == null ? clock.instant() : command.validFrom();
        if (command.validTo() != null && command.validTo().isBefore(from)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "consent dates are invalid");
        }
        var item = grants.save(new ConsentGrant(
                participant.id(),
                ConsentGrant.RecipientType.SPECIALIST,
                command.recipientId(),
                command.purpose(),
                command.templateVersionId(),
                command.dataScopes(),
                from,
                command.validTo(),
                clock.instant()));
        audit.record(subject, "CONSENT_GRANTED", "ConsentGrant", item.id);
        return view(item);
    }

    @Transactional
    public GrantView revoke(String subject, UUID grantId) {
        var participant = accounts.requireActive(subject);
        var item = grants.findById(grantId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "consent grant not found"));
        if (!item.participantId.equals(participant.id())) {
            throw forbidden("consent belongs to another participant");
        }
        item.revoke(clock.instant());
        audit.record(subject, "CONSENT_REVOKED", "ConsentGrant", item.id);
        return view(item);
    }

    @Override
    @Transactional(readOnly = true)
    public ConsentDecision requireAccess(
            UUID actor, UUID participant, Set<DataScope> scopes, Purpose purpose) {
        if (actor == null
                || participant == null
                || scopes == null
                || scopes.isEmpty()
                || purpose == null) {
            throw forbidden("explicit consent decision input is required");
        }
        Instant now = clock.instant();
        var grant = grants.findByParticipantIdAndRecipientTypeAndRecipientIdAndPurposeAndStatus(
                        participant,
                        ConsentGrant.RecipientType.SPECIALIST,
                        actor,
                        purpose,
                        ConsentGrant.Status.ACTIVE)
                .stream()
                .filter(item -> item.activeAt(now) && item.scopes.containsAll(scopes))
                .findFirst()
                .orElseThrow(() -> forbidden("active purpose and scope consent is required"));
        return new ConsentDecision(grant.id, actor, participant, scopes, purpose, grant.validTo);
    }

    private static String text(String value) {
        if (value == null || value.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "consent template fields are required");
        }
        return value.trim();
    }

    private static ResponseStatusException forbidden(String value) {
        return new ResponseStatusException(HttpStatus.FORBIDDEN, value);
    }

    private static GrantView view(ConsentGrant item) {
        return new GrantView(
                item.id,
                item.participantId,
                item.recipientId,
                item.purpose,
                Set.copyOf(item.scopes),
                item.status.name(),
                item.validFrom,
                item.validTo);
    }

    public record GrantCommand(
            UUID recipientId,
            Purpose purpose,
            UUID templateVersionId,
            Set<DataScope> dataScopes,
            Instant validFrom,
            Instant validTo) {
    }

    public record GrantView(
            UUID id,
            UUID participantId,
            UUID recipientId,
            Purpose purpose,
            Set<DataScope> scopes,
            String status,
            Instant validFrom,
            Instant validTo) {
    }

    public record TemplateView(UUID id, String code, int versionNumber) {
    }
}
