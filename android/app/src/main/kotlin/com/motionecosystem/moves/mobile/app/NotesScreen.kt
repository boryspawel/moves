package com.motionecosystem.moves.mobile.app

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.motionecosystem.moves.mobile.api.NoteView
import java.util.UUID

private fun noteStatus(status: String?): String = when (status) {
    "DRAFT" -> "Szkic"
    "FINAL" -> "Finalna"
    "ARCHIVED" -> "Zarchiwizowana"
    else -> "Stan nieznany"
}

/** S07 notes share a RAM-only, access-scoped ViewModel and the same optional speech input seam. */
@Composable
internal fun NotesSection(
    notes: ReadState<List<NoteView>>,
    selectedNoteId: UUID?,
    draft: NoteDraft?,
    writeState: NoteWriteState,
    onNew: () -> Unit,
    onOpen: (UUID) -> Unit,
    onClose: () -> Unit,
    onChange: (NoteValues) -> Unit,
    onCreate: () -> Unit,
    onUpdate: () -> Unit,
    onFinalise: () -> Unit,
    onArchive: () -> Unit,
    onReconcile: () -> Unit,
    onRefresh: () -> Unit
) {
    val saving = writeState is NoteWriteState.Saving
    var discardDialog by remember { mutableStateOf(false) }
    var finishDialog by remember { mutableStateOf(false) }
    var archiveDialog by remember { mutableStateOf(false) }
    var mergeDialog by remember { mutableStateOf(false) }

    if (discardDialog) {
        AlertDialog(
            onDismissRequest = { discardDialog = false },
            title = { Text("Porzucić zmiany notatki?") },
            text = { Text("Niezapisane dane zostaną usunięte z pamięci telefonu.") },
            confirmButton = {
                TextButton(onClick = { discardDialog = false; onClose() }) { Text("Porzuć") }
            },
            dismissButton = {
                TextButton(onClick = { discardDialog = false }) { Text("Anuluj") }
            }
        )
    }
    if (finishDialog || archiveDialog) {
        val finalising = finishDialog
        AlertDialog(
            onDismissRequest = { finishDialog = false; archiveDialog = false },
            title = { Text(if (finalising) "Sfinalizować notatkę?" else "Zarchiwizować notatkę?") },
            text = { Text(
                if (finalising) "Po finalizacji notatka będzie nieedytowalna."
                else "Notatka pozostanie w historii, ale nie będzie już aktywna."
            ) },
            confirmButton = {
                TextButton(onClick = {
                    finishDialog = false; archiveDialog = false
                    if (finalising) onFinalise() else onArchive()
                }) { Text(if (finalising) "Finalizuj" else "Archiwizuj") }
            },
            dismissButton = {
                TextButton(onClick = { finishDialog = false; archiveDialog = false }) {
                    Text("Anuluj")
                }
            }
        )
    }
    if (mergeDialog) {
        AlertDialog(
            onDismissRequest = { mergeDialog = false },
            title = { Text("Przyjąć aktualną wersję notatki?") },
            text = { Text("Zmienione przez Ciebie pola zostaną zachowane, pozostałe będą pochodzić z serwera. Następny zapis wykonasz ręcznie.") },
            confirmButton = {
                TextButton(onClick = { mergeDialog = false; onReconcile() }) {
                    Text("Połącz po przejrzeniu")
                }
            },
            dismissButton = { TextButton(onClick = { mergeDialog = false }) { Text("Anuluj") } }
        )
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Notatki", style = MaterialTheme.typography.titleLarge)
        when (val state = writeState) {
            is NoteWriteState.Saving -> LinearProgressIndicator(Modifier.fillMaxWidth())
            is NoteWriteState.Success -> Text(state.message, color = MaterialTheme.colorScheme.primary)
            is NoteWriteState.Failed -> Text(state.message, color = MaterialTheme.colorScheme.error)
            is NoteWriteState.Conflict -> Text(
                "Konflikt 409: odświeżona została wersja serwera. Sprawdź jej treść przed świadomym połączeniem zmian.",
                color = MaterialTheme.colorScheme.error
            )
            is NoteWriteState.Uncertain -> Text(
                if (state.operation == NoteOperation.CREATE ||
                    state.operation == NoteOperation.FINALISE || state.operation == NoteOperation.ARCHIVE)
                    "Niepewny wynik POST. Odśwież notatki i sprawdź stan; ponowienie użyje tego samego klucza."
                else "Niepewny wynik PUT. Odśwież notatki, przejrzyj najnowszą wersję i dopiero ponów zapis.",
                color = MaterialTheme.colorScheme.error
            )
            NoteWriteState.Idle -> Unit
        }
        OutlinedButton(onClick = onRefresh, enabled = !saving, modifier = Modifier.fillMaxWidth()) {
            Text("Odśwież notatki")
        }
        ReadPanel(notes, onRefresh, { false }) { records ->
            if (draft == null && selectedNoteId == null) {
                Button(onClick = onNew, enabled = !saving, modifier = Modifier.fillMaxWidth()) {
                    Text("Nowa notatka")
                }
                if (records.isEmpty()) Text("Brak notatek uczestnika.")
                records.forEach { note ->
                    ElevatedCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(note.title ?: "Bez tytułu", style = MaterialTheme.typography.titleMedium)
                            Text("${NOTE_CATEGORIES[note.category] ?: "Notatka"} · ${noteStatus(note.status)}",
                                style = MaterialTheme.typography.bodySmall)
                            Text(note.content.orEmpty().take(120),
                                style = MaterialTheme.typography.bodySmall, maxLines = 2)
                            OutlinedButton(
                                onClick = { note.id?.let(onOpen) },
                                enabled = !saving && note.id != null
                            ) { Text(if (note.status == "DRAFT") "Otwórz szkic" else "Otwórz") }
                        }
                    }
                }
            } else {
                val selected = records.firstOrNull { it.id == selectedNoteId }
                if (selectedNoteId != null && selected == null) {
                    Text("Notatka nie jest już dostępna.", color = MaterialTheme.colorScheme.error)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(
                        if (draft?.noteId == null && draft != null) "Nowa notatka"
                        else "Notatka — ${noteStatus(selected?.status)}",
                        style = MaterialTheme.typography.titleMedium
                    )
                    TextButton(
                        onClick = {
                            if (draft?.hasUnsavedContent == true) discardDialog = true else onClose()
                        },
                        enabled = !saving
                    ) { Text("Lista") }
                }
                val canEdit = draft != null &&
                    (draft.noteId == null || selected?.status == "DRAFT" &&
                        "UPDATE" in selected?.availableActions.orEmpty()) && !saving &&
                    writeState !is NoteWriteState.Conflict &&
                    writeState !is NoteWriteState.Uncertain
                val values = draft?.values ?: selected?.let {
                    NoteValues(it.category.orEmpty(), it.title.orEmpty(), it.content.orEmpty(), it.appointmentId)
                }
                if (values != null) {
                    Text("Kategoria", style = MaterialTheme.typography.labelLarge)
                    NOTE_CATEGORIES.forEach { (key, label) ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            RadioButton(
                                selected = values.category == key,
                                onClick = { if (canEdit) onChange(values.copy(category = key)) },
                                enabled = canEdit
                            )
                            Text(label)
                        }
                    }
                    OutlinedTextField(
                        value = values.title,
                        onValueChange = { onChange(values.copy(title = it.take(160))) },
                        label = { Text("Tytuł") },
                        readOnly = !canEdit,
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    DictationReadyTextField(
                        fieldId = "note-content", title = "Treść",
                        text = values.content,
                        canEdit = canEdit,
                        keyboardType = KeyboardType.Text,
                        maxLength = 8000,
                        onTextChange = { onChange(values.copy(content = it)) }
                    )
                    if (values.appointmentId != null) Text("Notatka przypisana do spotkania.",
                        style = MaterialTheme.typography.bodySmall)
                }
                if (draft?.hasUnsavedContent == true) {
                    Text("Niezapisane dane wyłącznie w pamięci aplikacji.",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall)
                }
                if (writeState is NoteWriteState.Conflict ||
                    writeState is NoteWriteState.Uncertain) {
                    if (draft?.noteId != null && selected?.status == "DRAFT" &&
                        writeState != NoteWriteState.Uncertain(NoteOperation.CREATE)) {
                        Text("Aktualna wersja serwera do porównania:",
                            style = MaterialTheme.typography.titleSmall)
                        Text("Kategoria: ${NOTE_CATEGORIES[selected.category] ?: "nieznana"}")
                        Text("Tytuł: ${selected.title.orEmpty()}")
                        Text("Treść:", style = MaterialTheme.typography.labelLarge)
                        Text(selected.content.orEmpty())
                        HorizontalDivider()
                        Text("Twoje niezapisane wartości:", style = MaterialTheme.typography.titleSmall)
                        Text("Tytuł: ${draft.values.title}")
                        Text(draft.values.content)
                        Text("Sprawdź różnice przed potwierdzeniem; nie wykonamy automatycznego zapisu.",
                            style = MaterialTheme.typography.bodySmall)
                        OutlinedButton(onClick = { mergeDialog = true },
                            modifier = Modifier.fillMaxWidth()) {
                            Text("Przejrzałem wersję serwera — połącz zmiany")
                        }
                    }
                }
                if (draft != null && canEdit) {
                    Button(
                        onClick = if (draft.noteId == null) onCreate else onUpdate,
                        enabled = validNoteValues(draft.values) &&
                            (draft.noteId == null || draft.dirty),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (draft.noteId == null) "Zapisz nową notatkę" else "Zapisz zmiany")
                    }
                }
                val hasPendingUpdate = draft?.dirty == true ||
                    writeState == NoteWriteState.Uncertain(NoteOperation.UPDATE)
                if (selected != null && !saving && !hasPendingUpdate) {
                    if ("FINALISE" in selected.availableActions) {
                        OutlinedButton(
                            onClick = { finishDialog = true },
                            enabled = writeState !is NoteWriteState.Conflict,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(if (writeState == NoteWriteState.Uncertain(NoteOperation.FINALISE))
                                "Ponów finalizację tym samym kluczem" else "Finalizuj notatkę")
                        }
                    }
                    if ("ARCHIVE" in selected.availableActions) {
                        OutlinedButton(
                            onClick = { archiveDialog = true },
                            enabled = writeState !is NoteWriteState.Conflict &&
                                writeState != NoteWriteState.Uncertain(NoteOperation.FINALISE),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(if (writeState == NoteWriteState.Uncertain(NoteOperation.ARCHIVE))
                                "Ponów archiwizację tym samym kluczem" else "Archiwizuj notatkę")
                        }
                    }
                }
            }
        }
    }
}
