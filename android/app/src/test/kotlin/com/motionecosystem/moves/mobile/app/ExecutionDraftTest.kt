package com.motionecosystem.moves.mobile.app

import com.motionecosystem.moves.mobile.api.SpecialistExecutionContext
import com.motionecosystem.moves.mobile.api.SpecialistExecutionPrescription
import java.util.UUID
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.*
import org.junit.Test

class ExecutionDraftTest {
    @Test fun plannedChangedAndSkippedFactsRespectServerPrescriptionAndResponseRanges() {
        val appointment = UUID.randomUUID()
        val participant = UUID.randomUUID()
        val session = UUID.randomUUID()
        val strength = UUID.randomUUID()
        val duration = UUID.randomUUID()
        val context = SpecialistExecutionContext(
            appointmentId = appointment, participantId = participant,
            appointmentStatus = "IN_PROGRESS", plannedSessionId = session,
            recordingAllowed = true,
            prescriptions = listOf(
                SpecialistExecutionPrescription(
                    id = strength, canonicalDoseType = "STRENGTH",
                    sets = 3, repetitions = 8, externalLoadValue = JsonPrimitive(15),
                    externalLoadUnit = "kg"
                ),
                SpecialistExecutionPrescription(
                    id = duration, canonicalDoseType = "ENDURANCE",
                    durationSeconds = 90
                )
            )
        )
        val empty = initialExecutionDraft(context)!!
        assertNull(buildExecutionCommand(context, empty))
        val planned = empty.copy(exercises = mapOf(
            strength to ExerciseActualDraft(choice = ExecutionChoice.AS_PLANNED),
            duration to ExerciseActualDraft(choice = ExecutionChoice.SKIPPED)
        ), pain = "0", difficulty = "5")
        val first = buildExecutionCommand(context, planned)!!
        assertEquals(2, first.results.size)
        assertEquals(3, first.results[0].actualSets)
        assertEquals(8, first.results[0].actualRepetitions)
        assertEquals(false, first.results[0].modified)
        assertEquals(true, first.results[1].skipped)
        assertNull(first.results[1].actualDurationSeconds)
        val edited = planned.copy(exercises = planned.exercises + (
            strength to ExerciseActualDraft(ExecutionChoice.CHANGED,
                sets = "2", repetitions = "6", externalLoadValue = "12,5")
        ))
        val second = buildExecutionCommand(context, edited)!!
        assertEquals(2, second.results.first().actualSets)
        assertEquals("12.5", second.results.first().actualExternalLoadValue?.content)
        assertEquals(true, second.results.first().modified)
        assertNull(buildExecutionCommand(context, edited.copy(pain = "11")))
        assertNull(buildExecutionCommand(context, edited.copy(difficulty = "0")))
        assertNull(buildExecutionCommand(context, edited.copy(
            exercises = edited.exercises + (strength to ExerciseActualDraft(
                ExecutionChoice.CHANGED, sets = "-1", repetitions = "6", externalLoadValue = "12"
            ))
        )))
        assertNull(buildExecutionCommand(context.copy(participantId = UUID.randomUUID()), edited))
        assertNull(buildExecutionCommand(context.copy(recordingAllowed = false), edited))
    }
}
