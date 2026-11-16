package com.motionecosystem.moves.mobile.app

import java.util.UUID
import org.junit.Assert.*
import org.junit.Test

class NavigationBackTest {
    @Test fun appointmentBackRestoresParticipantOnlyForWorkspaceOrigin() {
        val clientId = UUID.randomUUID()
        val appointment = ContextSelection.Appointment(clientId, UUID.randomUUID())
        assertEquals(clientId, participantToRestoreOnAppointmentBack("context", appointment))
        assertNull(participantToRestoreOnAppointmentBack("day", appointment))
        assertNull(participantToRestoreOnAppointmentBack("context", ContextSelection.Participant(clientId)))
        assertNull(participantToRestoreOnAppointmentBack("context", ContextSelection.Appointment(null, UUID.randomUUID())))
        assertNull(participantToRestoreOnAppointmentBack(null, appointment))
    }

    @Test fun backIsBlockedDuringMutationsButAllowedAfterUncertainResponse() {
        assertFalse(canLeaveAppointment(AppointmentStartState.SAVING))
        assertTrue(canLeaveAppointment(AppointmentStartState.UNCERTAIN))
        assertTrue(canLeaveAppointment(AppointmentStartState.IDLE))
        assertFalse(canLeaveSchedule(ScheduleSaveState.Saving))
        assertFalse(canLeaveSchedule(ScheduleSaveState.Saved(
            com.motionecosystem.moves.mobile.api.AppointmentView(appointmentId = UUID.randomUUID())
        )))
        assertTrue(canLeaveSchedule(ScheduleSaveState.Uncertain))
        assertTrue(canLeaveSchedule(ScheduleSaveState.Idle))
    }
}
