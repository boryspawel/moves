package com.motionecosystem.specialist;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.motionecosystem.application.MotionEcosystemApplication;
import com.motionecosystem.calendar.Appointment;
import com.motionecosystem.calendar.AppointmentService;
import com.motionecosystem.consent.ConsentGrantService;
import com.motionecosystem.consent.api.ConsentDecisionPort;
import com.motionecosystem.identityaccess.api.CurrentAccountService;
import com.motionecosystem.identityaccess.api.ProfileType;
import com.motionecosystem.participant.ParticipantProfileService;
import com.motionecosystem.support.PostgresTestConfiguration;
import com.motionecosystem.trainingexecution.TimelineExecutionAttemptFixture;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.server.ResponseStatusException;

@SpringBootTest(classes = MotionEcosystemApplication.class)
@Import(PostgresTestConfiguration.class)
class SpecialistParticipantReadIntegrationTest {
    private static final Instant FROM = Instant.parse("2030-06-01T00:00:00Z");
    private static final Instant TO = Instant.parse("2030-06-15T00:00:00Z");

    @Autowired private CurrentAccountService accounts;
    @Autowired private ParticipantProfileService participantProfiles;
    @Autowired private SpecialistProfileService specialistProfiles;
    @Autowired private ConsentGrantService consents;
    @Autowired private AppointmentService appointments;
    @Autowired private SpecialistParticipantReadService reads;
    @Autowired private EntityManager entityManager;
    @Autowired private TransactionTemplate transactions;
    @Autowired private WebApplicationContext context;
    @Autowired private FilterChainProxy securityFilterChain;
    private MockMvc mvc;

    @BeforeEach
    void setUp() { mvc = MockMvcBuilders.webAppContextSetup(context).addFilters(securityFilterChain).build(); }

