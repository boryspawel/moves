package com.motionecosystem.participant;

import java.time.Clock;
import java.util.Optional;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ParticipantProfileService {

    private final ParticipantProfileRepository profiles;
    private final Clock clock;

    ParticipantProfileService(ParticipantProfileRepository profiles, Clock clock) {
        this.profiles = profiles;
        this.clock = clock;
    }

    @Transactional
    public ProfileView save(UUID accountId, String displayName) {
        String name = validName(displayName);
        ParticipantProfile profile = profiles.findByAccountId(accountId)
                .orElseGet(() -> new ParticipantProfile(accountId, name, clock.instant()));
        profile.update(name, clock.instant());
        return view(profiles.save(profile));
    }

    @Transactional(readOnly = true)
    public Optional<ProfileView> find(UUID accountId) {
        return profiles.findByAccountId(accountId).map(ParticipantProfileService::view);
    }

    private static String validName(String value) {
        String name = value == null ? "" : value.trim();
        if (name.isEmpty() || name.length() > 80) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "display name must contain 1-80 characters");
        }
        return name;
    }

    private static ProfileView view(ParticipantProfile profile) {
        return new ProfileView(profile.id, profile.displayName);
    }

    public record ProfileView(UUID id, String displayName) {
    }
}
