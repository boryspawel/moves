package com.motionecosystem.specialist;

import java.time.Clock;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class SpecialistProfileService {

    private final SpecialistProfileRepository profiles;
    private final ProfessionalScopeRepository scopes;
    private final Clock clock;

    @Transactional
    public ProfileView save(UUID accountId, String displayName, SpecialistKind kind, String timeZoneId) {
        String name = validName(displayName);
        if (kind == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "specialist kind is required");
        }
        ZoneId timeZone = validTimeZoneOrLegacyDefault(timeZoneId);
        SpecialistProfile profile = profiles.findByAccountId(accountId)
                .orElseGet(() -> new SpecialistProfile(accountId, name, kind, timeZone, clock.instant()));
        profile.update(name, kind, timeZone, clock.instant());
        if (!scopes.existsById(new ProfessionalScope.Id(accountId, kind))) {
            scopes.save(new ProfessionalScope(accountId, kind, clock.instant()));
        }
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
    private static ZoneId validTimeZoneOrLegacyDefault(String value) {
        String id = value == null ? "" : value.trim();
        if (id.isEmpty()) return ZoneOffset.UTC;
        try { return ZoneId.of(id); }
        catch (RuntimeException invalid) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "timeZoneId must be a valid IANA identifier"); }
    }

    private static ProfileView view(SpecialistProfile profile) {
        return new ProfileView(profile.id, profile.displayName, profile.specialistKind, profile.timeZoneId);
    }

    public record ProfileView(UUID id, String displayName, SpecialistKind specialistKind, String timeZoneId) {
    }
}
