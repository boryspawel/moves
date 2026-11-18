package com.motionecosystem.moves.mobile.app

import com.motionecosystem.moves.mobile.api.AppointmentStatus
import com.motionecosystem.moves.mobile.api.AppointmentView
import com.motionecosystem.moves.mobile.api.CloseoutContextView
import java.util.UUID
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CloseoutScreenTest {
    @Test fun completionRequiresBothAuthorisedSnapshotsAndServerApproval() {
        val appointmentId = UUID.randomUUID()
        val participantId = UUID.randomUUID()
        val detail = AppointmentView(
            appointmentId = appointmentId, participantId = participantId,
            status = AppointmentStatus.IN_PROGRESS, version = 4, availableActions = listOf("COMPLETE")
        )
        val context = CloseoutContextView(
            appointmentId = appointmentId, participantId = participantId,
            status = AppointmentStatus.IN_PROGRESS, version = 4,
            executionRequired = true, executionRecorded = true, canComplete = true
        )
        assertTrue(canCompleteVisit(detail, context, CloseoutSaveState.Idle))
        assertFalse(canCompleteVisit(detail, context.copy(executionRecorded = false), CloseoutSaveState.Idle))
        assertFalse(canCompleteVisit(detail, context.copy(canComplete = false), CloseoutSaveState.Idle))
        assertFalse(canCompleteVisit(detail.copy(availableActions = emptyList()), context, CloseoutSaveState.Idle))
        assertFalse(canCompleteVisit(detail.copy(version = 5), context, CloseoutSaveState.Idle))
        assertFalse(canCompleteVisit(detail, context.copy(participantId = UUID.randomUUID()), CloseoutSaveState.Idle))
        assertFalse(canCompleteVisit(detail, context.copy(status = AppointmentStatus.COMPLETED), CloseoutSaveState.Idle))
        assertFalse(canCompleteVisit(detail, context, CloseoutSaveState.Conflict))
        assertFalse(canCompleteVisit(detail, context, CloseoutSaveState.Saving))
        assertTrue(canCompleteVisit(detail, context.copy(executionRequired = false, executionRecorded = false),
            CloseoutSaveState.Uncertain))
    }
}
