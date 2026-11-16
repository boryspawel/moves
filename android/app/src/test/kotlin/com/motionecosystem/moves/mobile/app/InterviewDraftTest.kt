package com.motionecosystem.moves.mobile.app

import com.motionecosystem.moves.mobile.api.InterviewAnswer
import com.motionecosystem.moves.mobile.api.InterviewQuestion
import com.motionecosystem.moves.mobile.api.InterviewView
import java.util.UUID
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.*
import org.junit.Test

class InterviewDraftTest {
    @Test fun draftMapsOnlyEditableServerFieldsAndNeverMixesParticipantsOrCompletedForms() {
        val participant = UUID.randomUUID()
        val questionnaire = InterviewView(
            id = UUID.randomUUID(), participantId = participant, status = "DRAFT",
            questions = listOf(
                InterviewQuestion("reason", "First", "Why?", "TEXT"),
                InterviewQuestion("pain", "Second", "Pain?", "NUMBER"),
                InterviewQuestion("date", "Third", "When?", "DATE"),
                InterviewQuestion("choices", "Fourth", "Which?", "MULTI_SELECT")
            ),
            answers = listOf(
                InterviewAnswer("reason", textValue = "Opis"),
                InterviewAnswer("pain", numberValue = JsonPrimitive(2.5)),
                InterviewAnswer("date", dateValue = "2026-11-16"),
                InterviewAnswer("choices", selections = listOf("ONE"))
            )
        )
        val draft = initialInterviewDraft(participant, questionnaire)!!
        assertEquals(mapOf("reason" to "Opis", "pain" to "2.5",
            "date" to "2026-11-16"), draft.values)
        assertFalse(draft.dirty)
        assertNull(initialInterviewDraft(UUID.randomUUID(), questionnaire))
        assertNull(initialInterviewDraft(participant, questionnaire.copy(status = "COMPLETED")))
        assertFalse(editableInterviewAnswer(questionnaire.questions.last()))
        assertEquals(listOf("ONE"), draft.selections["choices"])
    }

    @Test fun savePreservesSelectAnswersAndReconcilesUntouchedServerFieldsAfter409() {
        val participant = UUID.randomUUID()
        val id = UUID.randomUUID()
        val questions = listOf(
            InterviewQuestion("reason", "First", "Why?", "TEXT"),
            InterviewQuestion("goals", "First", "Goals?", "TEXT"),
            InterviewQuestion("activity_level", "Activity", "Level?", "SINGLE_SELECT")
        )
        val view = InterviewView(
            id = id, participantId = participant, status = "DRAFT", version = 1,
            availableActions = listOf("SAVE_DRAFT", "COMPLETE"), questions = questions,
            answers = listOf(
                InterviewAnswer("reason", textValue = "Stary opis"),
                InterviewAnswer("goals", textValue = "Stary cel"),
                InterviewAnswer("activity_level", selections = listOf("LOW"))
            )
        )
        val original = initialInterviewDraft(participant, view)!!
        val local = original.copy(
            values = original.values + ("reason" to "Mój opis"),
            selections = original.selections + ("activity_level" to listOf("HIGH")),
            dirty = true
        )
        val latest = view.copy(version = 2, answers = listOf(
            InterviewAnswer("reason", textValue = "Serwerowy opis"),
            InterviewAnswer("goals", textValue = "Nowy cel"),
            InterviewAnswer("activity_level", selections = listOf("MODERATE"))
        ))
        assertNull(interviewSaveCommand(latest, local)) // No implicit version bump.
        val rebased = reconcileInterviewDraft(local, latest)!!
        assertEquals(2L, rebased.baseVersion)
        assertEquals("Mój opis", rebased.values["reason"])
        assertEquals("Nowy cel", rebased.values["goals"])
        assertEquals(listOf("HIGH"), rebased.selections["activity_level"])
        val command = interviewSaveCommand(latest, rebased)!!
        assertEquals(2L, command.expectedVersion)
        assertEquals("Mój opis", command.answers.first { it.code == "reason" }.textValue)
        assertEquals("Nowy cel", command.answers.first { it.code == "goals" }.textValue)
        assertEquals(listOf("HIGH"), command.answers.first { it.code == "activity_level" }.selections)
    }

    @Test fun malformedDateOrNumberCannotBeSubmittedAsAnAnswer() {
        val participant = UUID.randomUUID()
        val view = InterviewView(
            id = UUID.randomUUID(), participantId = participant, status = "DRAFT",
            version = 3, availableActions = listOf("SAVE_DRAFT"),
            questions = listOf(InterviewQuestion("date", "Health", "When?", "DATE"))
        )
        val old = initialInterviewDraft(participant, view)!!
        val invalid = old.copy(values = mapOf("date" to "32/13/2026"), dirty = true)
        assertNull(interviewSaveCommand(view, invalid))
    }
}
