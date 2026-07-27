package com.motionecosystem.specialist;

import com.motionecosystem.audit.AuditRecorder;
import com.motionecosystem.consent.api.ConsentDecisionPort;
import com.motionecosystem.consent.api.TestDefaultConsentOverridePort;
import com.motionecosystem.identityaccess.api.CurrentAccountService;
import com.motionecosystem.identityaccess.api.ProfileType;
import com.motionecosystem.participant.api.ParticipantClientPort;
import com.motionecosystem.calendar.api.SpecialistAppointmentQueryPort;
import com.motionecosystem.trainingplanning.api.PlanRevisionQueryPort;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import org.springframework.dao.DataIntegrityViolationException;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

/** Owns the account-free participant-record creation vertical. Legacy participant-account ids are deliberately not written. */
@Service @RequiredArgsConstructor
public class SpecialistClientService {
    private final CurrentAccountService accounts;
    private final SpecialistProfileService profiles;
    private final SpecialistAuthorizationService authorization;
    private final ParticipantClientPort participants;
    private final ParticipantSpecialistRelationshipRepository relationships;
    private final TestDefaultConsentOverridePort testDefaultConsentOverrides;
    private final ClientCreateIdempotencyRepository idempotency;
    private final AuditRecorder audit;
    private final Clock clock;
    private final SpecialistAppointmentQueryPort appointments;
    private final PlanRevisionQueryPort plans;
    private final SpecialistWorklistItemRepository worklistItems;
    private final TransactionTemplate transactions;
    @Value("${moves.test-default-consent.enabled:false}") private boolean testConsentEnabled;

    public ClientView create(String subject, String key, ClientCommand command) {
        Access access = specialist(subject);
        authorization.requireVerifiedScope(access.id, access.kind);
        UUID idempotencyKey = requiredKey(key);
        NormalizedCommand normalized = normalize(command, access.kind);
        String fingerprint = fingerprint(normalized);
        ClientCreateIdempotency.Id requestId = new ClientCreateIdempotency.Id(access.id, idempotencyKey);
        var replay = idempotency.findById(requestId);
        if (replay.isPresent()) return replay(access.id, replay.get(), fingerprint);
        requireTestConsent();
        try {
            return transactions.execute(status -> createNew(subject, access, idempotencyKey, normalized, fingerprint));
        } catch (DataIntegrityViolationException race) {
            return idempotency.findById(requestId)
                    .map(item -> replay(access.id, item, fingerprint))
                    .orElseThrow(() -> race);
        }
    }

    private ClientView createNew(String subject, Access access, UUID idempotencyKey, NormalizedCommand command, String fingerprint) {
        Instant now = clock.instant();
        ParticipantClientPort.ClientRecord record = participants.create(new ParticipantClientPort.CreateCommand(
                command.displayName(), command.relationshipContext(), command.email(), command.phone(), command.zone(), access.id, now));
        relationships.save(new ParticipantSpecialistRelationship(access.id, record.id(), record.relationshipContext(), now));
        testDefaultConsentOverrides.create(new TestDefaultConsentOverridePort.CreateCommand(record.id(), access.id,
                consentPurpose(access.kind), consentScopes(), access.id, now));
        idempotency.saveAndFlush(new ClientCreateIdempotency(access.id, idempotencyKey, record.id(), fingerprint, now));
        audit.record(subject, "PARTICIPANT_RECORD_CREATED", "ParticipantRecord", record.id());
        audit.record(subject, "PARTICIPANT_RELATIONSHIP_CREATED", "ParticipantRecord", record.id());
        audit.record(subject, "TEST_DEFAULT_CONSENT_OVERRIDE_CREATED", "ParticipantRecord", record.id());
        return view(access.id, record);
    }

