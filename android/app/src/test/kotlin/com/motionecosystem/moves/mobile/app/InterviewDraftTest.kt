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
    }
}
