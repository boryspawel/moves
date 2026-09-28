package com.motionecosystem.specialist;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.motionecosystem.application.MotionEcosystemApplication;
import com.motionecosystem.application.workspace.SpecialistParticipantReadService;
import com.motionecosystem.application.workspace.SpecialistParticipantMeasurementApplicationService;
import com.motionecosystem.availability.RecurringAvailabilityService;
import com.motionecosystem.calendar.Appointment;
import com.motionecosystem.calendar.AppointmentService;
import com.motionecosystem.consent.ConsentGrantService;
import com.motionecosystem.consent.api.ConsentDecisionPort;
import com.motionecosystem.identityaccess.api.CurrentAccountService;
import com.motionecosystem.identityaccess.api.ProfileType;
import com.motionecosystem.participant.ParticipantProfileService;
import com.motionecosystem.participant.ParticipantRecord;
import com.motionecosystem.participant.ParticipantAccessLink;
import com.motionecosystem.participantmeasurements.ParticipantMeasurementService;
import com.motionecosystem.participant.api.ParticipantMetricCatalog.PresetId;
import com.motionecosystem.participantgoals.ParticipantGoalService;
import com.motionecosystem.identityaccess.api.SpecialistAuthorizationPort.ActingContext;
import com.motionecosystem.identityaccess.api.SpecialistAuthorizationPort.ProfessionalRole;
import com.motionecosystem.support.PostgresTestConfiguration;
import com.motionecosystem.trainingexecution.TimelineExecutionAttemptFixture;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.time.DayOfWeek;
import java.time.LocalTime;
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
    @Autowired private RecurringAvailabilityService availability;
    @Autowired private SpecialistParticipantReadService reads;
    @Autowired private SpecialistParticipantMeasurementApplicationService measurementRecording;
    @Autowired private ParticipantMeasurementService measurements;
    @Autowired private ParticipantGoalService goals;
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
    void persistsOneCanonicalMeasurementOnReplayProjectsGoalAndExposesWorkspaceTimelineFact() {
        Fixture fixture = canonicalFixture(fixture(true, EnumSet.of(ConsentDecisionPort.DataScope.PLAN)));
        createWeightGoal(fixture);
        var command = new ParticipantMeasurementService.ParticipantMeasurementCommand(PresetId.BODY_WEIGHT, new java.math.BigDecimal("75.8"), null,
                Instant.parse("2030-06-10T10:00:00Z"), "Pomiar kontrolny", null, null, null, null);

        var created = measurementRecording.record(fixture.specialistSubject, fixture.participantId, "weight-measurement", command);
        var replay = measurementRecording.record(fixture.specialistSubject, fixture.participantId, "weight-measurement", command);

        assertThat(created.participantId()).isEqualTo(fixture.participantId)
                .isNotEqualTo(accounts.requireActive(fixture.participantSubject).id());
        assertThat(replay.id()).isEqualTo(created.id());
        assertThat(entityManager.createQuery("select count(m) from ParticipantMeasurement m", Long.class).getSingleResult()).isEqualTo(1L);
        assertThat(entityManager.createQuery("select o.sourceMeasurementId from GoalObservation o", UUID.class).getResultList())
                .containsExactly(created.id());
        for (int value = 76; value <= 79; value++) {
            measurementRecording.record(fixture.specialistSubject, fixture.participantId, "weight-" + value,
                    new ParticipantMeasurementService.ParticipantMeasurementCommand(PresetId.BODY_WEIGHT, new java.math.BigDecimal(value), null,
                            command.measuredAt().plusSeconds((long) value * 60), null, null, null, null, null));
        }
        var history = measurements.list(fixture.specialistSubject, fixture.participantId, 10);
        assertThat(history)
                .extracting(ParticipantMeasurementService.ParticipantMeasurementView::value)
                .usingComparatorForType(java.math.BigDecimal::compareTo, java.math.BigDecimal.class)
                .containsExactly(new java.math.BigDecimal("79"), new java.math.BigDecimal("78"), new java.math.BigDecimal("77"),
                        new java.math.BigDecimal("76"), new java.math.BigDecimal("75.8"));
        assertThat(reads.workspace(fixture.specialistSubject, fixture.participantId).recentMeasurements())
                .extracting(SpecialistParticipantReadService.RecentMeasurementView::value)
                .usingComparatorForType(java.math.BigDecimal::compareTo, java.math.BigDecimal.class)
                .containsExactly(new java.math.BigDecimal("79"), new java.math.BigDecimal("78"), new java.math.BigDecimal("77"));
        assertThat(reads.workspace(fixture.specialistSubject, fixture.participantId).recentMeasurements()).hasSize(3)
                .noneMatch(item -> item.measurementId().equals(created.id()));
        var persistedCreated = history.stream().filter(item -> item.id().equals(created.id())).findFirst().orElseThrow();
        var event = timeline(fixture, FROM, TO, 10, SpecialistParticipantReadService.Granularity.DETAIL,
                EnumSet.of(SpecialistParticipantReadService.TimelineType.MEASUREMENT), null).items().stream()
                .filter(item -> item.eventId().equals("participant-measurement:" + created.id())).findFirst().orElseThrow();
        assertThat(event).extracting(SpecialistParticipantReadService.ParticipantTimelineEvent::eventId,
                SpecialistParticipantReadService.ParticipantTimelineEvent::eventType,
                SpecialistParticipantReadService.ParticipantTimelineEvent::effectiveFrom,
                SpecialistParticipantReadService.ParticipantTimelineEvent::recordedAt)
                .containsExactly("participant-measurement:" + created.id(), "MEASUREMENT", command.measuredAt(), persistedCreated.recordedAt());
        assertThat(reads.timelineEvent(fixture.specialistSubject, fixture.participantId, event.eventId()).measurement().note()).isEqualTo("Pomiar kontrolny");
        Fixture other = fixture(true, EnumSet.of(ConsentDecisionPort.DataScope.PLAN));
        assertThatThrownBy(() -> reads.timelineEvent(other.specialistSubject, other.participantId, event.eventId()))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        error -> assertThat(error.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
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
    void usesTheRecordedPartialAndStoppedOutcomesForExecutionEventTypes() {
        assertThat(SpecialistParticipantReadService.executionEventType(executionStart("PARTIAL")))
                .isEqualTo("SESSION_PARTIALLY_COMPLETED");
        assertThat(SpecialistParticipantReadService.executionEventType(executionStart("STOPPED")))
                .isEqualTo("SESSION_STOPPED");
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
    void resolvesOnlyTheAuthorizedParticipantsAppointmentEventByStrictPublicId() throws Exception {
        Fixture fixture = fixture(true, EnumSet.of(ConsentDecisionPort.DataScope.PLAN));
        appointment(fixture, Instant.parse("2030-06-10T09:00:00Z"), "deep-link-event");
        String eventId = timeline(fixture, FROM, TO, 10, SpecialistParticipantReadService.Granularity.DETAIL,
                EnumSet.of(SpecialistParticipantReadService.TimelineType.APPOINTMENT), null).items().getFirst().eventId();
        var request = SecurityMockMvcRequestPostProcessors.jwt().jwt(token -> token.subject(fixture.specialistSubject))
                .authorities(new SimpleGrantedAuthority("ROLE_SPECIALIST"));

        mvc.perform(get("/api/v1/specialist/participants/{participantId}/timeline/events/{eventId}", fixture.participantId, eventId).with(request))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/specialist/participants/{participantId}/timeline/events/{eventId}", fixture.participantId,
                        "session-execution:" + UUID.randomUUID()).with(request))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/specialist/participants/{participantId}/timeline/events/{eventId}", fixture.participantId,
                        "appointment-event:1-1-1-1-1").with(request))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/specialist/participants/{participantId}/timeline/events/{eventId}", fixture.participantId,
                        "appointment-event:" + UUID.randomUUID()).with(request))
                .andExpect(status().isNotFound());

        Fixture other = fixture(true, EnumSet.of(ConsentDecisionPort.DataScope.PLAN));
        appointment(other, Instant.parse("2030-06-11T09:00:00Z"), "other-participant-event");
        String otherEventId = timeline(other, FROM, TO, 10, SpecialistParticipantReadService.Granularity.DETAIL,
                EnumSet.of(SpecialistParticipantReadService.TimelineType.APPOINTMENT), null).items().getFirst().eventId();
        mvc.perform(get("/api/v1/specialist/participants/{participantId}/timeline/events/{eventId}", fixture.participantId, otherEventId).with(request))
                .andExpect(status().isNotFound());

        relationship(other.specialistId, fixture.participantId);
        grant(fixture.participantSubject, other.specialistId, EnumSet.of(ConsentDecisionPort.DataScope.PLAN));
        var otherSpecialistRequest = SecurityMockMvcRequestPostProcessors.jwt().jwt(token -> token.subject(other.specialistSubject))
                .authorities(new SimpleGrantedAuthority("ROLE_SPECIALIST"));
        mvc.perform(get("/api/v1/specialist/participants/{participantId}/timeline/events/{eventId}", fixture.participantId, eventId)
                        .with(otherSpecialistRequest))
                .andExpect(status().isNotFound());
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
                .containsExactlyInAnyOrder(eventType, "APPOINTMENT_CREATED");
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

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void createWeightGoal(Fixture fixture) {
        try {
            Class categoryType = Class.forName("com.motionecosystem.participantgoals.ParticipantGoal$Category");
            Object category = Enum.valueOf(categoryType, "PERFORMANCE");
            var commandType = ParticipantGoalService.CreateParticipantGoalCommand.class;
            var constructor = commandType.getDeclaredConstructors()[0];
            Object command = constructor.newInstance(category, "Masa ciała", null, 50, null,
                    List.of(new ParticipantGoalService.OutcomeCommand("body-weight", new java.math.BigDecimal("80"),
                            new java.math.BigDecimal("75"), "kg", "body-weight", com.motionecosystem.participantgoals.TargetComparator.AT_MOST)));
            goals.create(fixture.specialistSubject, fixture.participantId, new ActingContext(ProfessionalRole.TRAINER), "weight-goal",
                    (ParticipantGoalService.CreateParticipantGoalCommand) command);
        } catch (ReflectiveOperationException failure) {
            throw new AssertionError("Unable to create focused goal fixture", failure);
        }
    }

    private Fixture fixture(boolean hasRelationship, EnumSet<ConsentDecisionPort.DataScope> scopes) {
        String suffix = UUID.randomUUID().toString();
        String participantSubject = "timeline-participant-" + suffix;
        String specialistSubject = "timeline-specialist-" + suffix;
        UUID participantId = account(participantSubject, ProfileType.PARTICIPANT);
        UUID specialistId = account(specialistSubject, ProfileType.SPECIALIST);
        participantRecord(participantId, specialistId);
        specialistProfiles.save(specialistId, "Timeline specialist", SpecialistKind.TRAINER, "UTC");
        availability.replace(specialistId, java.util.Arrays.stream(DayOfWeek.values())
                .map(day -> new RecurringAvailabilityService.Slot(day, LocalTime.of(8, 0), LocalTime.of(22, 0), "UTC")).toList());
        verifyScope(specialistId);
        Fixture fixture = new Fixture(participantSubject, specialistSubject, participantId, specialistId);
        if (hasRelationship) relationship(fixture);
        grant(fixture, scopes);
        return fixture;
    }

    private Fixture canonicalFixture(Fixture accountBacked) {
        UUID canonicalParticipantId = transactions.execute(status -> {
            ParticipantRecord record = new ParticipantRecord("Canonical measurement participant", ParticipantRecord.RelationshipContext.CLIENT,
                    null, null, null, accountBacked.specialistId, Instant.now());
            entityManager.persist(record);
            try {
                var constructor = ParticipantAccessLink.class.getDeclaredConstructor(UUID.class, UUID.class);
                constructor.setAccessible(true);
                entityManager.persist(constructor.newInstance(record.id(), accounts.requireActive(accountBacked.participantSubject).id()));
            } catch (ReflectiveOperationException failure) {
                throw new AssertionError("Unable to create canonical participant access link", failure);
            }
            ParticipantSpecialistRelationship relationship = entityManager.createQuery("""
                    select relationship from ParticipantSpecialistRelationship relationship
                    where relationship.specialistAccountId = :specialistId
                      and relationship.participantAccountId = :participantAccountId
                    """, ParticipantSpecialistRelationship.class)
                    .setParameter("specialistId", accountBacked.specialistId)
                    .setParameter("participantAccountId", accounts.requireActive(accountBacked.participantSubject).id())
                    .getSingleResult();
            set(relationship, "participantId", record.id());
            return record.id();
        });
        return new Fixture(accountBacked.participantSubject, accountBacked.specialistSubject, canonicalParticipantId, accountBacked.specialistId);
    }

    private UUID account(String subject, ProfileType profile) {
        UUID id = accounts.requireActive(subject).id();
        accounts.selectProfileType(subject, profile);
        return id;
    }

    private void relationship(Fixture fixture) {
        relationship(fixture.specialistId, fixture.participantId);
    }

    private void relationship(UUID specialistId, UUID participantId) {
        ParticipantSpecialistRelationship relationship = new ParticipantSpecialistRelationship();
        set(relationship, "id", UUID.randomUUID());
        set(relationship, "specialistAccountId", specialistId);
        set(relationship, "participantAccountId", participantId);
        set(relationship, "participantId", participantId);
        set(relationship, "status", ParticipantSpecialistRelationship.Status.ACTIVE);
        set(relationship, "activatedAt", Instant.now());
        transactions.executeWithoutResult(status -> entityManager.persist(relationship));
    }

    private void participantRecord(UUID participantId, UUID specialistId) {
        transactions.executeWithoutResult(status -> {
            ParticipantRecord record = new ParticipantRecord("Timeline participant", ParticipantRecord.RelationshipContext.CLIENT,
                    null, null, null, specialistId, Instant.now());
            set(record, "id", participantId);
            entityManager.persist(record);
        });
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
        grant(fixture.participantSubject, fixture.specialistId, scopes);
    }

    private void grant(String participantSubject, UUID specialistId, EnumSet<ConsentDecisionPort.DataScope> scopes) {
        UUID template = consents.publishTemplate("TIMELINE_" + UUID.randomUUID(), 1, "urn:timeline:" + UUID.randomUUID(), "EXPLICIT_CONSENT").id();
        consents.grant(participantSubject, new ConsentGrantService.GrantCommand(specialistId,
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

    private static com.motionecosystem.trainingexecution.api.ParticipantExecutionHistoryQueryPort.ExecutionStart executionStart(String outcome) {
        Instant now = Instant.parse("2030-06-10T09:00:00Z");
        return new com.motionecosystem.trainingexecution.api.ParticipantExecutionHistoryQueryPort.ExecutionStart(UUID.randomUUID(), UUID.randomUUID(), null,
                "COMPLETED", "STANDARD", now, now, null, now, null, outcome, 1, 1, 0, 2, 3, 4, "Ból");
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
