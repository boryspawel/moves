package com.motionecosystem.calendar;

import com.motionecosystem.calendar.api.SpecialistAppointmentEventQueryPort;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
class AppointmentEventQueryAdapter implements SpecialistAppointmentEventQueryPort {
    private final AppointmentEventRepository events;

    @Override @Transactional(readOnly = true)
    public List<AppointmentEventSummary> timeline(UUID specialist, UUID participant, Instant from, Instant to, SeekCursor after, int limit) {
        if (specialist == null || participant == null || from == null || to == null || !to.isAfter(from) || limit < 1)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "specialist, participant, range and limit are required");
        var page = PageRequest.of(0, limit);
        List<AppointmentEventRepository.AppointmentEventProjection> result = after == null ? events.findInitial(specialist, participant, from, to, page)
                : events.findAfter(specialist, participant, from, to, after.effectiveAt(), after.recordedAt(), after.eventId(), page);
        return result.stream().map(AppointmentEventQueryAdapter::summary).toList();
    }

    @Override @Transactional(readOnly = true)
    public Optional<AppointmentEventSummary> findBySpecialistAndParticipant(UUID specialist, UUID participant, UUID eventId) {
        if (specialist == null || participant == null || eventId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "specialist, participant and event are required");
        }
        return events.findBySpecialistAndParticipant(specialist, participant, eventId).map(AppointmentEventQueryAdapter::summary);
    }

    private static AppointmentEventSummary summary(AppointmentEventRepository.AppointmentEventProjection event) {
        return new AppointmentEventSummary(event.getEventId(), event.getAppointmentId(), event.getEventType(), event.getFromStatus(),
                event.getToStatus(), event.getEffectiveAt(), event.getRecordedAt(), event.getAppointmentType(), event.getShortPurpose());
    }
}
