package com.motionecosystem.notification.participantaccess;

import com.motionecosystem.participant.api.ParticipantInvitationDeliveryPort;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.web.util.UriComponentsBuilder;

final class ParticipantInvitationSmtpDelivery implements ParticipantInvitationDeliveryPort {
    private final JavaMailSender mailSender;
    private final ParticipantInvitationMailProperties properties;

    ParticipantInvitationSmtpDelivery(JavaMailSender mailSender, ParticipantInvitationMailProperties properties) {
        this.mailSender = mailSender;
        this.properties = properties;
    }

    @Override
    public void deliver(UUID invitationId, String intendedEmail, String rawToken, Instant expiresAt) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, StandardCharsets.UTF_8.name());
            helper.setFrom(properties.sender());
            helper.setTo(intendedEmail);
            helper.setSubject("Complete your participant account setup");
            helper.setText("Use this one-time link to continue setting up your participant account:\n"
                    + claimLink(rawToken) + "\n\nThis link expires at " + expiresAt + ".", false);
            mailSender.send(message);
        } catch (MessagingException | RuntimeException exception) {
            throw new MailSendException("participant invitation email could not be delivered", exception);
        }
    }

    private String claimLink(String rawToken) {
        return UriComponentsBuilder.fromUriString(properties.frontendBaseUrl())
                .path("/participant/claim")
                .fragment("token=" + rawToken)
                .build()
                .toUriString();
    }
}
