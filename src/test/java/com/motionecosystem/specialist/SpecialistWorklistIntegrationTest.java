package com.motionecosystem.specialist;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.motionecosystem.application.MotionEcosystemApplication;
import com.motionecosystem.consent.ConsentGrantService;
import com.motionecosystem.consent.api.ConsentDecisionPort;
import com.motionecosystem.identityaccess.api.CurrentAccountService;
import com.motionecosystem.specialist.api.SpecialistAuthorizationPort.ActingContext;
import com.motionecosystem.specialist.api.SpecialistAuthorizationPort.ProfessionalRole;
import com.motionecosystem.specialist.api.SpecialistAuthorizationPort.Purpose;
import com.motionecosystem.support.PostgresTestConfiguration;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;

@SpringBootTest(classes = MotionEcosystemApplication.class)
@Import(PostgresTestConfiguration.class)
class SpecialistWorklistIntegrationTest {

    @Autowired private SpecialistWorklistService worklist;
    @Autowired private CurrentAccountService accounts;
    @Autowired private ConsentGrantService consents;
    @Autowired private JdbcTemplate jdbc;

    private UUID participant;
    private UUID specialist;
    private UUID template;

    @BeforeEach
    void setUp() {
        participant = account("worklist-participant", "PARTICIPANT");
        specialist = account("worklist-specialist", "SPECIALIST");
        scope(specialist);
        relationship();
        template = consents.publishTemplate("SPECIAL_DATA", 1, "urn:consent:worklist:v1", "EXPLICIT_CONSENT").id();
        consents.grant("worklist-participant", new ConsentGrantService.GrantCommand(
                specialist, ConsentDecisionPort.Purpose.PERFORMANCE_PLANNING, template,
                Set.of(ConsentDecisionPort.DataScope.EXECUTION), null, null));
    }

    @AfterEach
    void clean() {
        jdbc.execute("""
                TRUNCATE audit.audit_event,
                    consent.consent_template_version,
                    specialist.participant_specialist_relationship,
                    specialist.professional_scope,
                    identity_access.principal_account CASCADE
                """);
    }

    @Test
    void retriesReuseActiveIssueAndAResolvedIssueCreatesANewHistoryEntry() {
        var first = worklist.reportIssue("worklist-participant",
                new SpecialistWorklistService.ParticipantIssueCommand("PAIN", "knee hurts"));
        var retry = worklist.reportIssue("worklist-participant",
                new SpecialistWorklistService.ParticipantIssueCommand("PAIN", "knee hurts again"));

        assertThat(retry.id()).isEqualTo(first.id());
        assertThat(retry.issueText()).isEqualTo("knee hurts");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM specialist.worklist_item", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM specialist.participant_issue", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit.audit_event WHERE event_type = 'PARTICIPANT_ISSUE_REPORTED'", Integer.class)).isEqualTo(1);

        var resolved = worklist.action("worklist-specialist", first.id(), trainer(), Purpose.PERFORMANCE_PLANNING,
                new SpecialistWorklistService.ActionCommand("RESOLVE", null, "handled"));
        var reopenedPattern = worklist.reportIssue("worklist-participant",
                new SpecialistWorklistService.ParticipantIssueCommand("PAIN", "pain returned"));

        assertThat(resolved.status()).isEqualTo("RESOLVED");
        assertThat(reopenedPattern.id()).isNotEqualTo(first.id());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM specialist.worklist_item", Integer.class)).isEqualTo(2);
    }

    @Test
    void authorizedSpecialistCanTransitionIssueAndLosesAccessWhenRelationshipEnds() {
        var issue = worklist.reportIssue("worklist-participant",
                new SpecialistWorklistService.ParticipantIssueCommand("TECHNIQUE", "not sure how to move"));

        var acknowledged = worklist.action("worklist-specialist", issue.id(), trainer(), Purpose.PERFORMANCE_PLANNING,
                new SpecialistWorklistService.ActionCommand("ACKNOWLEDGE", null, null));
        var snoozed = worklist.action("worklist-specialist", issue.id(), trainer(), Purpose.PERFORMANCE_PLANNING,
                new SpecialistWorklistService.ActionCommand("SNOOZE", Instant.now().plusSeconds(3600), null));
        var resolved = worklist.action("worklist-specialist", issue.id(), trainer(), Purpose.PERFORMANCE_PLANNING,
                new SpecialistWorklistService.ActionCommand("RESOLVE", null, "useful"));

        assertThat(acknowledged.status()).isEqualTo("ACKNOWLEDGED");
        assertThat(snoozed.status()).isEqualTo("SNOOZED");
        assertThat(resolved.status()).isEqualTo("RESOLVED");

        jdbc.update("UPDATE specialist.participant_specialist_relationship SET status = 'ENDED', ended_at = now()");
        assertThatThrownBy(() -> worklist.action("worklist-specialist", issue.id(), trainer(), Purpose.PERFORMANCE_PLANNING,
                new SpecialistWorklistService.ActionCommand("ACKNOWLEDGE", null, null)))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        error -> assertThat(error.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));
    }

    private ActingContext trainer() { return new ActingContext(ProfessionalRole.TRAINER); }

    private UUID account(String subject, String profile) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO identity_access.principal_account
                    (id, external_subject, status, profile_type, created_at, version)
                VALUES (?, ?, 'ACTIVE', ?, now(), 0)
                """, id, subject, profile);
        return id;
    }

    private void scope(UUID account) {
        jdbc.update("""
                INSERT INTO specialist.professional_scope
                    (specialist_account_id, scope_type, verification_status, verified_at, created_at)
                VALUES (?, 'TRAINER', 'VERIFIED', now(), now())
                """, account);
    }

    private void relationship() {
        jdbc.update("""
                INSERT INTO specialist.participant_specialist_relationship
                    (id, specialist_account_id, participant_account_id, status, activated_at)
                VALUES (?, ?, ?, 'ACTIVE', now())
                """, UUID.randomUUID(), specialist, participant);
    }
}
