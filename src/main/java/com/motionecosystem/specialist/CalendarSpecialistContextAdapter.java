package com.motionecosystem.specialist;

import com.motionecosystem.calendar.api.CalendarSpecialistContextPort;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class CalendarSpecialistContextAdapter implements CalendarSpecialistContextPort {
    private final SpecialistRelationshipService relationships;
    private final SpecialistProfileService profiles;

    @Override
    public void requireActiveRelationship(UUID specialistAccountId, UUID participantId) {
        relationships.requireActive(specialistAccountId, participantId);
    }

    @Override
    public Optional<SpecialistContext> findSpecialist(UUID specialistAccountId) {
        return profiles.find(specialistAccountId).map(profile -> new SpecialistContext(profile.timeZoneId()));
    }
}
