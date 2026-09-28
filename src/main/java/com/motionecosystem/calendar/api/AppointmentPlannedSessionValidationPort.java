package com.motionecosystem.calendar.api;

import java.util.UUID;

/** Calendar-owned validation boundary for an optional planned-session appointment link. */
public interface AppointmentPlannedSessionValidationPort {
    void requireLinkable(String subject, UUID participantId, UUID plannedSessionId);
}
