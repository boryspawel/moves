package com.motionecosystem.specialist;

import com.motionecosystem.availability.RecurringAvailabilityService;
import com.motionecosystem.audit.AuditRecorder;
import com.motionecosystem.calendar.AppointmentService;
import com.motionecosystem.identityaccess.api.CurrentAccountService;
import com.motionecosystem.identityaccess.api.ProfileType;
import com.motionecosystem.participant.ParticipantProfileService;
import com.motionecosystem.specialist.api.SpecialistAuthorizationPort.ActingContext;
import com.motionecosystem.specialist.api.SpecialistAuthorizationPort.ProfessionalRole;
import com.motionecosystem.specialist.api.SpecialistAuthorizationPort.Purpose;
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
    private final SpecialistRelationshipService relationships;
    private final SpecialistProfileService profiles;
    private final ParticipantProfileService participantProfiles;
    private final RecurringAvailabilityService availability;
    private final AppointmentService appointments;
    private final SpecialistWorklistService worklist;
    private final AuditRecorder audit;
    private final Clock clock;

    @Transactional
    TodayView today(String subject, LocalDate requestedDate) {
        var account = accounts.requireActive(subject);
        if (!account.hasProfile(ProfileType.SPECIALIST)) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "specialist profile is required");
        List<RecurringAvailabilityService.Slot> slots = availability.list(account.id());
        SpecialistProfileService.ProfileView profile = profiles.find(account.id())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "specialist profile is required"));
        ZoneId zone = profileTimeZone(profile.timeZoneId());
        LocalDate localDate = requestedDate == null ? LocalDate.now(clock.withZone(zone)) : requestedDate;
        Instant start = SpecialistTodayTime.startOfDay(localDate, zone);
        Instant end = SpecialistTodayTime.endOfDay(localDate, zone);
        Instant now = clock.instant();
        Set<UUID> activeParticipants = relationships.activeParticipantIds(account.id());
        Map<UUID, String> labels = participantProfiles.findDisplayNames(activeParticipants);
        List<AppointmentService.AppointmentView> raw = appointments.inRange(account.id(), start, end, activeParticipants, now);
        Optional<AppointmentService.AppointmentView> current = raw.stream().filter(item -> item.isCurrent() && active(item)).findFirst();
        Optional<UUID> nextId = raw.stream().filter(item -> item.status() != com.motionecosystem.calendar.Appointment.Status.CANCELLED
                        && item.status() != com.motionecosystem.calendar.Appointment.Status.COMPLETED && item.startsAt().isAfter(now))
                .map(AppointmentService.AppointmentView::appointmentId).findFirst();
        List<AppointmentView> appointmentViews = raw.stream().map(item -> appointmentView(item, labels.get(item.participantId()), nextId.filter(item.appointmentId()::equals).isPresent())).toList();
        List<AvailabilityWindowView> windows = windows(slots, localDate);
        List<AttentionItemView> attention = attention(subject, account.id(), profile, labels);
        VisibleRange range = range(zone, localDate, windows, appointmentViews);
        if (!appointmentViews.isEmpty() || !attention.isEmpty()) audit.record(subject, "SPECIALIST_TODAY_VIEWED", "PrincipalAccount", account.id());
        return new TodayView(now, localDate, zone.getId(), range,
                current.map(item -> appointmentView(item, labels.get(item.participantId()), false)).orElse(null),
                nextId.flatMap(id -> appointmentViews.stream().filter(item -> item.appointmentId().equals(id)).findFirst()).orElse(null),
                appointmentViews, windows, attention, List.of(),
                new Counts(appointmentViews.size(), attention.size(), 0, current.isPresent() ? 1 : 0));
    }
    private static boolean active(AppointmentService.AppointmentView item) { return item.status() != com.motionecosystem.calendar.Appointment.Status.CANCELLED && item.status() != com.motionecosystem.calendar.Appointment.Status.COMPLETED && item.status() != com.motionecosystem.calendar.Appointment.Status.NO_SHOW; }
    private List<AttentionItemView> attention(String subject, UUID specialist, SpecialistProfileService.ProfileView profile, Map<UUID, String> labels) {
        if (profile == null) return List.of();
        ProfessionalRole role = ProfessionalRole.valueOf(profile.specialistKind().name());
        Purpose purpose = role == ProfessionalRole.TRAINER ? Purpose.PERFORMANCE_PLANNING : Purpose.FUNCTIONAL_RECOVERY;
        return worklist.list(subject, new ActingContext(role), purpose).stream()
                .sorted(Comparator.comparingInt((SpecialistWorklistService.WorklistItemView item) -> priority(item.priority())).reversed()
                        .thenComparing(SpecialistWorklistService.WorklistItemView::createdAt))
                .limit(ATTENTION_LIMIT)
                .map(item -> new AttentionItemView(item.id(), item.category(), item.priority(), labels.get(item.participantAccountId()),
                        item.category().replace('_', ' '), item.minimalData(), item.createdAt(), item.snoozedUntil(), item.status(),
                        actions(item), "/api/v1/specialist/worklist/" + item.id())).toList();
    }
    private static int priority(String priority) { return "HIGH".equals(priority) ? 3 : "MEDIUM".equals(priority) ? 2 : 1; }
    private static List<String> actions(SpecialistWorklistService.WorklistItemView item) { return "PARTICIPANT_ISSUE".equals(item.category()) ? List.of("OPEN_WORKLIST_ITEM", "REPLY") : List.of("OPEN_WORKLIST_ITEM", "ACKNOWLEDGE", "RESOLVE"); }
    private static ZoneId profileTimeZone(String timeZoneId) {
        if (timeZoneId == null || timeZoneId.isBlank()) throw new ResponseStatusException(HttpStatus.CONFLICT, "specialist time zone is not configured");
        try { return ZoneId.of(timeZoneId); }
        catch (RuntimeException invalid) { throw new ResponseStatusException(HttpStatus.CONFLICT, "specialist time zone is invalid"); }
    }
    private static List<AvailabilityWindowView> windows(List<RecurringAvailabilityService.Slot> slots, LocalDate date) { return slots.stream().filter(slot -> slot.dayOfWeek() == date.getDayOfWeek()).map(slot -> new AvailabilityWindowView(
            date.atTime(slot.startTime()).atZone(ZoneId.of(slot.timeZone())).toInstant(), date.atTime(slot.endTime()).atZone(ZoneId.of(slot.timeZone())).toInstant(), "STANDARD_AVAILABILITY")).toList(); }
    private static AppointmentView appointmentView(AppointmentService.AppointmentView item, String label, boolean next) { return new AppointmentView(item.appointmentId(), item.participantId(), label == null ? "Uczestnik" : label, item.startsAt(), item.endsAt(), item.type().name(), item.status().name(), item.locationMode().name(), item.location(), item.shortPurpose(), item.isCurrent(), next, item.availableActions(), item.version()); }
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
