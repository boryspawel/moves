package com.motionecosystem.moves.mobile.app

import com.motionecosystem.moves.mobile.api.AppointmentStatus
import com.motionecosystem.moves.mobile.api.AppointmentView
import java.time.OffsetDateTime
import org.junit.Assert.assertEquals
import java.util.UUID
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppointmentScreenTest {
    @Test fun unknownSpecialistZoneDisplaysOriginalExplicitServerOffsetInsteadOfDeviceTime() {
        val value = OffsetDateTime.parse("2026-11-20T01:30:00Z")
        assertEquals("20 listopada 2026, 01:30 (UTC+00:00)", appointmentTimeLabel(value, null))
        assertEquals("20 listopada 2026, 01:30 (UTC+00:00)", appointmentTimeLabel(value, "not-a-zone"))
        assertEquals("20 listopada 2026, 02:30", appointmentTimeLabel(value, "Europe/Warsaw"))
    }

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
