package com.motionecosystem.calendar;

import com.motionecosystem.calendar.api.SpecialistAppointmentQueryPort;
import com.motionecosystem.audit.AuditRecorder;
import com.motionecosystem.identityaccess.api.CurrentAccountService;
import com.motionecosystem.identityaccess.api.ProfileType;
import com.motionecosystem.specialist.SpecialistRelationshipService;
import java.time.*;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class AppointmentService implements SpecialistAppointmentQueryPort {
    private final AppointmentRepository appointments;
    private final AppointmentIdempotencyRepository idempotency;
    private final CurrentAccountService accounts;
    private final SpecialistRelationshipService relationships;
    private final AuditRecorder audit;
    private final Clock clock;

    @Transactional
    public AppointmentView create(String subject, String key, CreateCommand command) {
        UUID specialist = specialist(subject); String idempotencyKey = key(key);
        return replay(specialist, "CREATE", idempotencyKey).orElseGet(() -> {
            Values values = values(command); relationships.requireActive(specialist, values.participantId());
            conflictIfOverlapping(specialist, values.startsAt(), values.endsAt(), UUID.randomUUID());
            Appointment saved = appointments.saveAndFlush(new Appointment(specialist, values.participantId(), values.startsAt(), values.endsAt(),
                    values.type(), values.locationMode(), values.location(), values.shortPurpose(), specialist, clock.instant()));
            remember(specialist, "CREATE", idempotencyKey, saved.id);
            audit.record(subject, "APPOINTMENT_CREATED", "Appointment", saved.id);
            return view(saved);
        });
    }

    @Transactional
    public AppointmentView update(String subject, UUID id, String key, UpdateCommand command) {
        UUID specialist = specialist(subject); String idempotencyKey = key(key);
        return replay(specialist, "UPDATE:" + id, idempotencyKey).orElseGet(() -> {
            Appointment appointment = owned(specialist, id); version(appointment, command == null ? null : command.version());
            Values values = values(command); if (!appointment.participantAccountId.equals(values.participantId())) bad("participantId cannot be changed");
            relationships.requireActive(specialist, appointment.participantAccountId);
            if (appointment.status == Appointment.Status.CANCELLED) conflict("cancelled appointment cannot be changed");
            conflictIfOverlapping(specialist, values.startsAt(), values.endsAt(), appointment.id);
            appointment.update(values.startsAt(), values.endsAt(), values.type(), values.locationMode(), values.location(), values.shortPurpose(), clock.instant());
            Appointment saved = saveConflict(appointment); remember(specialist, "UPDATE:" + id, idempotencyKey, id);
            audit.record(subject, "APPOINTMENT_UPDATED", "Appointment", id); return view(saved);
        });
    }

    @Transactional
    public AppointmentView cancel(String subject, UUID id, String key, VersionCommand command) { return changeStatus(subject, id, key, command, "CANCEL", true); }
    @Transactional
    public AppointmentView noShow(String subject, UUID id, String key, VersionCommand command) { return changeStatus(subject, id, key, command, "NO_SHOW", false); }

    @Transactional(readOnly = true)
    public List<AppointmentView> inRange(UUID specialist, Instant start, Instant end, Set<UUID> activeParticipants, Instant now) {
        return appointments.findIntersecting(specialist, start, end).stream()
                .filter(appointment -> activeParticipants.contains(appointment.participantAccountId))
                .map(appointment -> view(appointment, now)).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<SpecialistAppointmentQueryPort.AppointmentSummary> findForParticipant(UUID specialistAccountId,
            UUID participantAccountId, Instant fromInclusive, Instant toExclusive, int limit) {
        if (specialistAccountId == null || participantAccountId == null || fromInclusive == null || toExclusive == null
                || !toExclusive.isAfter(fromInclusive) || limit < 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "specialist, participant, range and limit are required");
        }
        return appointments.findIntersecting(specialistAccountId, fromInclusive, toExclusive).stream()
                .filter(item -> participantAccountId.equals(item.participantAccountId))
                .sorted(Comparator.comparing((Appointment item) -> item.startsAt).reversed().thenComparing(item -> item.id))
                .limit(limit)
                .map(AppointmentService::summary)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<SpecialistAppointmentQueryPort.AppointmentSummary> timeline(UUID specialistAccountId,
            UUID participantAccountId, Instant fromInclusive, Instant toExclusive,
            SpecialistAppointmentQueryPort.SeekCursor after, int limit) {
        if (specialistAccountId == null || participantAccountId == null || fromInclusive == null || toExclusive == null
                || !toExclusive.isAfter(fromInclusive) || limit < 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "specialist, participant, range and limit are required");
        }
        PageRequest page = PageRequest.of(0, limit);
        List<Appointment> result = after == null
                ? appointments.findTimelineInitial(specialistAccountId, participantAccountId, fromInclusive, toExclusive, page)
                : after.recordedAt() == null
                ? appointments.findTimelineAfterUnrecorded(specialistAccountId, participantAccountId, fromInclusive, toExclusive,
                        after.effectiveFrom(), page)
                : appointments.findTimelineAfterRecorded(specialistAccountId, participantAccountId, fromInclusive, toExclusive,
                        after.effectiveFrom(), after.recordedAt(), after.eventId(), page);
        return result.stream().map(AppointmentService::summary).toList();
    }

    private AppointmentView changeStatus(String subject, UUID id, String key, VersionCommand command, String operation, boolean cancelling) {
        UUID specialist = specialist(subject); String idempotencyKey = key(key); String scopedOperation = operation + ":" + id;
        return replay(specialist, scopedOperation, idempotencyKey).orElseGet(() -> {
            Appointment appointment = owned(specialist, id); version(appointment, command == null ? null : command.version());
            relationships.requireActive(specialist, appointment.participantAccountId);
            if (appointment.status == Appointment.Status.CANCELLED || appointment.status == Appointment.Status.COMPLETED) conflict("appointment cannot transition from its current status");
            if (cancelling) appointment.cancel(clock.instant()); else appointment.noShow(clock.instant());
            Appointment saved = saveConflict(appointment); remember(specialist, scopedOperation, idempotencyKey, id);
            audit.record(subject, "APPOINTMENT_" + operation, "Appointment", id); return view(saved);
        });
    }
    private Optional<AppointmentView> replay(UUID specialist, String operation, String key) {
        return idempotency.findBySpecialistAccountIdAndOperationAndIdempotencyKey(specialist, operation, key)
                .flatMap(item -> appointments.findById(item.appointmentId)).map(AppointmentService::view);
    }
    private void remember(UUID specialist, String operation, String key, UUID appointment) {
        try { idempotency.saveAndFlush(new AppointmentIdempotency(specialist, operation, key, appointment, clock.instant())); }
        catch (DataIntegrityViolationException duplicate) { /* concurrent equivalent command is replayed by the caller */ }
    }
    private Appointment saveConflict(Appointment appointment) {
        try { return appointments.saveAndFlush(appointment); }
        catch (ObjectOptimisticLockingFailureException conflict) { throw conflict("appointment version is stale"); }
    }
    private void conflictIfOverlapping(UUID specialist, Instant start, Instant end, UUID excluded) {
        if (appointments.hasActiveOverlap(specialist, start, end, excluded)) conflict("appointment overlaps an existing appointment");
    }
    private Appointment owned(UUID specialist, UUID id) {
        Appointment appointment = appointments.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "appointment not found"));
        if (!appointment.specialistAccountId.equals(specialist)) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "appointment not found");
        return appointment;
    }
    private UUID specialist(String subject) {
        var account = accounts.requireActive(subject);
        if (!account.hasProfile(ProfileType.SPECIALIST)) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "specialist profile is required");
        return account.id();
    }
    private static void version(Appointment appointment, Long expected) { if (expected == null || expected != appointment.version) conflict("appointment version is stale"); }
    private static Values values(CreateCommand command) { if (command == null) bad("appointment command is required"); return values(command.participantId(), command.startsAt(), command.endsAt(), command.type(), command.locationMode(), command.location(), command.shortPurpose()); }
    private static Values values(UpdateCommand command) { if (command == null) bad("appointment command is required"); return values(command.participantId(), command.startsAt(), command.endsAt(), command.type(), command.locationMode(), command.location(), command.shortPurpose()); }
    private static Values values(UUID participant, Instant starts, Instant ends, Appointment.Type type, Appointment.LocationMode mode, String location, String purpose) {
        if (participant == null || starts == null || ends == null || !ends.isAfter(starts) || type == null || mode == null) bad("participantId, boundaries, type and locationMode are required");
        return new Values(participant, starts, ends, type, mode, optional(location, 160, "location"), optional(purpose, 500, "shortPurpose"));
    }
    private static String key(String value) { if (value == null || value.isBlank() || value.trim().length() > 120) bad("Idempotency-Key is required"); return value.trim(); }
    private static String optional(String value, int max, String field) { if (value == null || value.isBlank()) return null; if (value.trim().length() > max) bad(field + " is too long"); return value.trim(); }
    private static void bad(String detail) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, detail); }
    private static ResponseStatusException conflict(String detail) { return new ResponseStatusException(HttpStatus.CONFLICT, detail); }
    private static AppointmentView view(Appointment appointment) { return view(appointment, null); }
    private static SpecialistAppointmentQueryPort.AppointmentSummary summary(Appointment appointment) {
        return new SpecialistAppointmentQueryPort.AppointmentSummary(appointment.id, appointment.startsAt, appointment.endsAt,
                appointment.type.name(), appointment.status.name(), appointment.shortPurpose, appointment.createdAt, appointment.updatedAt);
    }
    private static AppointmentView view(Appointment appointment, Instant now) { return new AppointmentView(appointment.id, appointment.participantAccountId, appointment.startsAt, appointment.endsAt, appointment.type, appointment.status, appointment.locationMode, appointment.location, appointment.shortPurpose, now != null && !appointment.startsAt.isAfter(now) && appointment.endsAt.isAfter(now), false, actions(appointment), appointment.version); }
    private static List<String> actions(Appointment appointment) { return appointment.status == Appointment.Status.SCHEDULED || appointment.status == Appointment.Status.CONFIRMED || appointment.status == Appointment.Status.IN_PROGRESS ? List.of("OPEN_APPOINTMENT", "OPEN_PARTICIPANT", "CANCEL", "MARK_NO_SHOW") : List.of("OPEN_APPOINTMENT", "OPEN_PARTICIPANT"); }
    private record Values(UUID participantId, Instant startsAt, Instant endsAt, Appointment.Type type, Appointment.LocationMode locationMode, String location, String shortPurpose) { }
    public record CreateCommand(UUID participantId, Instant startsAt, Instant endsAt, Appointment.Type type, Appointment.LocationMode locationMode, String location, String shortPurpose) { }
    public record UpdateCommand(UUID participantId, Instant startsAt, Instant endsAt, Appointment.Type type, Appointment.LocationMode locationMode, String location, String shortPurpose, Long version) { }
    public record VersionCommand(Long version) { }
    public record AppointmentView(UUID appointmentId, UUID participantId, Instant startsAt, Instant endsAt, Appointment.Type type, Appointment.Status status, Appointment.LocationMode locationMode, String location, String shortPurpose, boolean isCurrent, boolean isNext, List<String> availableActions, long version) { }
}
