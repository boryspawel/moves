package com.motionecosystem.participant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.motionecosystem.application.MotionEcosystemApplication;
import com.motionecosystem.identityaccess.api.CurrentAccountService;
import com.motionecosystem.identityaccess.api.ProfileType;
import com.motionecosystem.identityaccess.api.SpecialistAuthorizationPort;
import com.motionecosystem.participant.api.ParticipantInvitationDeliveryPort;
import com.motionecosystem.support.PostgresTestConfiguration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = MotionEcosystemApplication.class)
@Import({PostgresTestConfiguration.class, ParticipantAccessInvitationJpaIntegrationTest.TestDeliveryConfiguration.class})
class ParticipantAccessInvitationJpaIntegrationTest {
    @Autowired ParticipantAccessInvitationService service;
    @Autowired ParticipantRecordRepository records;
    @Autowired ParticipantAccessLinkRepository links;
    @Autowired ParticipantAccessInvitationRepository invitations;
    @Autowired ParticipantClaimContextRepository contexts;
    @Autowired CurrentAccountService accounts;
    @Autowired CapturingDelivery delivery;
    @Autowired TestAuthorization invitationAuthorization;
    @Autowired ParticipantProfileService profiles;
    @Autowired ParticipantContextService participantContexts;
    @Autowired WebApplicationContext webContext;
    @Autowired FilterChainProxy securityFilterChain;
    @Autowired jakarta.persistence.EntityManager entityManager;
    @Autowired org.springframework.transaction.PlatformTransactionManager transactionManager;

    private UUID specialistId;
    private UUID participantId;
    private MockMvc mvc;
    private static int sequence;

    @BeforeEach
    void setUp() {
        String suffix = "invite-jpa-" + ++sequence;
        specialistId = accounts.selectProfileType(suffix + "-specialist", ProfileType.SPECIALIST).id();
        participantId = records.save(new ParticipantRecord("Participant", ParticipantRecord.RelationshipContext.CLIENT,
                "participant@example.test", null, ZoneOffset.UTC, specialistId, Instant.now())).id();
        mvc = MockMvcBuilders.webAppContextSetup(webContext).addFilters(securityFilterChain).build();
        delivery.sent.clear();
        invitationAuthorization.allowed = true;
    }

    @AfterEach
    void clean() {
        new org.springframework.transaction.support.TransactionTemplate(transactionManager).executeWithoutResult(ignored -> {
            entityManager.createNativeQuery("DELETE FROM participant_goals.goal_idempotency WHERE specialist_account_id = :specialist")
                    .setParameter("specialist", specialistId).executeUpdate();
            entityManager.createNativeQuery("DELETE FROM participant_goals.participant_goal WHERE specialist_account_id = :specialist")
                    .setParameter("specialist", specialistId).executeUpdate();
        });
        contexts.deleteAll();
        invitations.deleteAll();
        links.deleteAll();
        records.deleteAll();
    }

    @Test
    void issueBootstrapAndClaimUseExistingRecordAndPersistOnlyHashes() {
        long recordsBeforeClaim = records.count();
        long linksBeforeClaim = links.count();
        var issued = issue("participant@example.test");
        String rawToken = delivery.latest().rawToken();
        var context = service.bootstrap(rawToken);
        String participantSubject = "invite-jpa-" + sequence + "-participant";
        UUID accountId = accounts.selectProfileType(participantSubject, ProfileType.PARTICIPANT).id();
        profiles.save(accountId, "Claiming participant");
        participantContexts.setTimeZone(accountId, "Europe/Warsaw");

        var claimed = service.claim(participantSubject, "participant@example.test", context.secret());

        assertThat(claimed.participantId()).isEqualTo(participantId);
        assertThat(records.count()).isEqualTo(recordsBeforeClaim);
        assertThat(links.count()).isEqualTo(linksBeforeClaim + 1);
        assertThat(records.findById(participantId).orElseThrow().timeZoneId()).isEqualTo("Z");
        assertThat(invitations.findById(issued.invitationId()).orElseThrow().tokenHash).isNotEqualTo(rawToken);
        assertThat(contexts.findBySecretHash(context.secret())).isEmpty();
    }

