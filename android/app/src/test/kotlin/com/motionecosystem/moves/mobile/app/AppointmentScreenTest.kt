package com.motionecosystem.moves.mobile.app

import com.motionecosystem.moves.mobile.api.AppointmentStatus
import com.motionecosystem.moves.mobile.api.AppointmentView
import java.util.UUID
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppointmentScreenTest {
    @Test fun startDependsOnAuthoritativeActionAndVersion() {
        val eligible = AppointmentView(
            appointmentId = UUID.randomUUID(), participantId = UUID.randomUUID(),
            version = 3, status = AppointmentStatus.SCHEDULED, availableActions = listOf("START")
        )
        assertTrue(mayStartAppointment(eligible))
        assertFalse(mayStartAppointment(eligible.copy(availableActions = listOf("OPEN_APPOINTMENT"))))
        assertFalse(mayStartAppointment(eligible.copy(version = null)))
        assertFalse(mayStartAppointment(eligible.copy(status = AppointmentStatus.IN_PROGRESS)))
    }
}
