package com.motionecosystem.analytics.adherencemetrics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class AdherenceMetricsServiceTest {
    private final Clock clock = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);

    @Test
    void assignmentsAreDeterministicAndPersistedOncePerExperimentVersion() {
        var assignments = mock(AdherenceExperimentAssignmentRepository.class);
        var events = mock(AdherenceMetricEventRepository.class);
        UUID participant = UUID.fromString("8a4f91d9-48c6-4dde-bebf-1e86c1c9b1b2");
        when(assignments.findByParticipantAccountIdAndExperimentKeyAndExperimentVersion(any(), any(), any()))
                .thenReturn(Optional.empty());
        var service = new AdherenceMetricsService(assignments, events, clock);

        service.ensureAssignments(participant);
        ArgumentCaptor<AdherenceExperimentAssignment> first = ArgumentCaptor.forClass(AdherenceExperimentAssignment.class);
        verify(assignments, times(3)).saveAndFlush(first.capture());
        assertThat(first.getAllValues()).allSatisfy(assignment -> {
            assertThat(assignment.participantAccountId).isEqualTo(participant);
            assertThat(assignment.assignedAt).isEqualTo(clock.instant());
        });

        var secondAssignments = mock(AdherenceExperimentAssignmentRepository.class);
        when(secondAssignments.findByParticipantAccountIdAndExperimentKeyAndExperimentVersion(any(), any(), any()))
                .thenReturn(Optional.empty());
        new AdherenceMetricsService(secondAssignments, events, clock).ensureAssignments(participant);
        ArgumentCaptor<AdherenceExperimentAssignment> second = ArgumentCaptor.forClass(AdherenceExperimentAssignment.class);
        verify(secondAssignments, times(3)).saveAndFlush(second.capture());
        assertThat(second.getAllValues()).extracting(item -> item.variantCode)
                .containsExactlyElementsOf(first.getAllValues().stream().map(item -> item.variantCode).toList());
    }

    @Test
    void assignmentsHandleNegativeHashBuckets() {
        var assignments = mock(AdherenceExperimentAssignmentRepository.class);
        var events = mock(AdherenceMetricEventRepository.class);
        UUID participant = UUID.fromString("996ad860-2a9a-504f-8861-aeafd0b2ae29");
        when(assignments.findByParticipantAccountIdAndExperimentKeyAndExperimentVersion(any(), any(), any()))
                .thenReturn(Optional.empty());

        new AdherenceMetricsService(assignments, events, clock).ensureAssignments(participant);

        ArgumentCaptor<AdherenceExperimentAssignment> saved = ArgumentCaptor.forClass(AdherenceExperimentAssignment.class);
        verify(assignments, times(3)).saveAndFlush(saved.capture());
        assertThat(saved.getAllValues()).extracting(item -> item.variantCode)
                .containsExactly("BARRIER_FIRST", "TODAY_ONLY", "MINIMUM_PLAN");
    }

    @Test
    void eventContainsOnlyNeutralTechnicalFieldsAndHasRetention() {
        var assignments = mock(AdherenceExperimentAssignmentRepository.class);
        var events = mock(AdherenceMetricEventRepository.class);
        when(events.existsByDeduplicationKey(any())).thenReturn(false);
        UUID participant = UUID.randomUUID(); UUID reference = UUID.randomUUID();
        new AdherenceMetricsService(assignments, events, clock).record(participant, "BARRIER_REPORTED", reference,
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "BARRIER_RESPONSE_V1", "CONTACT_SPECIALIST");
        ArgumentCaptor<AdherenceMetricEvent> event = ArgumentCaptor.forClass(AdherenceMetricEvent.class);
        verify(events).saveAndFlush(event.capture());
        assertThat(event.getValue().deduplicationKey).isEqualTo("BARRIER_REPORTED:" + reference);
        assertThat(event.getValue().expiresAt).isEqualTo(clock.instant().plusSeconds(180L * 24 * 60 * 60));
        assertThat(AdherenceMetricEvent.class.getDeclaredFields()).extracting(java.lang.reflect.Field::getName)
                .doesNotContain("note", "text", "clinicalData", "shortText");
    }
}
