package com.motionecosystem.consent;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
class LegalDocumentVersions {

    private final String terms;
    private final String privacy;

    LegalDocumentVersions(
            @Value("${legal.documents.terms-version}") String terms,
            @Value("${legal.documents.privacy-version}") String privacy) {
        this.terms = terms;
        this.privacy = privacy;
    }

    String version(AcknowledgementType type) {
        return switch (type) {
            case TERMS_OF_SERVICE -> terms;
            case PRIVACY_NOTICE -> privacy;
        };
    }
}
