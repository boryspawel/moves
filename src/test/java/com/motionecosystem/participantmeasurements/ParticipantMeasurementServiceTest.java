package com.motionecosystem.participantmeasurements;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.motionecosystem.identityaccess.api.CurrentAccount;
import com.motionecosystem.identityaccess.api.CurrentAccountService;
import com.motionecosystem.identityaccess.api.ProfileType;
import com.motionecosystem.identityaccess.api.SpecialistAuthorizationPort;
import com.motionecosystem.participant.ParticipantRecord;
import com.motionecosystem.participant.ParticipantRecordRepository;
import com.motionecosystem.participant.api.ParticipantMetricCatalog.PresetId;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class ParticipantMeasurementServiceTest {
    private static final Instant NOW = Instant.parse("2030-06-10T12:00:00Z");

    @Test
    void recordsCanonicalParticipantFactWithDerivedBodyWeightAndManualProvenance() {
        Fixture fixture = fixture();
        when(fixture.measurements.saveAndFlush(any())).thenAnswer(call -> call.getArgument(0));

        var result = fixture.service.record("specialist", fixture.participantId, "weight-1",
                new ParticipantMeasurementService.ParticipantMeasurementCommand(PresetId.BODY_WEIGHT, new BigDecimal("75.8"), null, NOW, "after breakfast", null, null, null, null));

        assertThat(result.created()).isTrue();
        assertThat(result.measurement()).extracting(ParticipantMeasurementService.ParticipantMeasurementView::participantId,
                ParticipantMeasurementService.ParticipantMeasurementView::metricCode, ParticipantMeasurementService.ParticipantMeasurementView::unit,
                ParticipantMeasurementService.ParticipantMeasurementView::measurementMethod, ParticipantMeasurementService.ParticipantMeasurementView::source)
                .containsExactly(fixture.participantId, "body-weight", "kg", "body-weight", "SPECIALIST_MANUAL");
        verify(fixture.authorization).requireActiveRelationship(fixture.specialistId, fixture.participantId);
    }

    @Test
    void replaysSameKeyAndAppendsForAnotherKey() {
        Fixture fixture = fixture();
        when(fixture.measurements.saveAndFlush(any())).thenAnswer(call -> call.getArgument(0));
        var first = fixture.service.record("specialist", fixture.participantId, "same",
                command(PresetId.BODY_WEIGHT, null, null));
        ParticipantMeasurement saved = captured(fixture);
        when(fixture.measurements.findByRecordedByAccountIdAndParticipantIdAndIdempotencyKey(fixture.specialistId, fixture.participantId, "same"))
                .thenReturn(Optional.of(saved));
        var replay = fixture.service.record("specialist", fixture.participantId, "same", command(PresetId.BODY_WEIGHT, null, null));
        fixture.service.record("specialist", fixture.participantId, "different", command(PresetId.BODY_WEIGHT, null, null));

        assertThat(replay.created()).isFalse();
        assertThat(replay.measurement().id()).isEqualTo(first.measurement().id());
        verify(fixture.measurements, times(2)).saveAndFlush(any());
    }

    @Test
    void rejectsInvalidContextUnitsAndCustomRequirementsAndDoesNotImpersonateAnotherParticipant() {
        Fixture fixture = fixture();
        assertBad(() -> fixture.service.record("specialist", fixture.participantId, "bad-area",
                command(PresetId.BODY_CIRCUMFERENCE, "invalid", null)));
        assertBad(() -> fixture.service.record("specialist", fixture.participantId, "bad-unit",
                new ParticipantMeasurementService.ParticipantMeasurementCommand(PresetId.BODY_WEIGHT, BigDecimal.ONE, "lb", NOW, null, null, null, null, null)));
        assertBad(() -> fixture.service.record("specialist", fixture.participantId, "custom",
                new ParticipantMeasurementService.ParticipantMeasurementCommand(PresetId.CUSTOM, BigDecimal.ONE, null, NOW, null, null, null, null, null)));
        doThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, "active specialist relationship is required"))
                .when(fixture.authorization).requireActiveRelationship(fixture.specialistId, fixture.otherParticipantId);
        assertThatThrownBy(() -> fixture.service.record("specialist", fixture.otherParticipantId, "foreign", command(PresetId.BODY_WEIGHT, null, null)))
                .isInstanceOfSatisfying(ResponseStatusException.class, error -> assertThat(error.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));
        verify(fixture.measurements, never()).saveAndFlush(any());
    }

    private static ParticipantMeasurement captured(Fixture fixture) {
        ArgumentCaptor<ParticipantMeasurement> capture = ArgumentCaptor.forClass(ParticipantMeasurement.class);
        verify(fixture.measurements).saveAndFlush(capture.capture());
        return capture.getValue();
    }
    private static ParticipantMeasurementService.ParticipantMeasurementCommand command(PresetId preset, String bodyArea, String unit) {
        return new ParticipantMeasurementService.ParticipantMeasurementCommand(preset, BigDecimal.ONE, unit, NOW, null, bodyArea, null, null, null);
    }
    private static void assertBad(Runnable action) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(ResponseStatusException.class,
                error -> assertThat(error.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
    }
    private static Fixture fixture() {
        ParticipantMeasurementRepository measurements = mock(ParticipantMeasurementRepository.class);
        ParticipantRecordRepository participants = mock(ParticipantRecordRepository.class);
        CurrentAccountService accounts = mock(CurrentAccountService.class);
        SpecialistAuthorizationPort authorization = mock(SpecialistAuthorizationPort.class);
        UUID specialist = UUID.randomUUID(); UUID participant = UUID.randomUUID();
        when(accounts.requireActive("specialist")).thenReturn(new CurrentAccount(specialist, "specialist", ProfileType.SPECIALIST));
        when(participants.lockById(any())).thenReturn(Optional.of(mock(ParticipantRecord.class)));
        when(measurements.findByRecordedByAccountIdAndParticipantIdAndIdempotencyKey(any(), any(), any())).thenReturn(Optional.empty());
        return new Fixture(measurements, authorization, specialist, participant, UUID.randomUUID(),
                new ParticipantMeasurementService(measurements, participants, accounts, authorization, Clock.fixed(NOW, ZoneOffset.UTC)));
    }
    private record Fixture(ParticipantMeasurementRepository measurements, SpecialistAuthorizationPort authorization, UUID specialistId,
                           UUID participantId, UUID otherParticipantId, ParticipantMeasurementService service) { }
}
