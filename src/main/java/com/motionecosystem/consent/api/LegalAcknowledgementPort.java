package com.motionecosystem.consent.api;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface LegalAcknowledgementPort {

    List<View> acknowledgeRequired(UUID accountId, boolean termsAccepted, boolean privacyAcknowledged);

    boolean hasAllCurrent(UUID accountId);

    List<View> current(UUID accountId);

    record View(String type, String documentVersion, Instant acceptedAt) {
    }
}
