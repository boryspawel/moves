package com.motionecosystem.identityaccess.api;

import java.util.UUID;

public record CurrentAccount(UUID id, String externalSubject, ProfileType profileType) {
}
