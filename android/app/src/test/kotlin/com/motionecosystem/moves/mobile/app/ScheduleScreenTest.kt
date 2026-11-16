package com.motionecosystem.moves.mobile.app

import com.motionecosystem.moves.mobile.api.*
import java.time.OffsetDateTime
import java.util.UUID
import org.junit.Assert.*
import org.junit.Test

class ScheduleScreenTest {
    @Test fun newAppointmentCannotInheritAnotherParticipantsCachedFields() {
        val oldParticipant = UUID.randomUUID()
        val nextParticipant = UUID.randomUUID()
        val oldAppointment = AppointmentView(
            appointmentId = UUID.randomUUID(),
            participantId = oldParticipant,
            type = AppointmentType.PHYSIOTHERAPY,
            locationMode = AppointmentLocationMode.REMOTE,
            location = "Prywatny adres poprzedniego uczestnika",
            shortPurpose = "Poufny opis poprzedniej wizyty",
            version = 4L
        )
        val cached = ReadState.Ready(oldAppointment)
        // The new form must use its neutral defaults even if an old appointment remains in RAM.
        assertNull(scheduleDetailsFor(ScheduleTarget.New(nextParticipant), cached))
        assertNull(scheduleDetailsFor(ScheduleTarget.New(), cached))
        // An edit may use cached fields only for its own matching appointment AND participant.
        assertNull(scheduleDetailsFor(
            ScheduleTarget.Edit(oldAppointment.appointmentId!!, nextParticipant), cached
        ))
        assertNull(scheduleDetailsFor(
            ScheduleTarget.Edit(UUID.randomUUID(), oldParticipant), cached
        ))
        assertEquals(oldAppointment, scheduleDetailsFor(
            ScheduleTarget.Edit(oldAppointment.appointmentId!!, oldParticipant), cached
        ))
    }

    @Test fun originalOccupiedTimeIsAllowedForAuthorizedEditButNotForCreation() {
        val participant = UUID.randomUUID()
        val appointmentId = UUID.randomUUID()
        val start = OffsetDateTime.parse("2026-11-20T10:00:00Z")
        val detail = AppointmentView(
            appointmentId = appointmentId, participantId = participant,
            startsAt = start, endsAt = start.plusMinutes(45)
        )
        val current = AppointmentSaveCommand(
            participantId = participant, startsAt = start, endsAt = start.plusMinutes(45),
            type = AppointmentType.CONSULTATION, locationMode = AppointmentLocationMode.REMOTE
        )
        val unavailableDay = TodayView(bookableSlots = emptyList())
        val edit = ScheduleTarget.Edit(appointmentId, participant)
        assertTrue(validScheduleWindow(edit, current, detail, unavailableDay))
        assertTrue(validScheduleWindow(edit, current, detail, null))
        assertFalse(validScheduleWindow(ScheduleTarget.New(participant), current, detail, unavailableDay))
        assertFalse(validScheduleWindow(edit.copy(participantId = UUID.randomUUID()), current, detail, unavailableDay))
        assertFalse(validScheduleWindow(edit, current.copy(startsAt = start.plusMinutes(15)), detail, unavailableDay))
        assertFalse(validScheduleWindow(edit, current, detail.copy(appointmentId = UUID.randomUUID()), unavailableDay))
        assertFalse(validScheduleWindow(edit, current.copy(endsAt = start), detail, unavailableDay))
        val free = current.copy(startsAt = start.plusHours(2), endsAt = start.plusHours(3))
        assertTrue(validScheduleWindow(edit, free, detail,
            TodayView(bookableSlots = listOf(BookableSlotView(free.startsAt, free.endsAt)))))
    }

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
