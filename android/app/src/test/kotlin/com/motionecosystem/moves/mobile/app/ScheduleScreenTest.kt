package com.motionecosystem.moves.mobile.app

import com.motionecosystem.moves.mobile.api.*
import java.time.OffsetDateTime
import java.util.UUID
import org.junit.Assert.*
import org.junit.Test

class ScheduleScreenTest {
    @Test fun slotValidationAndFormattingUseServerCalendarZone() {
        val start = OffsetDateTime.parse("2026-03-29T00:30:00Z")
        val slot = BookableSlotView(start, start.plusHours(1))
        val day = TodayView(bookableSlots = listOf(slot), timeZoneId = "Europe/Warsaw")
        assertTrue(validBookableSlot(slot, day))
        assertEquals("01:30–03:30", slotLabel(slot, day.timeZoneId))
        assertFalse(validBookableSlot(BookableSlotView(start, start.plusMinutes(50)), day))
    }

    @Test fun onlyServerAuthorizedActiveClientsCanBeSelected() {
        val allowed = ClientView(participantId = UUID.randomUUID(), recordStatus = ClientRecordStatus.ACTIVE,
            availableActions = listOf("SCHEDULE_APPOINTMENT"))
        val archived = allowed.copy(participantId = UUID.randomUUID(), recordStatus = ClientRecordStatus.ARCHIVED)
        val denied = allowed.copy(participantId = UUID.randomUUID(), availableActions = listOf("OPEN_WORKSPACE"))
        assertEquals(listOf(allowed), eligibleCalendarClients(listOf(allowed, archived, denied)))
    }
}
