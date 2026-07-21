package com.motionecosystem.identityaccess.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import com.motionecosystem.application.MotionEcosystemApplication;
import com.motionecosystem.support.PostgresTestConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest(classes = MotionEcosystemApplication.class)
@Import(PostgresTestConfiguration.class)
class ActiveParticipantPortIntegrationTest {

    @Autowired
    ActiveParticipantPort participants;

    @Autowired
    JdbcTemplate jdbc;

    @AfterEach
    void clean() {
        jdbc.execute("TRUNCATE TABLE identity_access.principal_account CASCADE");
    }

    @Test
    void exposesOnlyActiveParticipantAccountsThroughPublicSnapshot() {
        UUID participantId = account("active-participant", "ACTIVE", "PARTICIPANT");
        UUID specialistId = account("active-specialist", "ACTIVE", "SPECIALIST");
        UUID suspendedParticipantId = account("suspended-participant", "SUSPENDED", "PARTICIPANT");

        assertThat(participants.findActiveParticipant(participantId))
                .contains(new ActiveParticipantPort.ActiveParticipantSnapshot(participantId));
        assertThat(participants.findActiveParticipant(specialistId)).isEmpty();
        assertThat(participants.findActiveParticipant(suspendedParticipantId)).isEmpty();
        assertThat(participants.findActiveParticipant(UUID.randomUUID())).isEmpty();
    }

    private UUID account(String subject, String status, String profileType) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO identity_access.principal_account
                    (id, external_subject, status, profile_type, created_at, version)
                VALUES (?, ?, ?, ?, now(), 0)
                """, id, subject, status, profileType);
        return id;
    }
}
