package com.motionecosystem.calendar;

import com.motionecosystem.calendar.api.SpecialistAppointmentHistoryQueryPort;
import com.motionecosystem.identityaccess.api.CurrentAccountService;
import com.motionecosystem.identityaccess.api.ProfileType;
import com.motionecosystem.identityaccess.api.SpecialistAuthorizationPort;
import com.motionecosystem.identityaccess.api.SpecialistAuthorizationPort.ActingContext;
import com.motionecosystem.identityaccess.api.SpecialistAuthorizationPort.Capability;
import com.motionecosystem.identityaccess.api.SpecialistAuthorizationPort.ProfessionalRole;
import com.motionecosystem.identityaccess.api.SpecialistAuthorizationPort.Purpose;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
class AppointmentHistoryQueryAdapter implements SpecialistAppointmentHistoryQueryPort {
    private final AppointmentRepository appointments;
    private final AppointmentEventRepository events;
    private final CurrentAccountService accounts;
    private final SpecialistAuthorizationPort authorization;

    @Override
    @Transactional(readOnly = true)
    public List<AppointmentSnapshot> appointmentCandidates(String subject, UUID participantId, ActingContext context,
                                                           Instant fromInclusive, Instant toExclusive) {
        Optional<UUID> specialist = authorize(subject, participantId, context);
        if (specialist.isEmpty()) return List.of();
        requireRange(fromInclusive, toExclusive);
        return appointments.findHistoryCandidates(specialist.get(), participantId, fromInclusive, toExclusive).stream()
                .map(AppointmentHistoryQueryAdapter::snapshot).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<AppointmentSnapshot> appointmentsByIds(String subject, UUID participantId, ActingContext context,
                                                       Set<UUID> appointmentIds) {
        Optional<UUID> specialist = authorize(subject, participantId, context);
        if (specialist.isEmpty() || appointmentIds == null || appointmentIds.isEmpty()) return List.of();
        return appointments.findHistoryByIds(specialist.get(), participantId, appointmentIds).stream()
                .map(AppointmentHistoryQueryAdapter::snapshot).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<AppointmentSnapshot> appointmentsByPlannedSessionIds(String subject, UUID participantId, ActingContext context,
                                                                     Set<UUID> plannedSessionIds) {
        Optional<UUID> specialist = authorize(subject, participantId, context);
        if (specialist.isEmpty() || plannedSessionIds == null || plannedSessionIds.isEmpty()) return List.of();
        return appointments.findHistoryByPlannedSessionIds(specialist.get(), participantId, plannedSessionIds).stream()
                .map(AppointmentHistoryQueryAdapter::snapshot).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<LifecycleSnapshot> lifecycleByAppointmentIds(String subject, UUID participantId, ActingContext context,
                                                              Set<UUID> appointmentIds) {
        Optional<UUID> specialist = authorize(subject, participantId, context);
        if (specialist.isEmpty() || appointmentIds == null || appointmentIds.isEmpty()) return List.of();
        return events.findHistoryByAppointmentIds(specialist.get(), participantId, appointmentIds).stream()
                .map(event -> new LifecycleSnapshot(event.getEventId(), event.getAppointmentId(), event.getEffectiveAt(),
                        event.getRecordedAt(), event.getEventType(), event.getFromStatus(), event.getToStatus()))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<LifecycleSnapshot> lifecycleByEventIds(String subject, UUID participantId, ActingContext context,
                                                        Set<UUID> eventIds) {
        Optional<UUID> specialist = authorize(subject, participantId, context);
        if (specialist.isEmpty() || eventIds == null || eventIds.isEmpty()) return List.of();
        return events.findHistoryByEventIds(specialist.get(), participantId, eventIds).stream()
                .map(event -> new LifecycleSnapshot(event.getEventId(), event.getAppointmentId(), event.getEffectiveAt(),
                        event.getRecordedAt(), event.getEventType(), event.getFromStatus(), event.getToStatus()))
                .toList();
    }

    private Optional<UUID> authorize(String subject, UUID participantId, ActingContext context) {
        try {
            if (participantId == null || context == null || context.role() == null) return Optional.empty();
            var account = accounts.requireActive(subject);
            if (!account.hasProfile(ProfileType.SPECIALIST)) return Optional.empty();
            authorization.requireCapabilities(account.id(), participantId, context, Set.of(capability(context)), purpose(context));
            return Optional.of(account.id());
        } catch (ResponseStatusException error) {
            if (error.getStatusCode().value() == HttpStatus.FORBIDDEN.value()) return Optional.empty();
            throw error;
        }
    }

    private static Capability capability(ActingContext context) {
        return context.role() == ProfessionalRole.TRAINER ? Capability.PLAN_PERFORMANCE : Capability.PLAN_FUNCTIONAL_RECOVERY;
    }

    private static Purpose purpose(ActingContext context) {
        return context.role() == ProfessionalRole.TRAINER ? Purpose.PERFORMANCE_PLANNING : Purpose.FUNCTIONAL_RECOVERY;
    }

    private static void requireRange(Instant fromInclusive, Instant toExclusive) {
        if (fromInclusive == null || toExclusive == null || !toExclusive.isAfter(fromInclusive)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "history range is required");
        }
    }

    private static AppointmentSnapshot snapshot(Appointment appointment) {
        return new AppointmentSnapshot(appointment.id, appointment.specialistAccountId, appointment.participantId,
                appointment.startsAt, appointment.endsAt, appointment.type.name(), appointment.status.name(), appointment.plannedSessionId);
    }
}
