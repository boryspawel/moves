package com.motionecosystem.calendar.api;

import java.util.Optional;
import java.util.UUID;

/** Specialist facts needed to validate calendar commands without depending on specialist internals. */
public interface CalendarSpecialistContextPort {
    void requireActiveRelationship(UUID specialistAccountId, UUID participantId);

    Optional<SpecialistContext> findSpecialist(UUID specialistAccountId);

    record SpecialistContext(String timeZoneId) { }
}
