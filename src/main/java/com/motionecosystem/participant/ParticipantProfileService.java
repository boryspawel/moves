package com.motionecosystem.participant;

import java.time.Clock;
import java.util.Optional;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class ParticipantProfileService {

    private final ParticipantProfileRepository profiles;
    private final Clock clock;

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

    /** Names are composed only after the caller has enforced its relationship policy. */
    @Transactional(readOnly = true)
    public Map<UUID, String> findDisplayNames(Collection<UUID> accountIds) {
        if (accountIds == null || accountIds.isEmpty()) return Map.of();
        return profiles.findByAccountIdIn(accountIds).stream()
                .collect(java.util.stream.Collectors.toUnmodifiableMap(profile -> profile.accountId, profile -> profile.displayName));
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
