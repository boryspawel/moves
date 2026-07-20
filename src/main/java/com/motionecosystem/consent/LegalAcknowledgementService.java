package com.motionecosystem.consent;

import java.time.Clock;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class LegalAcknowledgementService {

    private final LegalAcknowledgementRepository acknowledgements;
    private final LegalDocumentVersions versions;
    private final Clock clock;

    @Transactional
    public List<View> acknowledgeRequired(UUID accountId, boolean termsAccepted, boolean privacyAcknowledged) {
        if (!termsAccepted || !privacyAcknowledged) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "all required documents must be acknowledged");
        }
        Instant now = clock.instant();
        Arrays.stream(AcknowledgementType.values()).forEach(type -> {
            String version = versions.version(type);
            if (!acknowledgements.existsByAccountIdAndTypeAndDocumentVersion(accountId, type, version)) {
                acknowledgements.save(new LegalAcknowledgement(accountId, type, version, now));
            }
        });
        return current(accountId);
    }

    @Transactional(readOnly = true)
    public boolean hasAllCurrent(UUID accountId) {
        return Arrays.stream(AcknowledgementType.values()).allMatch(type ->
                acknowledgements.existsByAccountIdAndTypeAndDocumentVersion(
                        accountId, type, versions.version(type)));
    }

    @Transactional(readOnly = true)
    public List<View> current(UUID accountId) {
        return acknowledgements.findByAccountIdOrderByAcceptedAt(accountId).stream()
                .filter(item -> item.documentVersion.equals(versions.version(item.type)))
                .map(item -> new View(item.type, item.documentVersion, item.acceptedAt))
                .toList();
    }

    public record View(AcknowledgementType type, String documentVersion, Instant acceptedAt) {
    }
}
