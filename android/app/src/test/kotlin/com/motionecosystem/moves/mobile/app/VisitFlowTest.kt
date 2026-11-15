package com.motionecosystem.moves.mobile.app

import com.motionecosystem.moves.mobile.api.*
import java.util.UUID
import org.junit.Assert.*
import org.junit.Test

class VisitFlowTest {
    private val participant = UUID.randomUUID()
    private val appointmentId = UUID.randomUUID()
    private val session = UUID.randomUUID()
    private val selected = ContextSelection.Appointment(participant, appointmentId)
    private val inProgress = ReadState.Ready(AppointmentView(
        appointmentId = appointmentId, participantId = participant,
        status = AppointmentStatus.IN_PROGRESS, plannedSessionId = session
    ))

    @Test fun visitContextRequiresBothIdsToMatchAuthorizedGet() {
        assertEquals(participant, verifiedVisit(selected, inProgress)?.participantId)
        assertEquals(appointmentId, verifiedVisit(selected, inProgress)?.appointmentId)
        assertNull(verifiedVisit(ContextSelection.Appointment(participant, UUID.randomUUID()), inProgress))
        assertNull(verifiedVisit(ContextSelection.Appointment(UUID.randomUUID(), appointmentId), inProgress))
        assertNull(verifiedVisit(selected, ReadState.Loading))
        assertNull(verifiedVisit(selected, ReadState.Forbidden))
        assertNull(verifiedVisit(null, inProgress))
    }

    @Test fun executionAndCloseoutNeverOpenForAnUnstartedOrUnlinkedVisit() {
        val current = verifiedVisit(selected, inProgress)!!
        assertTrue(canOpenVisitStage(VisitStage.EXECUTION, current))
        assertTrue(canOpenVisitStage(VisitStage.CLOSEOUT, current))
        assertTrue(canOpenVisitStage(VisitStage.DOCUMENTATION, current))
        assertFalse(canOpenVisitStage(VisitStage.EXECUTION, current.copy(plannedSessionId = null)))
        assertFalse(canOpenVisitStage(VisitStage.CLOSEOUT, current.copy(status = AppointmentStatus.SCHEDULED)))
        assertFalse(canOpenVisitStage(VisitStage.EXECUTION, current.copy(status = AppointmentStatus.COMPLETED)))
        assertFalse(canOpenVisitStage(VisitStage.DOCUMENTATION, current.copy(status = AppointmentStatus.CANCELLED)))
    }

    @Test fun documentationPreservesCorrectParticipantFromEitherEntryAndRejectsStaleRecord() {
        val record = ReadState.Ready(WorkspaceView(participant = WorkspaceParticipant(participantId = participant)))
        assertEquals(participant, verifiedDocumentationParticipant(selected, inProgress, ReadState.Idle))
        assertEquals(participant, verifiedDocumentationParticipant(
            ContextSelection.Participant(participant), ReadState.Idle, record
        ))
        assertNull(verifiedDocumentationParticipant(
            ContextSelection.Participant(UUID.randomUUID()), ReadState.Idle, record
        ))
        assertNull(verifiedDocumentationParticipant(
            ContextSelection.Participant(participant), inProgress, ReadState.Idle
        ))
        assertNull(verifiedDocumentationParticipant(null, inProgress, record))
    }
}
