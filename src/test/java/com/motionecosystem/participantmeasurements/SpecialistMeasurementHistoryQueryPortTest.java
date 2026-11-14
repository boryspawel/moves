package com.motionecosystem.participantmeasurements;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.motionecosystem.identityaccess.api.CurrentAccount;
import com.motionecosystem.identityaccess.api.CurrentAccountService;
import com.motionecosystem.identityaccess.api.ProfileType;
import com.motionecosystem.identityaccess.api.SpecialistAuthorizationPort;
import com.motionecosystem.identityaccess.api.SpecialistAuthorizationPort.ActingContext;
import com.motionecosystem.identityaccess.api.SpecialistAuthorizationPort.ProfessionalRole;
import com.motionecosystem.participant.api.ParticipantRecordLockPort;
import com.motionecosystem.specialist.api.SpecialistWorkspacePort;
import com.motionecosystem.participantmeasurements.api.SpecialistMeasurementHistoryQueryPort.MeasurementFact;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class SpecialistMeasurementHistoryQueryPortTest {
    private static final Instant FROM = Instant.parse("2030-01-01T00:00:00Z");
    private static final Instant TO = Instant.parse("2030-02-01T00:00:00Z");

    @Test
    void returns_no_measurement_facts_when_owner_capability_is_denied() {
        Fixture fixture = fixture();
        doThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, "measurement access denied"))
                .when(fixture.authorization).requireCapabilities(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());

        assertThat(fixture.service.measurementCandidates("specialist", fixture.participantId,
                new ActingContext(ProfessionalRole.TRAINER), FROM, TO)).isEmpty();

        verifyNoInteractions(fixture.measurements);
    }

    @Test
    void redactsMeasurementsFromAllQueryProjectionsWithoutScopedConsent() {
        Fixture fixture = fixture();
        doThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, "no measurement consent"))
                .when(fixture.authorization).requireCapabilities(org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
        ActingContext context = new ActingContext(ProfessionalRole.TRAINER);
        assertThat(fixture.service.recent("specialist", fixture.participantId, context, 3)).isEmpty();
        assertThat(fixture.service.forAppointment("specialist", fixture.participantId, context, UUID.randomUUID(), 10)).isEmpty();
        assertThat(fixture.service.find("specialist", fixture.participantId, context, UUID.randomUUID())).isEmpty();
        assertThat(fixture.service.measurementCandidates("specialist", fixture.participantId, context, FROM, TO)).isEmpty();
        verifyNoInteractions(fixture.measurements);
    }

    @Test
    void propagates_non_authorization_failures() {
        Fixture fixture = fixture();
        when(fixture.accounts.requireActive("specialist"))
                .thenThrow(new ResponseStatusException(HttpStatus.CONFLICT, "account state unavailable"));

        assertThatThrownBy(() -> fixture.service.measurementCandidates("specialist", fixture.participantId,
                new ActingContext(ProfessionalRole.TRAINER), FROM, TO))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("account state unavailable");
    }

    @Test
    void exposes_neutral_measurement_provenance_without_note_or_recorder() {
        Fixture fixture = fixture();
        UUID appointmentId = UUID.randomUUID();
        when(fixture.measurements.findByParticipantIdAndMeasuredAtGreaterThanEqualAndMeasuredAtLessThanOrderByMeasuredAtDescRecordedAtDescIdDesc(
                fixture.participantId, FROM, TO)).thenReturn(List.of(new ParticipantMeasurement(UUID.randomUUID(), fixture.participantId,
                appointmentId, "body-weight", BigDecimal.TEN, "kg", "MANUAL", FROM, "sensitive note", UUID.randomUUID(), TO, "key")));

        MeasurementFact fact = fixture.service.measurementCandidates("specialist", fixture.participantId,
                new ActingContext(ProfessionalRole.TRAINER), FROM, TO).getFirst();

        assertThat(fact.appointmentId()).isEqualTo(appointmentId);
        assertThat(Arrays.stream(MeasurementFact.class.getRecordComponents()).map(component -> component.getName()))
                .doesNotContain("note", "recordedByAccountId");
    }

    private static Fixture fixture() {
        ParticipantMeasurementRepository measurements = mock(ParticipantMeasurementRepository.class);
        CurrentAccountService accounts = mock(CurrentAccountService.class);
        SpecialistAuthorizationPort authorization = mock(SpecialistAuthorizationPort.class);
        UUID participantId = UUID.randomUUID();
        when(accounts.requireActive("specialist"))
                .thenReturn(new CurrentAccount(UUID.randomUUID(), "specialist", ProfileType.SPECIALIST));
        return new Fixture(participantId, measurements, accounts, authorization,
                new ParticipantMeasurementService(measurements, mock(ParticipantRecordLockPort.class), accounts, authorization,
                        mock(SpecialistWorkspacePort.class), Clock.fixed(FROM, ZoneOffset.UTC)));
    }

    private record Fixture(UUID participantId, ParticipantMeasurementRepository measurements, CurrentAccountService accounts,
                           SpecialistAuthorizationPort authorization, ParticipantMeasurementService service) { }
}
