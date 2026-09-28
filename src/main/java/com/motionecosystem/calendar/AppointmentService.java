package com.motionecosystem.calendar;

import com.motionecosystem.calendar.api.SpecialistAppointmentQueryPort;
import com.motionecosystem.calendar.api.SpecialistOverdueAppointmentQueryPort;
import com.motionecosystem.calendar.api.CalendarSpecialistContextPort;
import com.motionecosystem.calendar.api.AppointmentPlannedSessionValidationPort;
import com.motionecosystem.availability.RecurringAvailabilityService;
import com.motionecosystem.audit.AuditRecorder;
import com.motionecosystem.identityaccess.api.CurrentAccountService;
import com.motionecosystem.identityaccess.api.ProfileType;
import java.time.*;
import java.util.*;
import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AppointmentService implements SpecialistAppointmentQueryPort, SpecialistOverdueAppointmentQueryPort {
    private static final int OVERDUE_OUTCOME_LIMIT = 20;
    private final AppointmentRepository appointments;
    private final AppointmentEventRepository events;
    private final AppointmentIdempotencyRepository idempotency;
    private final CurrentAccountService accounts;
    private final CalendarSpecialistContextPort specialistContext;
    private final RecurringAvailabilityService availability;
    private final AppointmentPlannedSessionValidationPort appointmentSessions;
    private final AuditRecorder audit;
    private final Clock clock;
    private final AppointmentLifecyclePolicy lifecycle = new AppointmentLifecyclePolicy();

    @Autowired
    AppointmentService(AppointmentRepository appointments, AppointmentEventRepository events,
                       AppointmentIdempotencyRepository idempotency, CurrentAccountService accounts,
                       CalendarSpecialistContextPort specialistContext, RecurringAvailabilityService availability,
                       AppointmentPlannedSessionValidationPort appointmentSessions, AuditRecorder audit, Clock clock) {
        this.appointments = appointments;
        this.events = events;
        this.idempotency = idempotency;
        this.accounts = accounts;
        this.specialistContext = specialistContext;
        this.availability = availability;
        this.appointmentSessions = appointmentSessions;
        this.audit = audit;
        this.clock = clock;
    }

    AppointmentService(AppointmentRepository appointments, AppointmentEventRepository events,
                       AppointmentIdempotencyRepository idempotency, CurrentAccountService accounts,
                       CalendarSpecialistContextPort specialistContext, RecurringAvailabilityService availability,
                       AuditRecorder audit, Clock clock) {
        this(appointments, events, idempotency, accounts, specialistContext, availability,
                (subject, participantId, plannedSessionId) -> { }, audit, clock);
    }

    @Transactional
    public AppointmentView create(String subject, String key, CreateCommand command) {
        UUID specialist = specialist(subject); String idempotencyKey = key(key);
        return replay(specialist, "CREATE", idempotencyKey).orElseGet(() -> {
            Values values = values(command); specialistContext.requireActiveRelationship(specialist, values.participantId());
            requireLinkableSession(subject, values.participantId(), values.plannedSessionId());
            requireWithinAvailability(specialist, values.startsAt(), values.endsAt());
            conflictIfOverlapping(specialist, values.startsAt(), values.endsAt(), UUID.randomUUID());
            Appointment saved = saveConflict(new Appointment(specialist, values.participantId(), values.startsAt(), values.endsAt(),
                    values.type(), values.locationMode(), values.location(), values.shortPurpose(), values.plannedSessionId(), specialist, clock.instant()));
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
            specialistContext.requireActiveRelationship(specialist, appointment.participantId);
            if (!Objects.equals(appointment.plannedSessionId, values.plannedSessionId())) {
                requireLinkableSession(subject, appointment.participantId, values.plannedSessionId());
            }
            requireWithinAvailability(specialist, values.startsAt(), values.endsAt());
            conflictIfOverlapping(specialist, values.startsAt(), values.endsAt(), appointment.id);
            Instant previousStartsAt = appointment.startsAt, previousEndsAt = appointment.endsAt;
            Appointment.Status previousStatus = appointment.status;
            Instant now = clock.instant();
            appointment.update(values.startsAt(), values.endsAt(), values.type(), values.locationMode(), values.location(), values.shortPurpose(), values.plannedSessionId(), now);
            Appointment saved = saveConflict(appointment); remember(specialist, "UPDATE:" + id, idempotencyKey, id);
            record(saved, previousStartsAt.equals(saved.startsAt) && previousEndsAt.equals(saved.endsAt)
                    ? AppointmentEvent.Type.UPDATED : AppointmentEvent.Type.RESCHEDULED, previousStatus, saved.startsAt, specialist, previousStartsAt, previousEndsAt);
            audit.record(subject, "APPOINTMENT_UPDATED", "Appointment", id); return view(saved);
        });
    }

    @Transactional
    public AppointmentView cancel(String subject, UUID id, String key, AppointmentVersionCommand command) { return changeStatus(subject, id, key, command, "CANCEL", AppointmentLifecyclePolicy.Action.CANCEL); }
    @Transactional
    public AppointmentView start(String subject, UUID id, String key, AppointmentVersionCommand command) { return changeStatus(subject, id, key, command, "START", AppointmentLifecyclePolicy.Action.START); }
    @Transactional
    public AppointmentView noShow(String subject, UUID id, String key, AppointmentVersionCommand command) { return changeStatus(subject, id, key, command, "NO_SHOW", AppointmentLifecyclePolicy.Action.MARK_NO_SHOW); }
    @Transactional
    public AppointmentView complete(String subject, UUID id, String key, AppointmentVersionCommand command) { return changeStatus(subject, id, key, command, "COMPLETE", AppointmentLifecyclePolicy.Action.COMPLETE); }

    @Transactional(readOnly = true)
    @Override
    public List<SpecialistAppointmentQueryPort.OperationalAppointment> inRange(UUID specialist, Instant start, Instant end, Set<UUID> activeParticipants, Instant now) {
        return appointments.findIntersecting(specialist, start, end).stream()
                .filter(appointment -> activeParticipants.contains(appointment.participantId))
                .map(appointment -> operationalView(appointment, now)).toList();
    }

    @Transactional(readOnly = true)
    @Override
    public List<SpecialistAppointmentQueryPort.OperationalAppointment> inProgress(UUID specialist, Set<UUID> activeParticipants, Instant now) {
        if (specialist == null || activeParticipants == null || activeParticipants.isEmpty()) return List.of();
        return appointments.findInProgress(specialist, activeParticipants).stream()
                .map(appointment -> operationalView(appointment, now)).toList();
    }

    @Transactional(readOnly = true)
    public AppointmentView detail(String subject, UUID id) {
        UUID specialist = specialist(subject);
        Appointment appointment = owned(specialist, id);
        specialistContext.requireActiveRelationship(specialist, appointment.participantId);
        return view(appointment);
    }

    @Override
    @Transactional(readOnly = true)
    public List<OverdueAppointment> overdueOutcomeAppointments(UUID specialistAccountId, Set<UUID> activeParticipantIds, Instant now) {
        if (specialistAccountId == null || activeParticipantIds == null || activeParticipantIds.isEmpty() || now == null) return List.of();
        return appointments.findOverdueOutcomeAppointments(specialistAccountId, activeParticipantIds, now,
                        lifecycle.outcomeOutstandingStatuses(), org.springframework.data.domain.PageRequest.of(0, OVERDUE_OUTCOME_LIMIT)).stream()
                .filter(appointment -> lifecycle.allows(AppointmentLifecyclePolicy.Action.COMPLETE, appointment, now))
                .map(appointment -> new OverdueAppointment(appointment.id, appointment.participantId, appointment.endsAt,
                        appointment.type.name(), appointment.status.name(), latestEventId(appointment.id)))
                .toList();
    }

    @Transactional(readOnly = true)
    @Override
    public List<SpecialistAppointmentQueryPort.BlockingTimeRange> blockingInRange(UUID specialist, Instant start, Instant end) {
        return appointments.findIntersecting(specialist, start, end).stream()
                .filter(appointment -> appointment.status != Appointment.Status.CANCELLED)
                .map(appointment -> new SpecialistAppointmentQueryPort.BlockingTimeRange(appointment.startsAt, appointment.endsAt))
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
        return appointments.findForParticipantIncludingInProgress(specialistAccountId, participantId, fromInclusive, toExclusive).stream()
                .sorted(Comparator.comparing((Appointment item) -> item.startsAt).reversed().thenComparing(item -> item.id))
                .limit(limit)
                .map(appointment -> summary(appointment, clock.instant()))
                .toList();
    }

    private AppointmentView changeStatus(String subject, UUID id, String key, AppointmentVersionCommand command, String operation, AppointmentLifecyclePolicy.Action action) {
        UUID specialist = specialist(subject); String idempotencyKey = key(key); String scopedOperation = operation + ":" + id;
        return replay(specialist, scopedOperation, idempotencyKey).orElseGet(() -> {
            Appointment appointment = owned(specialist, id); version(appointment, command == null ? null : command.version());
            specialistContext.requireActiveRelationship(specialist, appointment.participantId);
            Instant now = clock.instant(); requireAllowed(action, appointment, now);
            Appointment.Status fromStatus = appointment.status;
            switch (action) {
                case CANCEL -> appointment.cancel(now);
                case START -> appointment.start(now);
                case COMPLETE -> appointment.complete(now);
                case MARK_NO_SHOW -> appointment.noShow(now);
                default -> throw new IllegalArgumentException("unsupported lifecycle action");
            }
            Appointment saved = saveConflict(appointment); remember(specialist, scopedOperation, idempotencyKey, id);
            record(saved, switch (action) {
                case CANCEL -> AppointmentEvent.Type.CANCELLED;
                case START -> AppointmentEvent.Type.STARTED;
                case COMPLETE -> AppointmentEvent.Type.COMPLETED;
                case MARK_NO_SHOW -> AppointmentEvent.Type.NO_SHOW;
                default -> throw new IllegalArgumentException("unsupported lifecycle action");
            }, fromStatus, now, specialist, null, null);
            String auditAction = switch (operation) {
                case "START" -> "APPOINTMENT_STARTED";
                case "COMPLETE" -> "APPOINTMENT_COMPLETED";
                default -> "APPOINTMENT_" + operation;
            };
            audit.record(subject, auditAction, "Appointment", id); return view(saved);
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
        catch (DataIntegrityViolationException integrity) {
            if (hasConstraint(integrity, "uq_calendar_appointment_planned_session")) {
                throw conflict("planned session is already linked to another appointment");
            }
            throw integrity;
        }
    }
    private static boolean hasConstraint(Throwable failure, String constraint) {
        for (Throwable current = failure; current != null; current = current.getCause()) {
            if (current.getMessage() != null && current.getMessage().contains(constraint)) return true;
        }
        return false;
    }
    private void record(Appointment appointment, AppointmentEvent.Type type, Appointment.Status fromStatus, Instant effectiveAt,
                        UUID actor, Instant previousStartsAt, Instant previousEndsAt) {
        events.save(new AppointmentEvent(appointment, type, fromStatus, effectiveAt, clock.instant(), actor, previousStartsAt, previousEndsAt));
    }
    private UUID latestEventId(UUID appointmentId) {
        return events.findLatestEventId(appointmentId, org.springframework.data.domain.PageRequest.of(0, 1)).stream().findFirst().orElse(null);
    }
    private void conflictIfOverlapping(UUID specialist, Instant start, Instant end, UUID excluded) {
        if (appointments.hasActiveOverlap(specialist, start, end, excluded)) conflict("appointment overlaps an existing appointment");
    }
    private void requireWithinAvailability(UUID specialist, Instant start, Instant end) {
        ZoneId zone = specialistContext.findSpecialist(specialist)
                .map(CalendarSpecialistContextPort.SpecialistContext::timeZoneId)
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
    private static void version(Appointment appointment, Long expected) { if (expected == null || expected.longValue() != appointment.version) throw conflict("appointment version is stale"); }
    private void requireAllowed(AppointmentLifecyclePolicy.Action action, Appointment appointment, Instant now) {
        if (!lifecycle.allows(action, appointment, now)) throw conflict("appointment cannot " + action.name().toLowerCase().replace('_', '-') + " from its current status or time");
    }
    private static Values values(CreateCommand command) { if (command == null) bad("appointment command is required"); return values(command.participantId(), command.startsAt(), command.endsAt(), command.type(), command.locationMode(), command.location(), command.shortPurpose(), command.plannedSessionId()); }
    private static Values values(UpdateCommand command) { if (command == null) bad("appointment command is required"); return values(command.participantId(), command.startsAt(), command.endsAt(), command.type(), command.locationMode(), command.location(), command.shortPurpose(), command.plannedSessionId()); }
    private static Values values(UUID participant, Instant starts, Instant ends, Appointment.Type type, Appointment.LocationMode mode, String location, String purpose, UUID plannedSessionId) {
        if (participant == null || starts == null || ends == null || !ends.isAfter(starts) || type == null || mode == null) bad("participantId, boundaries, type and locationMode are required");
        return new Values(participant, starts, ends, type, mode, optional(location, 160, "location"), optional(purpose, 500, "shortPurpose"), plannedSessionId);
    }
    private static String key(String value) { if (value == null || value.isBlank() || value.trim().length() > 120) bad("Idempotency-Key is required"); return value.trim(); }
    private static String optional(String value, int max, String field) { if (value == null || value.isBlank()) return null; if (value.trim().length() > max) bad(field + " is too long"); return value.trim(); }
    private static ZoneId zone(String value) { try { return ZoneId.of(value); } catch (RuntimeException invalid) { throw conflict("specialist time zone is invalid"); } }
    private static void bad(String detail) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, detail); }
    private static ResponseStatusException conflict(String detail) { return new ResponseStatusException(HttpStatus.CONFLICT, detail); }
    private AppointmentView view(Appointment appointment) { return view(appointment, clock.instant()); }
    private SpecialistAppointmentQueryPort.AppointmentSummary summary(Appointment appointment, Instant now) {
        return new SpecialistAppointmentQueryPort.AppointmentSummary(appointment.id, appointment.startsAt, appointment.endsAt,
                appointment.type.name(), appointment.status.name(), appointment.shortPurpose, appointment.plannedSessionId,
                lifecycle.availableActions(appointment, now), appointment.version, appointment.createdAt, appointment.updatedAt);
    }
    private void requireLinkableSession(String subject, UUID participantId, UUID plannedSessionId) { if (plannedSessionId != null) appointmentSessions.requireLinkable(subject, participantId, plannedSessionId); }
    private AppointmentView view(Appointment appointment, Instant now) { return new AppointmentView(appointment.id, appointment.participantId, appointment.startsAt, appointment.endsAt, appointment.type, appointment.status, appointment.locationMode, appointment.location, appointment.shortPurpose, appointment.plannedSessionId, now != null && !appointment.startsAt.isAfter(now) && appointment.endsAt.isAfter(now), false, lifecycle.availableActions(appointment, now), appointment.version); }
    private SpecialistAppointmentQueryPort.OperationalAppointment operationalView(Appointment appointment, Instant now) { return new SpecialistAppointmentQueryPort.OperationalAppointment(appointment.id, appointment.participantId, appointment.startsAt, appointment.endsAt, appointment.type.name(), appointment.status.name(), appointment.locationMode.name(), appointment.location, appointment.shortPurpose, appointment.plannedSessionId, appointment.status == Appointment.Status.IN_PROGRESS || now != null && !appointment.startsAt.isAfter(now) && appointment.endsAt.isAfter(now), lifecycle.availableActions(appointment, now), appointment.version); }
    private record Values(UUID participantId, Instant startsAt, Instant endsAt, Appointment.Type type, Appointment.LocationMode locationMode, String location, String shortPurpose, UUID plannedSessionId) { }
    public record CreateCommand(UUID participantId, Instant startsAt, Instant endsAt, Appointment.Type type, Appointment.LocationMode locationMode, String location, String shortPurpose, UUID plannedSessionId) { public CreateCommand(UUID participantId, Instant startsAt, Instant endsAt, Appointment.Type type, Appointment.LocationMode locationMode, String location, String shortPurpose) { this(participantId, startsAt, endsAt, type, locationMode, location, shortPurpose, null); } }
    public record UpdateCommand(UUID participantId, Instant startsAt, Instant endsAt, Appointment.Type type, Appointment.LocationMode locationMode, String location, String shortPurpose, UUID plannedSessionId, Long version) { public UpdateCommand(UUID participantId, Instant startsAt, Instant endsAt, Appointment.Type type, Appointment.LocationMode locationMode, String location, String shortPurpose, Long version) { this(participantId, startsAt, endsAt, type, locationMode, location, shortPurpose, null, version); } }
    @Schema(name = "AppointmentVersionCommand")
    public record AppointmentVersionCommand(Long version) { }
    public record AppointmentView(UUID appointmentId, UUID participantId, Instant startsAt, Instant endsAt, Appointment.Type type, Appointment.Status status, Appointment.LocationMode locationMode, String location, String shortPurpose, UUID plannedSessionId, boolean isCurrent, boolean isNext, List<String> availableActions, long version) { }
}
