package com.motionecosystem.calendar;

import com.motionecosystem.calendar.api.SpecialistAppointmentQueryPort;
import com.motionecosystem.availability.RecurringAvailabilityService;
import com.motionecosystem.audit.AuditRecorder;
import com.motionecosystem.identityaccess.api.CurrentAccountService;
import com.motionecosystem.identityaccess.api.ProfileType;
import com.motionecosystem.specialist.SpecialistRelationshipService;
import com.motionecosystem.specialist.SpecialistProfileService;
import java.time.*;
import java.util.*;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.RequiredArgsConstructor;
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
    private final AppointmentEventRepository events;
    private final AppointmentIdempotencyRepository idempotency;
    private final CurrentAccountService accounts;
    private final SpecialistRelationshipService relationships;
    private final RecurringAvailabilityService availability;
    private final SpecialistProfileService profiles;
    private final AuditRecorder audit;
    private final Clock clock;
    private final AppointmentLifecyclePolicy lifecycle = new AppointmentLifecyclePolicy();

    @Transactional
    public AppointmentView create(String subject, String key, CreateCommand command) {
        UUID specialist = specialist(subject); String idempotencyKey = key(key);
        return replay(specialist, "CREATE", idempotencyKey).orElseGet(() -> {
            Values values = values(command); relationships.requireActive(specialist, values.participantId());
            requireWithinAvailability(specialist, values.startsAt(), values.endsAt());
            conflictIfOverlapping(specialist, values.startsAt(), values.endsAt(), UUID.randomUUID());
            Appointment saved = appointments.saveAndFlush(new Appointment(specialist, values.participantId(), values.startsAt(), values.endsAt(),
                    values.type(), values.locationMode(), values.location(), values.shortPurpose(), specialist, clock.instant()));
            record(saved, AppointmentEvent.Type.CREATED, null, saved.startsAt, specialist, null, null);
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
            requireAllowed(AppointmentLifecyclePolicy.Action.UPDATE, appointment, clock.instant());
            Values values = values(command); if (!appointment.participantId.equals(values.participantId())) bad("participantId cannot be changed");
            relationships.requireActive(specialist, appointment.participantId);
            requireWithinAvailability(specialist, values.startsAt(), values.endsAt());
            conflictIfOverlapping(specialist, values.startsAt(), values.endsAt(), appointment.id);
            Instant previousStartsAt = appointment.startsAt, previousEndsAt = appointment.endsAt;
            Appointment.Status previousStatus = appointment.status;
            Instant now = clock.instant();
            appointment.update(values.startsAt(), values.endsAt(), values.type(), values.locationMode(), values.location(), values.shortPurpose(), now);
            Appointment saved = saveConflict(appointment); remember(specialist, "UPDATE:" + id, idempotencyKey, id);
            record(saved, previousStartsAt.equals(saved.startsAt) && previousEndsAt.equals(saved.endsAt)
                    ? AppointmentEvent.Type.UPDATED : AppointmentEvent.Type.RESCHEDULED, previousStatus, saved.startsAt, specialist, previousStartsAt, previousEndsAt);
            audit.record(subject, "APPOINTMENT_UPDATED", "Appointment", id); return view(saved);
        });
    }

    @Transactional
    public AppointmentView cancel(String subject, UUID id, String key, AppointmentVersionCommand command) { return changeStatus(subject, id, key, command, "CANCEL", AppointmentLifecyclePolicy.Action.CANCEL); }
    @Transactional
    public AppointmentView noShow(String subject, UUID id, String key, AppointmentVersionCommand command) { return changeStatus(subject, id, key, command, "NO_SHOW", AppointmentLifecyclePolicy.Action.MARK_NO_SHOW); }
    @Transactional
    public AppointmentView complete(String subject, UUID id, String key, AppointmentVersionCommand command) { return changeStatus(subject, id, key, command, "COMPLETE", AppointmentLifecyclePolicy.Action.COMPLETE); }

    @Transactional(readOnly = true)
    public List<AppointmentView> inRange(UUID specialist, Instant start, Instant end, Set<UUID> activeParticipants, Instant now) {
        return appointments.findIntersecting(specialist, start, end).stream()
                .filter(appointment -> activeParticipants.contains(appointment.participantId))
                .map(appointment -> view(appointment, now)).toList();
    }

    @Transactional(readOnly = true)
    public List<TimeRange> blockingInRange(UUID specialist, Instant start, Instant end) {
        return appointments.findIntersecting(specialist, start, end).stream()
                .filter(appointment -> appointment.status != Appointment.Status.CANCELLED)
                .map(appointment -> new TimeRange(appointment.startsAt, appointment.endsAt))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<SpecialistAppointmentQueryPort.AppointmentSummary> findForParticipant(UUID specialistAccountId,
            UUID participantId, Instant fromInclusive, Instant toExclusive, int limit) {
        if (specialistAccountId == null || participantId == null || fromInclusive == null || toExclusive == null
                || !toExclusive.isAfter(fromInclusive) || limit < 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "specialist, participant, range and limit are required");
        }
        return appointments.findIntersecting(specialistAccountId, fromInclusive, toExclusive).stream()
                .filter(item -> participantId.equals(item.participantId))
                .sorted(Comparator.comparing((Appointment item) -> item.startsAt).reversed().thenComparing(item -> item.id))
                .limit(limit)
                .map(AppointmentService::summary)
                .toList();
    }

    private AppointmentView changeStatus(String subject, UUID id, String key, AppointmentVersionCommand command, String operation, AppointmentLifecyclePolicy.Action action) {
        UUID specialist = specialist(subject); String idempotencyKey = key(key); String scopedOperation = operation + ":" + id;
        return replay(specialist, scopedOperation, idempotencyKey).orElseGet(() -> {
            Appointment appointment = owned(specialist, id); version(appointment, command == null ? null : command.version());
            relationships.requireActive(specialist, appointment.participantId);
            Instant now = clock.instant(); requireAllowed(action, appointment, now);
            Appointment.Status fromStatus = appointment.status;
            switch (action) {
                case CANCEL -> appointment.cancel(now);
                case COMPLETE -> appointment.complete(now);
                case MARK_NO_SHOW -> appointment.noShow(now);
                default -> throw new IllegalArgumentException("unsupported lifecycle action");
            }
            Appointment saved = saveConflict(appointment); remember(specialist, scopedOperation, idempotencyKey, id);
            record(saved, switch (action) {
                case CANCEL -> AppointmentEvent.Type.CANCELLED;
                case COMPLETE -> AppointmentEvent.Type.COMPLETED;
                case MARK_NO_SHOW -> AppointmentEvent.Type.NO_SHOW;
                default -> throw new IllegalArgumentException("unsupported lifecycle action");
            }, fromStatus, now, specialist, null, null);
            audit.record(subject, operation.equals("COMPLETE") ? "APPOINTMENT_COMPLETED" : "APPOINTMENT_" + operation, "Appointment", id); return view(saved);
        });
    }
    private Optional<AppointmentView> replay(UUID specialist, String operation, String key) {
        return idempotency.findBySpecialistAccountIdAndOperationAndIdempotencyKey(specialist, operation, key)
                .flatMap(item -> appointments.findById(item.appointmentId)).map(this::view);
    }
    private void remember(UUID specialist, String operation, String key, UUID appointment) {
        try { idempotency.saveAndFlush(new AppointmentIdempotency(specialist, operation, key, appointment, clock.instant())); }
        catch (DataIntegrityViolationException duplicate) { /* concurrent equivalent command is replayed by the caller */ }
    }
    private Appointment saveConflict(Appointment appointment) {
        try { return appointments.saveAndFlush(appointment); }
        catch (ObjectOptimisticLockingFailureException conflict) { throw conflict("appointment version is stale"); }
    }
    private void record(Appointment appointment, AppointmentEvent.Type type, Appointment.Status fromStatus, Instant effectiveAt,
                        UUID actor, Instant previousStartsAt, Instant previousEndsAt) {
        events.save(new AppointmentEvent(appointment, type, fromStatus, effectiveAt, clock.instant(), actor, previousStartsAt, previousEndsAt));
    }
    private void conflictIfOverlapping(UUID specialist, Instant start, Instant end, UUID excluded) {
        if (appointments.hasActiveOverlap(specialist, start, end, excluded)) conflict("appointment overlaps an existing appointment");
    }
    private void requireWithinAvailability(UUID specialist, Instant start, Instant end) {
        ZoneId zone = profiles.find(specialist)
                .map(SpecialistProfileService.ProfileView::timeZoneId)
                .map(AppointmentService::zone)
                .orElseThrow(() -> conflict("specialist profile is required"));
        LocalDate date = start.atZone(zone).toLocalDate();
        boolean contained = availability.windows(specialist, date).stream()
                .anyMatch(window -> !start.isBefore(window.startsAt()) && !end.isAfter(window.endsAt()));
        if (!contained) throw conflict("appointment must be within specialist availability");
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
    private void requireAllowed(AppointmentLifecyclePolicy.Action action, Appointment appointment, Instant now) {
        if (!lifecycle.allows(action, appointment, now)) throw conflict("appointment cannot " + action.name().toLowerCase().replace('_', '-') + " from its current status or time");
    }
    private static Values values(CreateCommand command) { if (command == null) bad("appointment command is required"); return values(command.participantId(), command.startsAt(), command.endsAt(), command.type(), command.locationMode(), command.location(), command.shortPurpose()); }
    private static Values values(UpdateCommand command) { if (command == null) bad("appointment command is required"); return values(command.participantId(), command.startsAt(), command.endsAt(), command.type(), command.locationMode(), command.location(), command.shortPurpose()); }
    private static Values values(UUID participant, Instant starts, Instant ends, Appointment.Type type, Appointment.LocationMode mode, String location, String purpose) {
        if (participant == null || starts == null || ends == null || !ends.isAfter(starts) || type == null || mode == null) bad("participantId, boundaries, type and locationMode are required");
        return new Values(participant, starts, ends, type, mode, optional(location, 160, "location"), optional(purpose, 500, "shortPurpose"));
    }
    private static String key(String value) { if (value == null || value.isBlank() || value.trim().length() > 120) bad("Idempotency-Key is required"); return value.trim(); }
    private static String optional(String value, int max, String field) { if (value == null || value.isBlank()) return null; if (value.trim().length() > max) bad(field + " is too long"); return value.trim(); }
    private static ZoneId zone(String value) { try { return ZoneId.of(value); } catch (RuntimeException invalid) { throw conflict("specialist time zone is invalid"); } }
    private static void bad(String detail) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, detail); }
    private static ResponseStatusException conflict(String detail) { return new ResponseStatusException(HttpStatus.CONFLICT, detail); }
    private AppointmentView view(Appointment appointment) { return view(appointment, clock.instant()); }
    private static SpecialistAppointmentQueryPort.AppointmentSummary summary(Appointment appointment) {
        return new SpecialistAppointmentQueryPort.AppointmentSummary(appointment.id, appointment.startsAt, appointment.endsAt,
                appointment.type.name(), appointment.status.name(), appointment.shortPurpose, appointment.createdAt, appointment.updatedAt);
    }
    private AppointmentView view(Appointment appointment, Instant now) { return new AppointmentView(appointment.id, appointment.participantId, appointment.startsAt, appointment.endsAt, appointment.type, appointment.status, appointment.locationMode, appointment.location, appointment.shortPurpose, now != null && !appointment.startsAt.isAfter(now) && appointment.endsAt.isAfter(now), false, lifecycle.availableActions(appointment, now), appointment.version); }
    private record Values(UUID participantId, Instant startsAt, Instant endsAt, Appointment.Type type, Appointment.LocationMode locationMode, String location, String shortPurpose) { }
    public record CreateCommand(UUID participantId, Instant startsAt, Instant endsAt, Appointment.Type type, Appointment.LocationMode locationMode, String location, String shortPurpose) { }
    public record UpdateCommand(UUID participantId, Instant startsAt, Instant endsAt, Appointment.Type type, Appointment.LocationMode locationMode, String location, String shortPurpose, Long version) { }
    @Schema(name = "AppointmentVersionCommand")
    public record AppointmentVersionCommand(Long version) { }
    public record TimeRange(Instant startsAt, Instant endsAt) { }
    public record AppointmentView(UUID appointmentId, UUID participantId, Instant startsAt, Instant endsAt, Appointment.Type type, Appointment.Status status, Appointment.LocationMode locationMode, String location, String shortPurpose, boolean isCurrent, boolean isNext, List<String> availableActions, long version) { }
}
