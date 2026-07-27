package com.motionecosystem.specialist;

import com.motionecosystem.audit.AuditRecorder;
import com.motionecosystem.identityaccess.api.CurrentAccountService;
import com.motionecosystem.identityaccess.api.ProfileType;
import com.motionecosystem.participant.ParticipantAccessLinkRepository;
import com.motionecosystem.participant.ParticipantRecord;
import com.motionecosystem.participant.ParticipantRecordRepository;
import com.motionecosystem.calendar.api.SpecialistAppointmentQueryPort;
import com.motionecosystem.trainingplanning.api.PlanRevisionQueryPort;
import java.time.Clock;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Owns the account-free participant-record creation vertical. Legacy participant-account ids are deliberately not written. */
@Service @RequiredArgsConstructor
public class SpecialistClientService {
    private final CurrentAccountService accounts;
    private final SpecialistProfileService profiles;
    private final ParticipantRecordRepository records;
    private final ParticipantAccessLinkRepository links;
    private final ParticipantSpecialistRelationshipRepository relationships;
    private final TestDefaultConsentOverrideRepository overrides;
    private final ClientCreateIdempotencyRepository idempotency;
    private final AuditRecorder audit;
    private final Clock clock;
    private final SpecialistAppointmentQueryPort appointments;
    private final PlanRevisionQueryPort plans;
    private final SpecialistWorklistItemRepository worklistItems;
    @Value("${moves.test-default-consent.enabled:false}") private boolean testConsentEnabled;

    @Transactional
    public ClientView create(String subject, String key, ClientCommand command) {
        Access access = specialist(subject);
        UUID idempotencyKey = requiredKey(key);
        ClientCreateIdempotency.Id requestId = new ClientCreateIdempotency.Id(access.id, idempotencyKey);
        var replay = idempotency.findById(requestId);
        if (replay.isPresent()) return view(access.id, record(replay.get().participantId()));
        requireTestConsent();
        ParticipantRecord record = records.save(new ParticipantRecord(name(command.displayName), context(command.relationshipContext, access.kind), optional(command.email, 254), optional(command.phone, 40), zone(command.timeZoneId), access.id, clock.instant()));
        relationships.save(new ParticipantSpecialistRelationship(access.id, record.id(), record.relationshipContext(), clock.instant()));
        overrides.save(new TestDefaultConsentOverride(record.id(), access.id, purpose(access.kind), "RECORD,PLAN,CALENDAR,EXECUTION,MEASUREMENTS,NOTES,TIMELINE,ALERTS", subject, clock.instant()));
        idempotency.save(new ClientCreateIdempotency(access.id, idempotencyKey, record.id(), clock.instant()));
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
        Access access = specialist(subject); ParticipantRecord record = authorized(access.id, participantId);
        if (record.recordStatus() == ParticipantRecord.Status.ARCHIVED) throw conflict("archived participant record cannot be edited");
        record.update(name(command.displayName), command.relationshipContext == null ? null : command.relationshipContext, optional(command.email, 254), optional(command.phone, 40), zone(command.timeZoneId), clock.instant());
        audit.record(subject, "PARTICIPANT_RECORD_UPDATED", "ParticipantRecord", record.id()); return view(access.id, record);
    }
    @Transactional
    public ClientView archive(String subject, UUID participantId) {
        Access access = specialist(subject); ParticipantRecord record = authorized(access.id, participantId); record.archive(clock.instant());
        audit.record(subject, "PARTICIPANT_RECORD_ARCHIVED", "ParticipantRecord", record.id()); return view(access.id, record);
    }
    private ParticipantRecord authorized(UUID specialistId, UUID participantId) {
        if (relationships.findBySpecialistAccountIdAndParticipantIdAndStatus(specialistId, participantId, ParticipantSpecialistRelationship.Status.ACTIVE).isEmpty()) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "active participant-specialist relationship is required");
        return record(participantId);
    }
    private ClientView view(UUID specialistId, ParticipantRecord value) {
        var link = links.findByParticipantId(value.id());
        String access = link.map(item -> item.accessStatus().name()).orElse("NO_ACCOUNT");
        String consent = overrides.existsByParticipantIdAndSpecialistId(value.id(), specialistId) ? "TEST_DEFAULT_ACTIVE" : "NOT_AVAILABLE";
        UUID accountId = link.map(com.motionecosystem.participant.ParticipantAccessLink::principalAccountId).orElse(null);
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
                .findByParticipantAccountIdOrderByUpdatedAtDesc(accountId).stream()
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
    private ParticipantRecord record(UUID id) { return records.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "participant record not found")); }
    private static UUID requiredKey(String key) { try { return UUID.fromString(key == null ? "" : key.trim()); } catch (IllegalArgumentException invalid) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Idempotency-Key must be a UUID"); } }
    private static String name(String value) { String result = value == null ? "" : value.trim(); if (result.isEmpty() || result.length() > 80) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "displayName must contain 1-80 characters"); return result; }
    private static String optional(String value, int max) { if (value == null || value.isBlank()) return null; String result = value.trim(); if (result.length() > max) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "optional field is too long"); return result; }
    private static ZoneId zone(String value) { if (value == null || value.isBlank()) return null; try { return ZoneId.of(value.trim()); } catch (RuntimeException invalid) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "timeZoneId must be a valid IANA identifier"); } }
    private static ParticipantRecord.RelationshipContext context(ParticipantRecord.RelationshipContext given, SpecialistKind kind) { return given == null ? (kind == SpecialistKind.PHYSIOTHERAPIST ? ParticipantRecord.RelationshipContext.PATIENT : ParticipantRecord.RelationshipContext.CLIENT) : given; }
    private static String purpose(SpecialistKind kind) { return kind == SpecialistKind.PHYSIOTHERAPIST ? "FUNCTIONAL_RECOVERY" : "PERFORMANCE_PLANNING"; }
    private static List<String> actions(ParticipantRecord.Status status) { return status == ParticipantRecord.Status.ARCHIVED ? List.of("OPEN_WORKSPACE") : List.of("OPEN_WORKSPACE", "EDIT_BASIC_DATA", "SCHEDULE_APPOINTMENT", "ADD_NOTE", "CREATE_PLAN", "ARCHIVE"); }
    private static ResponseStatusException conflict(String message) { return new ResponseStatusException(HttpStatus.CONFLICT, message); }
    private record Access(UUID id, SpecialistKind kind) { }
    public record ClientCommand(String displayName, ParticipantRecord.RelationshipContext relationshipContext, String email, String phone, String timeZoneId) { }
    public record ClientView(UUID participantId, String displayName, ParticipantRecord.RelationshipContext relationshipContext, ParticipantRecord.Status recordStatus, String accessStatus, String consentStatus, ClientAppointmentView nextAppointment, ClientActivePlanView activePlan, List<ClientAttentionView> attentionItems, List<String> availableActions, long version) { }
    public record ClientAppointmentView(UUID appointmentId, java.time.Instant startsAt, String type, String status) { }
    public record ClientActivePlanView(UUID planId, UUID revisionId, String status, java.time.LocalDate validFrom, java.time.LocalDate validTo) { }
    public record ClientAttentionView(UUID id, String category, String priority, String status) { }
}
