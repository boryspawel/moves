package com.motionecosystem.specialist;

import java.time.Clock;
import java.util.Optional;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class SpecialistProfileService {

    private final SpecialistProfileRepository profiles;
    private final Clock clock;

    SpecialistProfileService(SpecialistProfileRepository profiles, Clock clock) {
        this.profiles = profiles;
        this.clock = clock;
    }

    @Transactional
    public ProfileView save(UUID accountId, String displayName, SpecialistKind kind) {
        String name = validName(displayName);
        if (kind == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "specialist kind is required");
        }
        SpecialistProfile profile = profiles.findByAccountId(accountId)
                .orElseGet(() -> new SpecialistProfile(accountId, name, kind, clock.instant()));
        profile.update(name, kind, clock.instant());
        return view(profiles.save(profile));
    }

    @Transactional(readOnly = true)
    public Optional<ProfileView> find(UUID accountId) {
        return profiles.findByAccountId(accountId).map(SpecialistProfileService::view);
    }

    private static String validName(String value) {
        String name = value == null ? "" : value.trim();
        if (name.isEmpty() || name.length() > 80) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "display name must contain 1-80 characters");
        }
        return name;
    }

    private static ProfileView view(SpecialistProfile profile) {
        return new ProfileView(profile.id, profile.displayName, profile.specialistKind);
    }

    public record ProfileView(UUID id, String displayName, SpecialistKind specialistKind) {
    }
}