    @Test
    void realClaimExposesAllOwnGoalStatusesButNeverAnotherParticipantsGoalAndInitializesOnlyMissingTimezone() throws Exception {
        ParticipantRecord record = records.findById(participantId).orElseThrow();
        record.updateTimeZone(null, Instant.now());
        records.saveAndFlush(record);
        UUID foreignParticipant = records.save(new ParticipantRecord("Foreign", ParticipantRecord.RelationshipContext.CLIENT,
                "foreign@example.test", null, ZoneOffset.UTC, specialistId, Instant.now())).id();
        String activeGoalId = createGoal(participantId, "Active", "active-goal");
        String completedGoalId = createGoal(participantId, "Completed", "completed-goal");
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/specialist/clients/{participantId}/goals/{goalId}/achieve", participantId, completedGoalId)
                        .param("actingContext", "TRAINER").header("Idempotency-Key", "achieve-goal")
                        .with(specialistJwt()).contentType("application/json").content("{\"expectedVersion\":0}"))
                .andExpect(status().isOk());
        String foreignGoalId = createGoal(foreignParticipant, "Foreign", "foreign-goal");

        issue("participant@example.test");
        var context = service.bootstrap(delivery.latest().rawToken());
        String participantSubject = "invite-jpa-" + sequence + "-participant";
        UUID accountId = accounts.selectProfileType(participantSubject, ProfileType.PARTICIPANT).id();
        profiles.save(accountId, "Claiming participant");
        participantContexts.setTimeZone(accountId, "Europe/Warsaw");
        long recordsBeforeClaim = records.count();
        service.claim(participantSubject, "participant@example.test", context.secret());

        mvc.perform(get("/api/v1/participant/goals").with(participantJwt(participantSubject)))
                .andExpect(status().isOk()).andExpect(jsonPath("$[?(@.id == '%s')].status".formatted(activeGoalId)).value(org.hamcrest.Matchers.contains("ACTIVE")))
                .andExpect(jsonPath("$[?(@.id == '%s')].status".formatted(completedGoalId)).value(org.hamcrest.Matchers.contains("ACHIEVED")));
        mvc.perform(get("/api/v1/participant/goals/{goalId}", foreignGoalId).with(participantJwt(participantSubject)))
                .andExpect(status().isForbidden());
        assertThat(records.count()).isEqualTo(recordsBeforeClaim);
        assertThat(records.findById(participantId).orElseThrow().timeZoneId()).isEqualTo("Europe/Warsaw");

