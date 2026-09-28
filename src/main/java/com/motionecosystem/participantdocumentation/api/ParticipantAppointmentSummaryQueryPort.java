package com.motionecosystem.participantdocumentation.api;

import com.motionecosystem.identityaccess.api.SpecialistAuthorizationPort.ActingContext;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** Authorized owner-side lookup of the ordinary appointment-summary note. */
public interface ParticipantAppointmentSummaryQueryPort {
    Optional<AppointmentSummary> findAppointmentSummary(String subject, UUID participantId, UUID appointmentId, ActingContext context);
    record AppointmentSummary(UUID noteId, String title, String content, Instant recordedAt) { }
}
