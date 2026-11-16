package com.motionecosystem.moves.mobile.app

import com.motionecosystem.moves.mobile.api.CreateNoteCommand
import com.motionecosystem.moves.mobile.api.NoteView
import com.motionecosystem.moves.mobile.api.UpdateNoteCommand
import java.util.UUID

/** S07 note editor: medical content lives exclusively in retained ViewModel RAM. */
internal data class NoteValues(
    val category: String = "GENERAL",
    val title: String = "",
    val content: String = "",
    val appointmentId: UUID? = null
)

internal data class NoteDraft(
    val participantId: UUID,
    val noteId: UUID? = null,
    val values: NoteValues = NoteValues(),
    val original: NoteValues = values,
    val version: Long? = null,
    val requestKey: String = UUID.randomUUID().toString()
) {
    val dirty: Boolean get() = values != original
    val hasUnsavedContent: Boolean get() = dirty ||
        (noteId == null && (values.title.isNotBlank() || values.content.isNotBlank()))
}

internal enum class NoteOperation { CREATE, UPDATE, FINALISE, ARCHIVE }

internal sealed interface NoteWriteState {
    data object Idle : NoteWriteState
    data class Saving(val operation: NoteOperation) : NoteWriteState
    data class Conflict(val operation: NoteOperation) : NoteWriteState
    data class Uncertain(val operation: NoteOperation) : NoteWriteState
    data class Failed(val message: String) : NoteWriteState
    data class Success(val message: String) : NoteWriteState
}

internal val NOTE_CATEGORIES: Map<String, String> = linkedMapOf(
    "GENERAL" to "Notatka ogólna",
    "SESSION" to "Notatka ze spotkania",
    "HEALTH" to "Notatka zdrowotna",
    "OTHER" to "Inna notatka"
)

internal fun noteFromServer(participantId: UUID, view: NoteView): NoteDraft? {
    if (view.id == null || view.participantId != participantId) return null
    val values = NoteValues(
        category = view.category.orEmpty(),
        title = view.title.orEmpty(),
        content = view.content.orEmpty(),
        appointmentId = view.appointmentId
    )
    return NoteDraft(participantId, view.id, values, values, view.version)
}

/** Three-way merge: only locally edited fields override the newer server values.
 * The user explicitly approves this operation; it never triggers an automatic PUT.
 */
internal fun rebaseNoteDraft(local: NoteDraft, fresh: NoteView): NoteDraft? {
    val current = noteFromServer(local.participantId, fresh) ?: return null
    if (local.noteId != fresh.id || fresh.status != "DRAFT" || current.version == null) return null
    val merged = NoteValues(
        category = if (local.values.category != local.original.category)
            local.values.category else current.values.category,
        title = if (local.values.title != local.original.title)
            local.values.title else current.values.title,
        content = if (local.values.content != local.original.content)
            local.values.content else current.values.content,
        appointmentId = if (local.values.appointmentId != local.original.appointmentId)
            local.values.appointmentId else current.values.appointmentId
    )
    return current.copy(values = merged, requestKey = local.requestKey)
}

internal fun validNoteValues(values: NoteValues): Boolean =
    values.category in NOTE_CATEGORIES &&
        values.title.isNotBlank() && values.title.length <= 160 &&
        values.content.isNotBlank() && values.content.length <= 8000

internal fun createNotePayload(draft: NoteDraft): CreateNoteCommand? {
    if (draft.noteId != null || !validNoteValues(draft.values)) return null
    return CreateNoteCommand(
        draft.values.category, draft.values.title.trim(), draft.values.content.trim(),
        draft.values.appointmentId
    )
}

internal fun updateNotePayload(draft: NoteDraft, record: NoteView): UpdateNoteCommand? {
    if (draft.noteId == null || draft.noteId != record.id ||
        draft.participantId != record.participantId ||
        record.status != "DRAFT" || "UPDATE" !in record.availableActions ||
        draft.version == null || draft.version != record.version ||
        !validNoteValues(draft.values)) return null
    return UpdateNoteCommand(
        draft.version, draft.values.category,
        draft.values.title.trim(), draft.values.content.trim(), draft.values.appointmentId
    )
}
