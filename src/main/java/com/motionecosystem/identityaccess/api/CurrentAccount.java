package com.motionecosystem.identityaccess.api;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public record CurrentAccount(
        UUID id,
        String externalSubject,
        ProfileType profileType,
        Set<ProfileType> profiles) {

    public CurrentAccount {
        HashSet<ProfileType> active = new HashSet<>(profiles == null ? Set.of() : profiles);
        if (profileType != null) {
            active.add(profileType);
        }
        profiles = Set.copyOf(active);
    }

    public CurrentAccount(UUID id, String externalSubject, ProfileType profileType) {
        this(id, externalSubject, profileType, profileType == null ? Set.of() : Set.of(profileType));
    }

    public boolean hasProfile(ProfileType type) {
        return profiles.contains(type);
    }
}
