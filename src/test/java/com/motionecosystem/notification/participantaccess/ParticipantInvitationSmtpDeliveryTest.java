package com.motionecosystem.notification.participantaccess;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import java.time.Duration;
import java.time.Instant;
import java.util.Properties;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.mail.javamail.JavaMailSender;

class ParticipantInvitationSmtpDeliveryTest {
    private final ApplicationContextRunner context = new ApplicationContextRunner()
            .withUserConfiguration(ParticipantInvitationSmtpConfiguration.class);

    @Test
    void sendsTheCredentialOnlyInTheSpaFragment() throws Exception {
        JavaMailSender sender = mock(JavaMailSender.class);
        MimeMessage message = new MimeMessage(Session.getInstance(new Properties()));
        when(sender.createMimeMessage()).thenReturn(message);
        var delivery = new ParticipantInvitationSmtpDelivery(sender, properties());

        delivery.deliver(UUID.randomUUID(), "participant@example.test", "raw-token_123", Instant.parse("2026-09-16T10:00:00Z"));

        ArgumentCaptor<MimeMessage> sent = ArgumentCaptor.forClass(MimeMessage.class);
        org.mockito.Mockito.verify(sender).send(sent.capture());
        String body = (String) sent.getValue().getContent();
        assertThat(body).contains("https://app.example.test/participant/claim#token=raw-token_123");
        assertThat(body).doesNotContain("?token=");
        assertThat(sent.getValue().getAllRecipients()[0].toString()).isEqualTo("participant@example.test");
    }

    @Test
    void refusesEnabledDeliveryWithoutACompleteSmtpConfiguration() {
        context.withPropertyValues("participant-invitation.delivery.enabled=true")
                .run(result -> assertThat(result.getStartupFailure()).hasMessageContaining("sender must be configured"));
    }

    private static ParticipantInvitationMailProperties properties() {
        return new ParticipantInvitationMailProperties(true, "no-reply@example.test", "https://app.example.test",
                "smtp.example.test", 587, Duration.ofSeconds(5), Duration.ofSeconds(5), Duration.ofSeconds(5));
    }
}
