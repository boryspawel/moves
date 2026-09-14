package com.motionecosystem.application.workspace;

import com.motionecosystem.availability.RecurringAvailabilityService;
import com.motionecosystem.audit.AuditRecorder;
import com.motionecosystem.calendar.api.SpecialistAppointmentQueryPort;
import com.motionecosystem.calendar.api.SpecialistOverdueAppointmentQueryPort;
import com.motionecosystem.identityaccess.api.CurrentAccountService;
import com.motionecosystem.identityaccess.api.ProfileType;
import com.motionecosystem.participant.api.ParticipantClientPort;
import com.motionecosystem.specialist.api.SpecialistWorkspacePort;
import com.motionecosystem.specialist.api.SpecialistWorkspacePort.WorkspacePurpose;
import com.motionecosystem.specialist.api.SpecialistWorkspacePort.WorkspaceRole;
import java.time.*;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Bounded operational composition; appointment data remains owned by the calendar module. */
@Service
@RequiredArgsConstructor
class SpecialistTodayService {
    private static final int ATTENTION_LIMIT = 10;
    private final CurrentAccountService accounts;
    private final SpecialistWorkspacePort specialistWorkspace;
    private final ParticipantClientPort participants;
    private final RecurringAvailabilityService availability;
    private final SpecialistAppointmentQueryPort appointments;
    private final SpecialistOverdueAppointmentQueryPort overdueAppointments;
    private final AuditRecorder audit;
    private final Clock clock;

