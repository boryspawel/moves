package com.motionecosystem.calendar.api;

import java.util.Collection;
import java.util.Set;
import java.util.UUID;

/** Calendar-owned binding facts used to keep participant execution separate from appointments. */
public interface AppointmentSessionBindingQueryPort {
    boolean isBound(UUID participantId, UUID plannedSessionId);

    Set<UUID> boundSessionIds(UUID participantId, Collection<UUID> plannedSessionIds);
}
