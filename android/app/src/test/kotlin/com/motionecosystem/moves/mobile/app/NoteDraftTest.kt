package com.motionecosystem.moves.mobile.app

import com.motionecosystem.moves.mobile.api.NoteView
import java.util.UUID
import org.junit.Assert.*
import org.junit.Test

class NoteDraftTest {
    @Test fun rebasePreservesOnlyTouchedFieldsAndNeverMixesPatients() {
        val participant = UUID.randomUUID()
        val id = UUID.randomUUID()
        val old = NoteView(
            id = id, participantId = participant, category = "GENERAL",
            title = "Stary tytuł", content = "Stary opis", status = "DRAFT",
            version = 1L, availableActions = listOf("UPDATE")
        )
        val draft = noteFromServer(participant, old)!!
            .let { it.copy(values = it.values.copy(title = "Mój tytuł")) }
        val latest = old.copy(version = 2L, content = "Nowy opis")
        val rebased = rebaseNoteDraft(draft, latest)!!
        assertEquals("Mój tytuł", rebased.values.title)
        assertEquals("Nowy opis", rebased.values.content)
        assertEquals(2L, rebased.version)
        assertEquals(2L, updateNotePayload(rebased, latest)?.expectedVersion)
        assertNull(rebaseNoteDraft(draft, latest.copy(participantId = UUID.randomUUID())))
        assertNull(rebaseNoteDraft(draft, latest.copy(status = "FINAL")))
        assertNull(updateNotePayload(draft, latest))
        assertFalse(validNoteValues(rebased.values.copy(title = "")))
    }
}