    @Test
    void workspaceShowsAnActiveRelationshipAndRejectsAnUnrelatedSpecialist() {
        Fixture active = fixture(true, EnumSet.of(ConsentDecisionPort.DataScope.PLAN));
        assertThat(reads.workspace(active.specialistSubject, active.participantId).relationship().status()).isEqualTo("ACTIVE");
        Fixture unrelated = fixture(false, EnumSet.of(ConsentDecisionPort.DataScope.PLAN));
        assertThatThrownBy(() -> reads.workspace(unrelated.specialistSubject, active.participantId))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        error -> assertThat(error.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));
    }

    @Test
    void executionEventsAreRedactedUntilExecutionConsentIsGranted() {
        Fixture fixture = fixture(true, EnumSet.of(ConsentDecisionPort.DataScope.PLAN));
        execution(fixture.participantId, Instant.parse("2030-06-10T09:00:00Z"), "execution-redacted");
        assertThat(timeline(fixture, FROM, TO, 10, SpecialistParticipantReadService.Granularity.DETAIL,
                EnumSet.of(SpecialistParticipantReadService.TimelineType.EXECUTION), null).items()).isEmpty();
        grant(fixture, EnumSet.of(ConsentDecisionPort.DataScope.EXECUTION));
        assertThat(timeline(fixture, FROM, TO, 10, SpecialistParticipantReadService.Granularity.DETAIL,
                EnumSet.of(SpecialistParticipantReadService.TimelineType.EXECUTION), null).items())
                .singleElement().extracting(SpecialistParticipantReadService.ParticipantTimelineEvent::eventType)
                .isEqualTo("SESSION_COMPLETED");
    }

    @Test
    void combinesSourcesInDeterministicOrderAndUsesCursorForCalendarSeek() {
        Fixture fixture = fixture(true, EnumSet.of(ConsentDecisionPort.DataScope.PLAN, ConsentDecisionPort.DataScope.EXECUTION));
        Instant effectiveAt = Instant.parse("2030-06-10T09:00:00Z");
        execution(fixture.participantId, effectiveAt, "combined-order");
        appointment(fixture, effectiveAt, "calendar-seek");
        var firstPage = timeline(fixture, FROM, TO, 1, SpecialistParticipantReadService.Granularity.DETAIL,
                EnumSet.of(SpecialistParticipantReadService.TimelineType.APPOINTMENT, SpecialistParticipantReadService.TimelineType.EXECUTION), null);
        var secondPage = timeline(fixture, FROM, TO, 1, SpecialistParticipantReadService.Granularity.DETAIL,
                EnumSet.of(SpecialistParticipantReadService.TimelineType.APPOINTMENT, SpecialistParticipantReadService.TimelineType.EXECUTION), firstPage.nextCursor());
        assertThat(firstPage.items()).extracting(SpecialistParticipantReadService.ParticipantTimelineEvent::category).containsExactly("EXECUTION");
        assertThat(firstPage.nextCursor()).isNotBlank();
        assertThat(secondPage.items()).extracting(SpecialistParticipantReadService.ParticipantTimelineEvent::category).containsExactly("APPOINTMENT");
        assertThat(secondPage.nextCursor()).isNull();
    }

    @Test
    void validatesRangeLimitAndUnsupportedType() throws Exception {
        Fixture fixture = fixture(true, EnumSet.of(ConsentDecisionPort.DataScope.PLAN));
        assertBadRequest(() -> timeline(fixture, TO, FROM, 1, SpecialistParticipantReadService.Granularity.DETAIL,
                EnumSet.of(SpecialistParticipantReadService.TimelineType.APPOINTMENT), null));
        assertBadRequest(() -> timeline(fixture, FROM, TO, 0, SpecialistParticipantReadService.Granularity.DETAIL,
                EnumSet.of(SpecialistParticipantReadService.TimelineType.APPOINTMENT), null));
        assertBadRequest(() -> timeline(fixture, FROM.minusSeconds(15L * 24 * 60 * 60), TO, 1,
                SpecialistParticipantReadService.Granularity.DETAIL, EnumSet.of(SpecialistParticipantReadService.TimelineType.APPOINTMENT), null));
        mvc.perform(get("/api/v1/specialist/participants/{participantId}/timeline", fixture.participantId)
                        .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(token -> token.subject(fixture.specialistSubject))
                                .authorities(new SimpleGrantedAuthority("ROLE_SPECIALIST")))
                        .param("types", "APPOINTMENT,UNSUPPORTED"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void aggregatesRepeatedEventsForWeekAndMonthWhileKeepingAppointmentsIndividual() {
        Fixture fixture = fixture(true, EnumSet.of(ConsentDecisionPort.DataScope.PLAN, ConsentDecisionPort.DataScope.EXECUTION));
        execution(fixture.participantId, Instant.parse("2030-06-03T09:00:00Z"), "week-one");
        execution(fixture.participantId, Instant.parse("2030-06-05T09:00:00Z"), "week-two");
        appointment(fixture, Instant.parse("2030-06-04T11:00:00Z"), "aggregate-appointment");
        assertAggregateAndAppointment(timeline(fixture, FROM, TO, 10, SpecialistParticipantReadService.Granularity.WEEK,
                EnumSet.of(SpecialistParticipantReadService.TimelineType.APPOINTMENT, SpecialistParticipantReadService.TimelineType.EXECUTION), null).items(), "WEEK_SUMMARY");
        assertAggregateAndAppointment(timeline(fixture, Instant.parse("2030-06-01T00:00:00Z"), Instant.parse("2030-07-01T00:00:00Z"), 10,
                SpecialistParticipantReadService.Granularity.MONTH,
                EnumSet.of(SpecialistParticipantReadService.TimelineType.APPOINTMENT, SpecialistParticipantReadService.TimelineType.EXECUTION), null).items(), "MONTH_SUMMARY");
    }

    private void assertAggregateAndAppointment(List<SpecialistParticipantReadService.ParticipantTimelineEvent> items, String eventType) {
        assertThat(items).extracting(SpecialistParticipantReadService.ParticipantTimelineEvent::eventType)
                .containsExactlyInAnyOrder(eventType, "APPOINTMENT_SCHEDULED");
        assertThat(items).filteredOn(item -> eventType.equals(item.eventType())).singleElement().satisfies(item -> {
            assertThat(item.detail().sourceEventCount()).isEqualTo(2);
            assertThat(item.detail().sourceFrom()).isEqualTo(Instant.parse("2030-06-03T09:00:00Z"));
            assertThat(item.detail().sourceTo()).isEqualTo(Instant.parse("2030-06-05T09:00:00Z"));
        });
    }

    private SpecialistParticipantReadService.ParticipantTimelineView timeline(Fixture fixture, Instant from, Instant to, int limit,
            SpecialistParticipantReadService.Granularity granularity, EnumSet<SpecialistParticipantReadService.TimelineType> types, String cursor) {
        return reads.timeline(fixture.specialistSubject, fixture.participantId,
                new SpecialistParticipantReadService.TimelineQuery(from, to, types, granularity, cursor, limit));
    }

    private Fixture fixture(boolean hasRelationship, EnumSet<ConsentDecisionPort.DataScope> scopes) {
        String suffix = UUID.randomUUID().toString();
        String participantSubject = "timeline-participant-" + suffix;
        String specialistSubject = "timeline-specialist-" + suffix;
        UUID participantId = account(participantSubject, ProfileType.PARTICIPANT);
        UUID specialistId = account(specialistSubject, ProfileType.SPECIALIST);
        participantProfiles.save(participantId, "Timeline participant");
        specialistProfiles.save(specialistId, "Timeline specialist", SpecialistKind.TRAINER, "UTC");
        verifyScope(specialistId);
        Fixture fixture = new Fixture(participantSubject, specialistSubject, participantId, specialistId);
        if (hasRelationship) relationship(fixture);
        grant(fixture, scopes);
        return fixture;
    }

    private UUID account(String subject, ProfileType profile) {
        UUID id = accounts.requireActive(subject).id();
        accounts.selectProfileType(subject, profile);
        return id;
    }

    private void relationship(Fixture fixture) {
        ParticipantSpecialistRelationship relationship = new ParticipantSpecialistRelationship();
        set(relationship, "id", UUID.randomUUID());
        set(relationship, "specialistAccountId", fixture.specialistId);
        set(relationship, "participantAccountId", fixture.participantId);
        set(relationship, "status", ParticipantSpecialistRelationship.Status.ACTIVE);
        set(relationship, "activatedAt", Instant.now());
        transactions.executeWithoutResult(status -> entityManager.persist(relationship));
    }

    private void verifyScope(UUID specialistId) {
        transactions.executeWithoutResult(status -> {
            ProfessionalScope scope = entityManager.find(ProfessionalScope.class,
                    new ProfessionalScope.Id(specialistId, SpecialistKind.TRAINER));
            scope.status = ProfessionalScope.VerificationStatus.VERIFIED;
            scope.verifiedAt = Instant.now();
        });
    }

    private void grant(Fixture fixture, EnumSet<ConsentDecisionPort.DataScope> scopes) {
        UUID template = consents.publishTemplate("TIMELINE_" + UUID.randomUUID(), 1, "urn:timeline:" + UUID.randomUUID(), "EXPLICIT_CONSENT").id();
        consents.grant(fixture.participantSubject, new ConsentGrantService.GrantCommand(fixture.specialistId,
                ConsentDecisionPort.Purpose.PERFORMANCE_PLANNING, template, scopes, null, null));
    }

    private void appointment(Fixture fixture, Instant startsAt, String key) {
        appointments.create(fixture.specialistSubject, key, new AppointmentService.CreateCommand(fixture.participantId, startsAt,
                startsAt.plusSeconds(3600), Appointment.Type.CONSULTATION, Appointment.LocationMode.REMOTE, null, "Timeline appointment"));
    }

    private void execution(UUID participantId, Instant completedAt, String key) {
        transactions.executeWithoutResult(status -> TimelineExecutionAttemptFixture.completed(
                entityManager, participantId, UUID.randomUUID(), key, completedAt));
    }

    private static void set(Object target, String fieldName, Object value) {
        try {
            var field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException failure) {
            throw new AssertionError("Unable to create JPA relationship fixture", failure);
        }
    }

    private static void assertBadRequest(Runnable action) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(ResponseStatusException.class,
                error -> assertThat(error.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    private record Fixture(String participantSubject, String specialistSubject, UUID participantId, UUID specialistId) { }
}
