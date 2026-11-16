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
            ScheduleTarget.Edit(oldAppointment.appointmentId, nextParticipant), cached
        ))
        assertNull(scheduleDetailsFor(
            ScheduleTarget.Edit(UUID.randomUUID(), oldParticipant), cached
        ))
        assertEquals(oldAppointment, scheduleDetailsFor(
            ScheduleTarget.Edit(oldAppointment.appointmentId, oldParticipant), cached
        ))
    }

    @Test fun editBaselineKeepsOriginalFieldsAndTimeAfterServerChangesVersionAndSlot() {
        val participant = UUID.randomUUID()
        val appointmentId = UUID.randomUUID()
        val start = OffsetDateTime.parse("2026-11-20T10:00:00Z")
        val target = ScheduleTarget.Edit(appointmentId, participant)
        val old = AppointmentView(
            appointmentId, participantId = participant, version = 3,
            startsAt = start, endsAt = start.plusMinutes(45),
            type = AppointmentType.TRAINING, locationMode = AppointmentLocationMode.IN_PERSON,
            location = "Sala A", shortPurpose = "Konsultacja wstępna"
        )
        val baseline = editBaseline(target, ReadState.Ready(old))!!
        val latest = old.copy(
            version = 4, startsAt = start.plusHours(2), endsAt = start.plusHours(2).plusMinutes(45),
            shortPurpose = "Cel zmieniony na innym urządzeniu", location = "Sala B"
        )
        assertEquals("Konsultacja wstępna", baseline.shortPurpose)
        assertEquals("Sala A", baseline.location)
        assertEquals(AppointmentType.TRAINING, baseline.type)
        assertTrue(sameScheduleWindow(baseline.originalSlot,
            BookableSlotView(old.startsAt, old.endsAt)))
        assertFalse(sameScheduleWindow(baseline.originalSlot,
            BookableSlotView(latest.startsAt, latest.endsAt)))
        val now = editBaseline(target, ReadState.Ready(latest))!!
        assertEquals(4L, latest.version)
        assertEquals("Cel zmieniony na innym urządzeniu", now.shortPurpose)
        // Existing in-memory draft still references the original baseline; it isn't overwritten.
        assertEquals("Konsultacja wstępna", baseline.shortPurpose)
        assertNull(editBaseline(
            ScheduleTarget.Edit(appointmentId, UUID.randomUUID()), ReadState.Ready(latest)))
        assertNull(editBaseline(ScheduleTarget.New(participant), ReadState.Ready(latest)))
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

    @Test fun appointmentCalendarDateUsesSpecialistZoneNotUtcOrPhoneDefault() {
        val start = OffsetDateTime.parse("2026-11-20T00:30:00Z")
        assertEquals(java.time.LocalDate.of(2026, 11, 19),
            appointmentDateInSpecialistZone(start, "America/Los_Angeles"))
        assertEquals(java.time.LocalDate.of(2026, 11, 20),
            appointmentDateInSpecialistZone(start, "Europe/Warsaw"))
        assertNull(appointmentDateInSpecialistZone(start, null))
        assertNull(appointmentDateInSpecialistZone(start, "Unknown/Zone"))
        assertNull(appointmentDateInSpecialistZone(null, "Europe/Warsaw"))
    }

    @Test fun slotHoursMustNotAssumeUtcWhenCalendarZoneMissing() {
        val start = OffsetDateTime.parse("2026-11-20T23:30:00Z")
        val slot = BookableSlotView(start, start.plusMinutes(45))
        assertEquals("Nieznana strefa kalendarza", slotLabel(slot, null))
        assertEquals("Nieznana strefa kalendarza", slotLabel(slot, "Not/A_Zone"))
        assertEquals("00:30–01:15", slotLabel(slot, "Europe/Warsaw"))
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