    @Transactional(readOnly = true)
    public List<ClientView> list(String subject) {
        Access access = specialist(subject);
        return relationships.findBySpecialistAccountIdAndStatus(access.id, ParticipantSpecialistRelationship.Status.ACTIVE).stream()
                .map(ParticipantSpecialistRelationship::participantId).filter(java.util.Objects::nonNull).distinct()
                .map(this::record).map(record -> view(access.id, record)).toList();
    }
    @Transactional(readOnly = true)
    public ClientView get(String subject, UUID participantId) { Access access = specialist(subject); return view(access.id, authorized(access.id, participantId)); }
    @Transactional
    public ClientView update(String subject, UUID participantId, ClientCommand command) {
        Access access = specialist(subject); ParticipantClientPort.ClientRecord record = authorized(access.id, participantId);
        if (record.recordStatus() == ParticipantClientPort.RecordStatus.ARCHIVED) throw conflict("archived participant record cannot be edited");
        ParticipantClientPort.ClientRecord updated = participants.update(participantId, new ParticipantClientPort.UpdateCommand(
                name(command.displayName), command.relationshipContext == null ? record.relationshipContext() : command.relationshipContext(),
                optional(command.email, 254), optional(command.phone, 40), zone(command.timeZoneId), clock.instant())).orElseThrow(() -> notFound());
        audit.record(subject, "PARTICIPANT_RECORD_UPDATED", "ParticipantRecord", updated.id()); return view(access.id, updated);
    }
    @Transactional
    public ClientView archive(String subject, UUID participantId) {
        Access access = specialist(subject); authorized(access.id, participantId);
        ParticipantClientPort.ClientRecord archived = participants.archive(participantId, clock.instant()).orElseThrow(() -> notFound());
        audit.record(subject, "PARTICIPANT_RECORD_ARCHIVED", "ParticipantRecord", archived.id()); return view(access.id, archived);
    }
    private ParticipantClientPort.ClientRecord authorized(UUID specialistId, UUID participantId) {
        if (relationships.findBySpecialistAccountIdAndParticipantIdAndStatus(specialistId, participantId, ParticipantSpecialistRelationship.Status.ACTIVE).isEmpty()) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "active participant-specialist relationship is required");
        return record(participantId);
    }
    private ClientView view(UUID specialistId, ParticipantClientPort.ClientRecord value) {
        var link = participants.findAccessLink(value.id());
        String access = link.map(ParticipantClientPort.AccessLink::accessStatus).orElse("NO_ACCOUNT");
        String consent = testDefaultConsentOverrides.find(value.id(), specialistId, ConsentDecisionPort.Purpose.PERFORMANCE_PLANNING, consentScopes()).isPresent()
                || testDefaultConsentOverrides.find(value.id(), specialistId, ConsentDecisionPort.Purpose.FUNCTIONAL_RECOVERY, consentScopes()).isPresent()
                ? "TEST_DEFAULT_ACTIVE" : "NOT_AVAILABLE";
        UUID accountId = link.map(ParticipantClientPort.AccessLink::principalAccountId).orElse(null);
        ClientAppointmentView nextAppointment = accountId == null ? null : appointments.findForParticipant(
                specialistId, accountId, clock.instant(), clock.instant().plusSeconds(366L * 24 * 60 * 60), 1).stream()
                .filter(item -> "SCHEDULED".equals(item.status()) || "CONFIRMED".equals(item.status()))
                .min(java.util.Comparator.comparing(SpecialistAppointmentQueryPort.AppointmentSummary::startsAt))
                .map(item -> new ClientAppointmentView(item.appointmentId(), item.startsAt(), item.type(), item.status()))
                .orElse(null);
        ClientActivePlanView activePlan = accountId == null ? null : plans.findActiveRevision(accountId)
                .map(item -> new ClientActivePlanView(item.planId(), item.revisionId(), item.status(), item.validFrom(), item.validTo()))
                .orElse(null);
        List<ClientAttentionView> attentionItems = accountId == null ? List.of() : worklistItems
                .findByParticipantIdOrderByUpdatedAtDesc(accountId).stream()
                .filter(item -> "OPEN".equals(item.status) || "ACKNOWLEDGED".equals(item.status) || "SNOOZED".equals(item.status))
                .map(item -> new ClientAttentionView(item.id, item.category, item.priority, item.status)).toList();
        return new ClientView(value.id(), value.displayName(), value.relationshipContext(), value.recordStatus(), access, consent,
                nextAppointment, activePlan, attentionItems, actions(value.recordStatus()), value.version());
    }
    private Access specialist(String subject) {
        var account = accounts.requireActive(subject);
        if (!account.hasProfile(ProfileType.SPECIALIST)) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "specialist profile is required");
        var profile = profiles.find(account.id()).orElseThrow(() -> conflict("specialist profile is required"));
        return new Access(account.id(), profile.specialistKind());
    }
    private void requireTestConsent() { if (!testConsentEnabled) throw conflict("test default consent override is disabled"); }
    private ParticipantClientPort.ClientRecord record(UUID id) { return participants.find(id).orElseThrow(SpecialistClientService::notFound); }
    private static UUID requiredKey(String key) { try { return UUID.fromString(key == null ? "" : key.trim()); } catch (IllegalArgumentException invalid) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Idempotency-Key must be a UUID"); } }
    private static String name(String value) { String result = value == null ? "" : value.trim(); if (result.isEmpty() || result.length() > 80) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "displayName must contain 1-80 characters"); return result; }
    private static String optional(String value, int max) { if (value == null || value.isBlank()) return null; String result = value.trim(); if (result.length() > max) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "optional field is too long"); return result; }
    private static ZoneId zone(String value) { if (value == null || value.isBlank()) return null; try { return ZoneId.of(value.trim()); } catch (RuntimeException invalid) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "timeZoneId must be a valid IANA identifier"); } }
    private static ParticipantClientPort.RelationshipContext context(ParticipantClientPort.RelationshipContext given, SpecialistKind kind) { return given == null ? (kind == SpecialistKind.PHYSIOTHERAPIST ? ParticipantClientPort.RelationshipContext.PATIENT : ParticipantClientPort.RelationshipContext.CLIENT) : given; }
    private static ConsentDecisionPort.Purpose consentPurpose(SpecialistKind kind) { return kind == SpecialistKind.PHYSIOTHERAPIST ? ConsentDecisionPort.Purpose.FUNCTIONAL_RECOVERY : ConsentDecisionPort.Purpose.PERFORMANCE_PLANNING; }
    private static java.util.Set<ConsentDecisionPort.DataScope> consentScopes() { return java.util.EnumSet.allOf(ConsentDecisionPort.DataScope.class); }
    private ClientView replay(UUID specialistId, ClientCreateIdempotency request, String fingerprint) {
        if (!request.hasFingerprint(fingerprint)) throw conflict("Idempotency-Key was already used with a different request payload");
        return view(specialistId, record(request.participantId()));
    }
    private static NormalizedCommand normalize(ClientCommand command, SpecialistKind kind) {
        if (command == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "request body is required");
        return new NormalizedCommand(name(command.displayName), context(command.relationshipContext, kind), optional(command.email, 254), optional(command.phone, 40), zone(command.timeZoneId));
    }
    private static String fingerprint(NormalizedCommand command) {
        String value = command.displayName() + '\u001f' + command.relationshipContext().name() + '\u001f'
                + nullSafe(command.email()) + '\u001f' + nullSafe(command.phone()) + '\u001f' + (command.zone() == null ? "" : command.zone().getId());
        try { return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException unavailable) { throw new IllegalStateException("SHA-256 is required", unavailable); }
    }
    private static String nullSafe(String value) { return value == null ? "" : value; }
    private static List<String> actions(ParticipantClientPort.RecordStatus status) { return status == ParticipantClientPort.RecordStatus.ARCHIVED ? List.of("OPEN_WORKSPACE") : List.of("OPEN_WORKSPACE", "EDIT_BASIC_DATA", "SCHEDULE_APPOINTMENT", "ADD_NOTE", "CREATE_PLAN", "ARCHIVE"); }
    private static ResponseStatusException notFound() { return new ResponseStatusException(HttpStatus.NOT_FOUND, "participant record not found"); }
    private static ResponseStatusException conflict(String message) { return new ResponseStatusException(HttpStatus.CONFLICT, message); }
    private record Access(UUID id, SpecialistKind kind) { }
    private record NormalizedCommand(String displayName, ParticipantClientPort.RelationshipContext relationshipContext, String email, String phone, ZoneId zone) { }
    public record ClientCommand(String displayName, ParticipantClientPort.RelationshipContext relationshipContext, String email, String phone, String timeZoneId) { }
    public record ClientView(UUID participantId, String displayName, ParticipantClientPort.RelationshipContext relationshipContext, ParticipantClientPort.RecordStatus recordStatus, String accessStatus, String consentStatus, ClientAppointmentView nextAppointment, ClientActivePlanView activePlan, List<ClientAttentionView> attentionItems, List<String> availableActions, long version) { }
    public record ClientAppointmentView(UUID appointmentId, java.time.Instant startsAt, String type, String status) { }
    public record ClientActivePlanView(UUID planId, UUID revisionId, String status, java.time.LocalDate validFrom, java.time.LocalDate validTo) { }
    public record ClientAttentionView(UUID id, String category, String priority, String status) { }
}
