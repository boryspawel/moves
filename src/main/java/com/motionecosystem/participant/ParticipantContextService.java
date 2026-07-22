package com.motionecosystem.participant;

import com.motionecosystem.participant.api.ParticipantContextQueryPort;
import java.time.Clock;
import java.time.ZoneId;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ParticipantContextService implements ParticipantContextQueryPort {

    private final ParticipantProfileRepository profiles;
    private final Clock clock;

    @Transactional
    public void setTimeZone(UUID participantAccountId, String timeZoneId) {
        ZoneId timeZone = requiredTimeZone(timeZoneId);
        ParticipantProfile profile = profiles.findByAccountId(participantAccountId)
                .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(
                        org.springframework.http.HttpStatus.NOT_FOUND, "participant profile not found"));
        profile.update(profile.displayName, timeZone, clock.instant());
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ParticipantContext> findContext(UUID participantAccountId) {
        return profiles.findByAccountId(participantAccountId)
                .filter(profile -> profile.timeZoneId != null)
                .map(profile -> new ParticipantContext(profile.accountId, ZoneId.of(profile.timeZoneId)));
    }
    private static ZoneId requiredTimeZone(String value) {
        String id = value == null ? "" : value.trim();
        if (id.isEmpty()) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.BAD_REQUEST, "time zone is required");
        }
        try {
            return ZoneId.of(id);
        } catch (RuntimeException invalid) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.BAD_REQUEST, "time zone must be a valid IANA identifier");
        }
    }
}
