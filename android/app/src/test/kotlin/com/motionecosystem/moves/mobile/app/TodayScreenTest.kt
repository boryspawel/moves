package com.motionecosystem.moves.mobile.app

import com.motionecosystem.moves.mobile.api.AppointmentView
import com.motionecosystem.moves.mobile.api.TodayView
import java.time.OffsetDateTime
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Test

class TodayScreenTest {
    @Test fun appointmentsAreSortedByInstantNotByOffsetOrResponseOrder() {
        val later = AppointmentView(
            appointmentId = UUID.randomUUID(),
            startsAt = OffsetDateTime.parse("2026-11-16T11:30:00Z")
        )
        val earlier = AppointmentView(
            appointmentId = UUID.randomUUID(),
            startsAt = OffsetDateTime.parse("2026-11-16T13:00:00+02:00")
        )
        assertEquals(listOf(earlier, later), orderedAgenda(TodayView(appointments = listOf(later, earlier))))
    }

    @Test fun missingOrInvalidCalendarZoneDoesNotShowMisleadingUtcHours() {
        val appointment = AppointmentView(
            startsAt = OffsetDateTime.parse("2026-11-20T23:30:00Z"),
            endsAt = OffsetDateTime.parse("2026-11-21T00:30:00Z")
        )
        assertEquals("Godzina niedostępna — brak strefy kalendarza",
            appointmentHours(appointment, null))
        assertEquals("Godzina niedostępna — brak strefy kalendarza",
            appointmentHours(appointment, "Invalid/CalendarZone"))
        assertEquals("00:30–01:30", appointmentHours(appointment, "Europe/Warsaw"))
    }

    @Test fun appointmentTimesUseSpecialistZoneAcrossDstTransition() {
        val appointment = AppointmentView(
            startsAt = OffsetDateTime.parse("2026-03-29T00:30:00Z"),
            endsAt = OffsetDateTime.parse("2026-03-29T01:30:00Z")
        )
        assertEquals("01:30–03:30", appointmentHours(appointment, "Europe/Warsaw"))
    }
}