        participantContexts.setTimeZone(accountId, "America/New_York");
        assertThat(records.findById(participantId).orElseThrow().timeZoneId()).isEqualTo("America/New_York");
    }

    @Test
    void sameContextAndSameAccountRetryIsIdempotent() {
        issue("participant@example.test");
        var context = service.bootstrap(delivery.latest().rawToken());
        String participantSubject = "invite-jpa-" + sequence + "-participant";
        accounts.selectProfileType(participantSubject, ProfileType.PARTICIPANT);

        service.claim(participantSubject, "participant@example.test", context.secret());
        var retry = service.claim(participantSubject, "participant@example.test", context.secret());

        assertThat(retry.status()).isEqualTo("CLAIMED");
        assertThat(links.count()).isEqualTo(1);
    }

    @Test
    void rejectsEmailMismatchAndReplayByAnotherAccount() {
        issue("participant@example.test");
        var context = service.bootstrap(delivery.latest().rawToken());
        String first = "invite-jpa-" + sequence + "-first";
        String second = "invite-jpa-" + sequence + "-second";
        accounts.selectProfileType(first, ProfileType.PARTICIPANT);
        accounts.selectProfileType(second, ProfileType.PARTICIPANT);

        assertCode(() -> service.claim(first, "different@example.test", context.secret()), HttpStatus.FORBIDDEN, "EMAIL_MISMATCH");
        service.claim(first, "participant@example.test", context.secret());
        assertCode(() -> service.claim(second, "participant@example.test", context.secret()), HttpStatus.CONFLICT, "ALREADY_CLAIMED_OTHER");
    }

    @Test
    void expiredOrRevokedTokensCannotBootstrapAndReissueInvalidatesOldToken() {
        issue("participant@example.test");
        String old = delivery.latest().rawToken();
        ParticipantAccessInvitation invitation = invitations.findByTokenHash(hash(old)).orElseThrow();
        invitation.expiresAt = Instant.now().minusSeconds(1);
        invitations.saveAndFlush(invitation);
        assertCode(() -> service.bootstrap(old), HttpStatus.BAD_REQUEST, "INVITATION_EXPIRED");
        assertThat(service.status(specialistSubject(), participantId, trainer()).status()).isEqualTo("EXPIRED");

        var reissued = service.reissue(specialistSubject(), participantId, "participant@example.test", trainer());
        assertCode(() -> service.bootstrap(old), HttpStatus.BAD_REQUEST, "INVITATION_REVOKED");
        assertThat(service.bootstrap(delivery.latest().rawToken()).status()).isEqualTo("PENDING");
        assertThat(reissued.status()).isEqualTo("PENDING");
    }

    @Test
    void matchingActiveLinkCompletesClaimButSuspendedLinkConflicts() {
        issue("participant@example.test");
        var context = service.bootstrap(delivery.latest().rawToken());
        String subject = "invite-jpa-" + sequence + "-participant";
        UUID account = accounts.selectProfileType(subject, ProfileType.PARTICIPANT).id();
        links.saveAndFlush(new ParticipantAccessLink(participantId, account));

        assertThat(service.claim(subject, "participant@example.test", context.secret()).status()).isEqualTo("CLAIMED");
        assertThat(links.count()).isEqualTo(1);
    }

    @Test
    void suspendedMatchingLinkIsNeverReactivatedByAnInvitationClaim() {
        issue("participant@example.test");
        var context = service.bootstrap(delivery.latest().rawToken());
        String subject = "invite-jpa-" + sequence + "-participant";
        UUID account = accounts.selectProfileType(subject, ProfileType.PARTICIPANT).id();
        ParticipantAccessLink suspended = new ParticipantAccessLink(participantId, account);
        ReflectionTestUtils.setField(suspended, "accessStatus", ParticipantAccessLink.Status.SUSPENDED);
        links.saveAndFlush(suspended);

        assertCode(() -> service.claim(subject, "participant@example.test", context.secret()), HttpStatus.CONFLICT, "ACCESS_LINK_CONFLICT");
        assertThat(links.findByParticipantId(participantId).orElseThrow().accessStatus()).isEqualTo(ParticipantAccessLink.Status.SUSPENDED);
    }

    @Test
    void unauthorizedSpecialistAndArchivedParticipantCannotIssue() {
        invitationAuthorization.allowed = false;
        assertThatThrownBy(() -> issue("participant@example.test"))
                .isInstanceOfSatisfying(ResponseStatusException.class, error -> assertThat(error.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));
        invitationAuthorization.allowed = true;
        ParticipantRecord archived = records.findById(participantId).orElseThrow();
        archived.archive(Instant.now());
        records.saveAndFlush(archived);
        assertCode(() -> issue("participant@example.test"), HttpStatus.CONFLICT, "PARTICIPANT_ARCHIVED");
    }

    @Test
    void concurrentClaimsForOneInvitationCreateOneLinkAndRemainRetryable() throws Exception {
        issue("participant@example.test");
        var context = service.bootstrap(delivery.latest().rawToken());
        String subject = "invite-jpa-" + sequence + "-participant";
        accounts.selectProfileType(subject, ProfileType.PARTICIPANT);
        List<Future<String>> results = invokeConcurrently(
                () -> claimResult(subject, context.secret()), () -> claimResult(subject, context.secret()));

        assertThat(results.get(0).get()).isEqualTo("CLAIMED");
        assertThat(results.get(1).get()).isEqualTo("CLAIMED");
        assertThat(links.findByParticipantId(participantId)).isPresent();
        assertThat(service.claim(subject, "participant@example.test", context.secret()).status()).isEqualTo("CLAIMED");
    }

    @Test
    void concurrentClaimsForTwoParticipantsByOneAccountLeaveExactlyOneLink() throws Exception {
        UUID secondParticipant = records.save(new ParticipantRecord("Second", ParticipantRecord.RelationshipContext.CLIENT,
                "participant@example.test", null, ZoneOffset.UTC, specialistId, Instant.now())).id();
        issue("participant@example.test");
        var firstContext = service.bootstrap(delivery.latest().rawToken());
        service.issue(specialistSubject(), secondParticipant, "participant@example.test", trainer());
        var secondContext = service.bootstrap(delivery.latest().rawToken());
        String subject = "invite-jpa-" + sequence + "-participant";
        accounts.selectProfileType(subject, ProfileType.PARTICIPANT);
        List<Future<String>> results = invokeConcurrently(
                () -> claimResult(subject, firstContext.secret()), () -> claimResult(subject, secondContext.secret()));

        List<String> outcomes = List.of(results.get(0).get(), results.get(1).get());
        assertThat(outcomes).contains("CLAIMED");
        assertThat(outcomes).allMatch(value -> value.equals("CLAIMED") || value.equals("ACCESS_LINK_CONFLICT"));
        assertThat(links.findByPrincipalAccountId(accounts.requireActive(subject).id())).isPresent();
        assertThat(links.findAll().stream().filter(link -> link.principalAccountId().equals(accounts.requireActive(subject).id())).count()).isEqualTo(1);
    }

    private ParticipantAccessInvitationService.IssueView issue(String email) {
        return service.issue(specialistSubject(), participantId, email, trainer());
    }

    private String specialistSubject() { return "invite-jpa-" + sequence + "-specialist"; }
    private static SpecialistAuthorizationPort.ActingContext trainer() { return new SpecialistAuthorizationPort.ActingContext(SpecialistAuthorizationPort.ProfessionalRole.TRAINER); }
    private static String hash(String value) { try { return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8))); } catch (Exception error) { throw new IllegalStateException(error); } }
    private static void assertCode(org.assertj.core.api.ThrowableAssert.ThrowingCallable call, HttpStatus status, String code) {
        assertThatThrownBy(call).isInstanceOfSatisfying(ParticipantInvitationProblem.class, error -> {
            assertThat(error.getStatusCode()).isEqualTo(status);
            assertThat(error.code()).isEqualTo(code);
        });
    }
    private List<Future<String>> invokeConcurrently(Callable<String> first, Callable<String> second) {
        var executor = Executors.newFixedThreadPool(2);
        try { return List.of(executor.submit(first), executor.submit(second)); }
        finally { executor.shutdown(); }
    }
    private String claimResult(String subject, String secret) {
        try { return service.claim(subject, "participant@example.test", secret).status(); }
        catch (ParticipantInvitationProblem error) { return error.code(); }
    }
    private static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor participantJwt(String subject) {
        return jwt().jwt(token -> token.subject(subject)).authorities(new SimpleGrantedAuthority("ROLE_PARTICIPANT"));
    }
    private org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor specialistJwt() {
        return jwt().jwt(token -> token.subject(specialistSubject())).authorities(new SimpleGrantedAuthority("ROLE_SPECIALIST"));
    }
    private String createGoal(UUID participant, String title, String key) throws Exception {
        String body = mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/specialist/clients/{participantId}/goals", participant)
                        .param("actingContext", "TRAINER").header("Idempotency-Key", key).with(specialistJwt())
                        .contentType("application/json").content("{\"perspective\":\"PERFORMANCE\",\"title\":\"" + title + "\",\"priority\":10,\"outcomes\":[]}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return new com.fasterxml.jackson.databind.ObjectMapper().readTree(body).required("id").asText();
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class TestDeliveryConfiguration {
        @Bean @Primary CapturingDelivery capturingDelivery() { return new CapturingDelivery(); }
        @Bean @Primary TestAuthorization invitationAuthorization() { return new TestAuthorization(); }
    }

    static class CapturingDelivery implements ParticipantInvitationDeliveryPort {
        final List<SentInvitation> sent = new ArrayList<>();
        @Override public void deliver(UUID invitationId, String intendedEmail, String rawToken, Instant expiresAt) { sent.add(new SentInvitation(invitationId, intendedEmail, rawToken, expiresAt)); }
        SentInvitation latest() { return sent.getLast(); }
    }
    static class TestAuthorization implements SpecialistAuthorizationPort {
        volatile boolean allowed = true;
        @Override public void requireActiveRelationship(UUID specialistId, UUID participantId) { }
        @Override public AuthorizationDecision requireCapabilities(UUID actor, UUID participant, ActingContext context, Set<Capability> capabilities, Purpose purpose) {
            if (!allowed) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "relationship is required");
            return new AuthorizationDecision(actor, participant, context.role(), purpose, capabilities);
        }
    }
    record SentInvitation(UUID id, String email, String rawToken, Instant expiresAt) { }
}