    @Transactional
    TodayView today(String subject, LocalDate requestedDate) {
        var account = accounts.requireActive(subject);
        if (!account.hasProfile(ProfileType.SPECIALIST)) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "specialist profile is required");
        List<RecurringAvailabilityService.Slot> slots = availability.list(account.id());
        SpecialistWorkspacePort.Profile profile = specialistWorkspace.findProfile(account.id())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "specialist profile is required"));
        ZoneId zone = profileTimeZone(profile.timeZoneId());
        LocalDate localDate = requestedDate == null ? LocalDate.now(clock.withZone(zone)) : requestedDate;
        Instant start = SpecialistTodayTime.startOfDay(localDate, zone);
        Instant end = SpecialistTodayTime.endOfDay(localDate, zone);
        Instant now = clock.instant();
        Set<UUID> activeParticipants = specialistWorkspace.activeParticipantIds(account.id());
        Map<UUID, String> labels = participantLabels(activeParticipants);
        List<SpecialistAppointmentQueryPort.ScheduledAppointment> raw = appointments.inRange(account.id(), start, end, activeParticipants, now);
        Optional<SpecialistAppointmentQueryPort.ScheduledAppointment> current = raw.stream().filter(item -> item.current() && active(item)).findFirst();
        Optional<UUID> nextId = raw.stream().filter(item -> !"CANCELLED".equals(item.status())
                        && !"COMPLETED".equals(item.status()) && item.startsAt().isAfter(now))
                .min(Comparator.comparing(SpecialistAppointmentQueryPort.ScheduledAppointment::startsAt))
                .map(SpecialistAppointmentQueryPort.ScheduledAppointment::appointmentId);
        List<AppointmentView> appointmentViews = raw.stream().map(item -> appointmentView(item, labels.get(item.participantId()), nextId.filter(item.appointmentId()::equals).isPresent())).toList();
        List<AvailabilityWindowView> windows = windows(slots, localDate);
        List<AttentionItemView> attention = attention(subject, account.id(), profile, labels);
        List<OperationalTaskView> operationalTasks = operationalTasks(account.id(), activeParticipants, now, labels);
        VisibleRange range = range(zone, localDate, windows, appointmentViews);
        if (!appointmentViews.isEmpty() || !attention.isEmpty()) audit.record(subject, "SPECIALIST_TODAY_VIEWED", "PrincipalAccount", account.id());
        return new TodayView(now, localDate, zone.getId(), range,
                current.map(item -> appointmentView(item, labels.get(item.participantId()), false)).orElse(null),
                nextId.flatMap(id -> appointmentViews.stream().filter(item -> item.appointmentId().equals(id)).findFirst()).orElse(null),
                appointmentViews, windows, attention, operationalTasks,
                new Counts(appointmentViews.size(), attention.size(), operationalTasks.size(), current.isPresent() ? 1 : 0));
    }
    private static boolean active(SpecialistAppointmentQueryPort.ScheduledAppointment item) { return !Set.of("CANCELLED", "COMPLETED", "NO_SHOW").contains(item.status()); }
    private Map<UUID, String> participantLabels(Set<UUID> participantIds) {
        return participantIds.stream().map(participantId -> participants.find(participantId)
                        .map(record -> Map.entry(participantId, record.displayName())))
                .flatMap(Optional::stream)
                .collect(java.util.stream.Collectors.toUnmodifiableMap(Map.Entry::getKey, Map.Entry::getValue));
    }
    private List<AttentionItemView> attention(String subject, UUID specialist, SpecialistWorkspacePort.Profile profile, Map<UUID, String> labels) {
        if (profile == null) return List.of();
        WorkspaceRole role = profile.role();
        WorkspacePurpose purpose = role == WorkspaceRole.TRAINER ? WorkspacePurpose.PERFORMANCE_PLANNING : WorkspacePurpose.FUNCTIONAL_RECOVERY;
        return specialistWorkspace.listWorklist(subject, role, purpose).stream()
                .sorted(Comparator.comparingInt((SpecialistWorkspacePort.WorklistItem item) -> priority(item.priority())).reversed()
                        .thenComparing(SpecialistWorkspacePort.WorklistItem::createdAt))
                .limit(ATTENTION_LIMIT)
                .map(item -> new AttentionItemView(item.id(), item.category(), item.priority(), labels.get(item.participantId()),
                        item.category().replace('_', ' '), item.minimalData(), item.createdAt(), item.snoozedUntil(), item.status(),
                        actions(item), "/api/v1/specialist/worklist/" + item.id())).toList();
    }
    private List<OperationalTaskView> operationalTasks(UUID specialist, Set<UUID> activeParticipants, Instant now, Map<UUID, String> labels) {
        return overdueAppointments.overdueOutcomeAppointments(specialist, activeParticipants, now).stream()
                .map(item -> new OperationalTaskView("APPOINTMENT_OUTCOME_REQUIRED",
                        "Uzupełnij wynik spotkania z " + labels.getOrDefault(item.participantId(), "Uczestnik"),
                        "/specialist/clients/" + item.participantId() + (item.latestEventId() == null ? "" : "?eventId=appointment-event:" + item.latestEventId())))
                .toList();
    }
    private static int priority(String priority) { return "HIGH".equals(priority) ? 3 : "MEDIUM".equals(priority) ? 2 : 1; }
    private static List<String> actions(SpecialistWorkspacePort.WorklistItem item) { return "PARTICIPANT_ISSUE".equals(item.category()) ? List.of("OPEN_WORKLIST_ITEM", "REPLY") : List.of("OPEN_WORKLIST_ITEM", "ACKNOWLEDGE", "RESOLVE"); }
    private static ZoneId profileTimeZone(String timeZoneId) {
        if (timeZoneId == null || timeZoneId.isBlank()) throw new ResponseStatusException(HttpStatus.CONFLICT, "specialist time zone is not configured");
        try { return ZoneId.of(timeZoneId); }
        catch (RuntimeException invalid) { throw new ResponseStatusException(HttpStatus.CONFLICT, "specialist time zone is invalid"); }
    }
    private static List<AvailabilityWindowView> windows(List<RecurringAvailabilityService.Slot> slots, LocalDate date) { return slots.stream().filter(slot -> slot.dayOfWeek() == date.getDayOfWeek()).map(slot -> new AvailabilityWindowView(
            date.atTime(slot.startTime()).atZone(ZoneId.of(slot.timeZone())).toInstant(), date.atTime(slot.endTime()).atZone(ZoneId.of(slot.timeZone())).toInstant(), "STANDARD_AVAILABILITY")).toList(); }
    private static AppointmentView appointmentView(SpecialistAppointmentQueryPort.ScheduledAppointment item, String label, boolean next) { return new AppointmentView(item.appointmentId(), item.participantId(), label == null ? "Uczestnik" : label, item.startsAt(), item.endsAt(), item.type(), item.status(), item.locationMode(), item.location(), item.shortPurpose(), item.current(), next, item.availableActions(), item.version()); }
    private static VisibleRange range(ZoneId zone, LocalDate date, List<AvailabilityWindowView> windows, List<AppointmentView> appointments) {
        List<Instant> points = new ArrayList<>(); windows.forEach(item -> { points.add(item.startsAt()); points.add(item.endsAt()); }); appointments.forEach(item -> { points.add(item.startsAt()); points.add(item.endsAt()); });
        if (points.isEmpty()) return new VisibleRange(date.atTime(8, 0).atZone(zone).toInstant(), date.atTime(18, 0).atZone(zone).toInstant(), 30);
        Instant earliest = points.stream().min(Comparator.naturalOrder()).orElseThrow(); Instant latest = points.stream().max(Comparator.naturalOrder()).orElseThrow();
        if (Duration.between(earliest, latest).compareTo(Duration.ofHours(16)) > 0) { Instant midpoint = earliest.plus(Duration.between(earliest, latest).dividedBy(2)); return new VisibleRange(midpoint.minus(Duration.ofHours(8)), midpoint.plus(Duration.ofHours(8)), 30); }
        return new VisibleRange(earliest.minus(Duration.ofHours(1)), latest.plus(Duration.ofHours(1)), 30);
    }
    record TodayView(Instant generatedAt, LocalDate localDate, String timeZoneId, VisibleRange visibleRange, AppointmentView currentAppointment, AppointmentView nextAppointment, List<AppointmentView> appointments, List<AvailabilityWindowView> availabilityWindows, List<AttentionItemView> attentionItems, List<OperationalTaskView> operationalTasks, Counts counts) { }
    record VisibleRange(Instant startsAt, Instant endsAt, int recommendedStepMinutes) { }
    record AppointmentView(UUID appointmentId, UUID participantId, String participantLabel, Instant startsAt, Instant endsAt, String type, String status, String locationMode, String location, String shortPurpose, boolean isCurrent, boolean isNext, List<String> availableActions, long version) { }
    record AvailabilityWindowView(Instant startsAt, Instant endsAt, String type) { }
    record AttentionItemView(UUID id, String type, String priority, String participantLabel, String title, String neutralReason, Instant createdAt, Instant dueAt, String status, List<String> availableActions, String navigationReference) { }
    record OperationalTaskView(String type, String title, String navigationReference) { }
    record Counts(int appointments, int attentionItems, int operationalTasks, int currentAppointments) { }
}
