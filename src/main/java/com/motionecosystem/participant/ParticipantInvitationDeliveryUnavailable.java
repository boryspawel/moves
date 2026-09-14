package com.motionecosystem.participant;
import com.motionecosystem.participant.api.ParticipantInvitationDeliveryPort; import java.time.Instant; import java.util.UUID; import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty; import org.springframework.http.HttpStatus; import org.springframework.stereotype.Service; import org.springframework.web.server.ResponseStatusException;
/** Fail closed until the SMTP adapter is configured; never logs the credential. */
@Service
@ConditionalOnProperty(prefix = "participant-invitation.delivery", name = "enabled", havingValue = "false", matchIfMissing = true)
class ParticipantInvitationDeliveryUnavailable implements ParticipantInvitationDeliveryPort { public void deliver(UUID id,String email,String token,Instant expiry){throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"participant invitation delivery is unavailable");} }
