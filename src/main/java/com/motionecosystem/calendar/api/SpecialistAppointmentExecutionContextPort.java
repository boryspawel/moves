package com.motionecosystem.calendar.api;

import java.util.UUID;

public interface SpecialistAppointmentExecutionContextPort {
    AppointmentExecutionContext readExecutionContext(String subject, UUID appointmentId);
    AppointmentExecutionContext lockWritableExecutionContext(String subject, UUID appointmentId);
    CloseoutAppointmentContext readCloseoutContext(String subject, UUID appointmentId);
    record AppointmentExecutionContext(UUID appointmentId, UUID specialistAccountId, UUID participantId,
                                       UUID plannedSessionId, String status) { }
    record CloseoutAppointmentContext(UUID appointmentId, UUID specialistAccountId, UUID participantId, UUID plannedSessionId,
                                     String status, long version, java.time.Instant startsAt, java.time.Instant endsAt, String shortPurpose) { }
}
