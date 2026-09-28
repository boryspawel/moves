package com.motionecosystem.calendar;

import com.motionecosystem.calendar.api.AppointmentSessionBindingQueryPort;
import java.util.Collection;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@RequiredArgsConstructor
class AppointmentSessionBindingAdapter implements AppointmentSessionBindingQueryPort {
    private final AppointmentRepository appointments;

    @Override
    @Transactional(readOnly = true)
    public boolean isBound(UUID participantId, UUID plannedSessionId) {
        return participantId != null && plannedSessionId != null
                && appointments.existsByParticipantIdAndPlannedSessionId(participantId, plannedSessionId);
    }

    @Override
    @Transactional(readOnly = true)
    public Set<UUID> boundSessionIds(UUID participantId, Collection<UUID> plannedSessionIds) {
        if (participantId == null || plannedSessionIds == null || plannedSessionIds.isEmpty()) return Set.of();
        return Set.copyOf(appointments.findBoundPlannedSessionIds(participantId, Set.copyOf(plannedSessionIds)));
    }
}
