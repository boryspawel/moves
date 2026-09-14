package com.motionecosystem.notification.participantaccess;

import com.motionecosystem.participant.api.ParticipantInvitationDeliveryPort;
import java.util.Arrays;
import java.util.Properties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(ParticipantInvitationMailProperties.class)
@ConditionalOnProperty(prefix = "participant-invitation.delivery", name = "enabled", havingValue = "true")
class ParticipantInvitationSmtpConfiguration {

    @Bean
    JavaMailSender participantInvitationJavaMailSender(ParticipantInvitationMailProperties properties, Environment environment) {
        properties.validate(Arrays.asList(environment.getActiveProfiles()).contains("local"));
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(properties.smtpHost());
        sender.setPort(properties.smtpPort());
        Properties mailProperties = sender.getJavaMailProperties();
        mailProperties.setProperty("mail.smtp.connectiontimeout", Long.toString(properties.connectTimeout().toMillis()));
        mailProperties.setProperty("mail.smtp.timeout", Long.toString(properties.readTimeout().toMillis()));
        mailProperties.setProperty("mail.smtp.writetimeout", Long.toString(properties.writeTimeout().toMillis()));
        return sender;
    }

    @Bean
    ParticipantInvitationDeliveryPort participantInvitationDeliveryPort(JavaMailSender participantInvitationJavaMailSender,
                                                                        ParticipantInvitationMailProperties properties) {
        return new ParticipantInvitationSmtpDelivery(participantInvitationJavaMailSender, properties);
    }
}
