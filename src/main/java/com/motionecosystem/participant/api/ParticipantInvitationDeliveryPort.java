package com.motionecosystem.participant.api;
import java.time.Instant; import java.util.UUID;
/** Outbound boundary; implementations must never persist or log the credential. */
public interface ParticipantInvitationDeliveryPort { void deliver(UUID invitationId, String intendedEmail, String rawToken, Instant expiresAt); }
