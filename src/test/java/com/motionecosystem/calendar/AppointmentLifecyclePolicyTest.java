package com.motionecosystem.calendar;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AppointmentLifecyclePolicyTest {
    private final AppointmentLifecyclePolicy policy = new AppointmentLifecyclePolicy();
    private final Instant startsAt = Instant.parse("2030-06-10T12:00:00Z");
    private final Instant endsAt = Instant.parse("2030-06-10T13:00:00Z");

    @Test
    void permits_pre_start_edits_and_cancellation_only_for_scheduled_or_confirmed_appointments() {
        Appointment appointment = appointment(Appointment.Status.SCHEDULED);

        assertThat(policy.isEditable(appointment, startsAt.minusSeconds(1))).isTrue();
        assertThat(policy.allows(AppointmentLifecyclePolicy.Action.CANCEL, appointment, startsAt.minusSeconds(1))).isTrue();
        assertThat(policy.isEditable(appointment, startsAt)).isFalse();
        assertThat(policy.allows(AppointmentLifecyclePolicy.Action.CANCEL, appointment, startsAt)).isFalse();
        assertThat(policy.isEditable(appointment(Appointment.Status.COMPLETED), startsAt.minusSeconds(1))).isFalse();
    }

    @Test
    void permits_completion_after_start_and_no_show_after_end() {
        Appointment appointment = appointment(Appointment.Status.CONFIRMED);

        assertThat(policy.allows(AppointmentLifecyclePolicy.Action.COMPLETE, appointment, startsAt.minusSeconds(1))).isFalse();
        assertThat(policy.allows(AppointmentLifecyclePolicy.Action.COMPLETE, appointment, startsAt)).isTrue();
        assertThat(policy.allows(AppointmentLifecyclePolicy.Action.MARK_NO_SHOW, appointment, endsAt.minusSeconds(1))).isFalse();
        assertThat(policy.allows(AppointmentLifecyclePolicy.Action.MARK_NO_SHOW, appointment, endsAt)).isTrue();
        assertThat(policy.allows(AppointmentLifecyclePolicy.Action.COMPLETE, appointment(Appointment.Status.IN_PROGRESS), startsAt.minusSeconds(1))).isTrue();
    }

    @Test
    void exposes_only_actions_allowed_at_the_controlled_time() {
        assertThat(policy.availableActions(appointment(Appointment.Status.SCHEDULED), startsAt.minusSeconds(1)))
                .containsExactly("OPEN_APPOINTMENT", "OPEN_PARTICIPANT", "UPDATE", "CANCEL");
        assertThat(policy.availableActions(appointment(Appointment.Status.SCHEDULED), startsAt))
                .containsExactly("OPEN_APPOINTMENT", "OPEN_PARTICIPANT", "COMPLETE");
        assertThat(policy.availableActions(appointment(Appointment.Status.SCHEDULED), endsAt))
                .containsExactly("OPEN_APPOINTMENT", "OPEN_PARTICIPANT", "COMPLETE", "MARK_NO_SHOW");
        assertThat(policy.availableActions(appointment(Appointment.Status.COMPLETED), endsAt))
                .containsExactly("OPEN_APPOINTMENT", "OPEN_PARTICIPANT");
        assertThat(policy.availableActions(appointment(Appointment.Status.IN_PROGRESS), startsAt.minusSeconds(1)))
                .containsExactly("OPEN_APPOINTMENT", "OPEN_PARTICIPANT", "COMPLETE");
    }

    private Appointment appointment(Appointment.Status status) {
        Appointment appointment = new Appointment(UUID.randomUUID(), UUID.randomUUID(), startsAt, endsAt,
                Appointment.Type.CONSULTATION, Appointment.LocationMode.REMOTE, null, null, UUID.randomUUID(), startsAt.minusSeconds(60));
        appointment.status = status;
        return appointment;
    }
}
