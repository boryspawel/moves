package com.motionecosystem.notification.participantaccess;

import java.net.URI;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("participant-invitation.delivery")
public record ParticipantInvitationMailProperties(boolean enabled, String sender, String frontendBaseUrl,
                                                   String smtpHost, int smtpPort, Duration connectTimeout,
                                                   Duration readTimeout, Duration writeTimeout) {
    private static final Duration MAX_TIMEOUT = Duration.ofSeconds(30);

    void validate(boolean localProfile) {
        requireText(sender, "sender");
        requireText(smtpHost, "smtp-host");
        if (smtpPort < 1 || smtpPort > 65_535) {
            throw new IllegalStateException("participant-invitation.delivery.smtp-port must be between 1 and 65535");
        }
        validateTimeout(connectTimeout, "connect-timeout");
        validateTimeout(readTimeout, "read-timeout");
        validateTimeout(writeTimeout, "write-timeout");
        URI frontend = URI.create(frontendBaseUrl);
        if (frontend.getScheme() == null || frontend.getHost() == null || frontend.getUserInfo() != null
                || (frontend.getPath() != null && !frontend.getPath().isBlank() && !"/".equals(frontend.getPath()))
                || frontend.getQuery() != null || frontend.getFragment() != null) {
            throw new IllegalStateException("participant-invitation.delivery.frontend-base-url must be an origin");
        }
        if (!localProfile && !"https".equalsIgnoreCase(frontend.getScheme())) {
            throw new IllegalStateException("participant-invitation.delivery.frontend-base-url must use HTTPS outside the local profile");
        }
    }

    private static void requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("participant-invitation.delivery." + name + " must be configured when delivery is enabled");
        }
    }

    private static void validateTimeout(Duration timeout, String name) {
        if (timeout == null || timeout.isNegative() || timeout.isZero() || timeout.compareTo(MAX_TIMEOUT) > 0) {
            throw new IllegalStateException("participant-invitation.delivery." + name + " must be greater than zero and no more than 30 seconds");
        }
    }
}
