package com.motionecosystem.moves.mobile.app

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.motionecosystem.moves.mobile.api.SpecialistExecutionContext
import com.motionecosystem.moves.mobile.api.SpecialistExecutionPrescription

@Composable
private fun ActualDoseInput(
    label: String,
    value: String,
    decimal: Boolean = false,
    onChange: (String) -> Unit
) {
    OutlinedTextField(
        value = value,
        onValueChange = { onChange(it.take(24)) },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Number
        ),
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun ExerciseExecutionCard(
    prescription: SpecialistExecutionPrescription,
    index: Int,
    actual: ExerciseActualDraft,
    enabled: Boolean,
    onChanged: (ExerciseActualDraft) -> Unit
) {
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(prescription.exerciseName?.takeIf { it.isNotBlank() } ?: "Ćwiczenie ${index + 1}",
                style = MaterialTheme.typography.titleMedium)
            Text("Planowana dawka: ${plannedExecutionDose(prescription)}",
                style = MaterialTheme.typography.bodyMedium)
            prescription.side?.takeIf { it.isNotBlank() }?.let { Text("Strona: $it") }
            prescription.tempo?.takeIf { it.isNotBlank() }?.let { Text("Tempo: $it") }
            prescription.restSeconds?.let { Text("Przerwa: $it s") }
            prescription.notes?.takeIf { it.isNotBlank() }?.let {
                Text("Wskazówki: $it", style = MaterialTheme.typography.bodySmall)
            }
            Text("Rzeczywiste wykonanie", style = MaterialTheme.typography.labelLarge)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                val labels = listOf(
                    ExecutionChoice.AS_PLANNED to "Zgodnie",
                    ExecutionChoice.CHANGED to "Zmieniono",
                    ExecutionChoice.SKIPPED to "Pominięto"
                )
                labels.forEach { (choice, label) ->
                    FilterChip(
                        selected = actual.choice == choice,
                        enabled = enabled,
                        onClick = { onChanged(actual.copy(choice = choice)) },
                        label = { Text(label) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            if (actual.choice == ExecutionChoice.SKIPPED) {
                Text("Dawka wyniesie zero. Ćwiczenie będzie oznaczone jako pominięte.",
                    style = MaterialTheme.typography.bodySmall)
            }
            if (actual.choice == ExecutionChoice.CHANGED) {
                Text("Wprowadź rzeczywiste wartości, nie zmieniaj planowanej dawki.",
                    style = MaterialTheme.typography.bodySmall)
                if (showsExecutionSets(prescription))
                    ActualDoseInput("Serie", actual.sets, onChange = {
                        onChanged(actual.copy(sets = it))
                    })
                if (showsExecutionRepetitions(prescription))
                    ActualDoseInput("Powtórzenia", actual.repetitions, onChange = {
                        onChanged(actual.copy(repetitions = it))
                    })
                if (showsExecutionDuration(prescription))
                    ActualDoseInput("Czas (s)", actual.durationSeconds, onChange = {
                        onChanged(actual.copy(durationSeconds = it))
                    })
                if (showsExecutionDistance(prescription))
                    ActualDoseInput("Dystans (m)", actual.distanceMeters, decimal = true, onChange = {
                        onChanged(actual.copy(distanceMeters = it))
                    })
                if (showsExecutionContacts(prescription))
                    ActualDoseInput("Kontakty", actual.contacts, onChange = {
                        onChanged(actual.copy(contacts = it))
                    })
                if (showsExecutionLoad(prescription))
                    ActualDoseInput(
                        "Obciążenie ${prescription.externalLoadUnit.orEmpty()}",
                        actual.externalLoadValue, decimal = true,
                        onChange = { onChanged(actual.copy(externalLoadValue = it)) }
                    )
            }
        }
    }
}

/** S06 only sends declared exercise facts; this is not the participant's session attempt API. */
@Composable
internal fun ExecutionScreen(
    state: ReadState<SpecialistExecutionContext>,
    draft: ExecutionDraft?,
    save: ExecutionSaveState,
    onChange: (ExecutionDraft) -> Unit,
    onSave: () -> Unit,
    onAcceptConflict: () -> Unit,
    onRefresh: () -> Unit,
    onBack: () -> Unit
) {
    val saving = save == ExecutionSaveState.Saving
    val dirty = draft?.dirty == true
    var exitConfirmation by remember { mutableStateOf(false) }
    var recordConfirmation by remember { mutableStateOf(false) }
    var conflictConfirmation by remember { mutableStateOf(false) }

    fun back() {
        if (saving) return
        if (dirty && save != ExecutionSaveState.Saved) exitConfirmation = true
        else onBack()
    }
    BackHandler(enabled = saving || dirty) { back() }
    if (exitConfirmation) {
        AlertDialog(
            onDismissRequest = { exitConfirmation = false },
            title = { Text("Niezapisana realizacja") },
            text = { Text("Wpisane fakty pozostaną tylko w pamięci aplikacji. Powrót nie kończy wizyty ani nie zapisuje danych.") },
            confirmButton = {
                TextButton(onClick = { exitConfirmation = false; onBack() }) {
                    Text("Wróć do spotkania")
                }
            },
            dismissButton = {
                TextButton(onClick = { exitConfirmation = false }) { Text("Pozostań") }
            }
        )
    }
    if (recordConfirmation) {
        AlertDialog(
            onDismissRequest = { recordConfirmation = false },
            title = { Text("Zatwierdzić realizację sesji?") },
            text = { Text(
                if (save == ExecutionSaveState.Uncertain)
                    "Wynik poprzedniego żądania jest niepewny. Ponowienie użyje dokładnie tej samej treści i klucza."
                else "Fakty wykonania zostaną zapisane na serwerze. Operacja jest jednorazowa i nie będzie automatycznie ponawiana."
            ) },
            confirmButton = {
                TextButton(onClick = { recordConfirmation = false; onSave() }) {
                    Text(if (save == ExecutionSaveState.Uncertain) "Ponów identyczne żądanie" else "Zapisz")
                }
            },
            dismissButton = {
                TextButton(onClick = { recordConfirmation = false }) { Text("Anuluj") }
            }
        )
    }
    if (conflictConfirmation) {
        AlertDialog(
            onDismissRequest = { conflictConfirmation = false },
            title = { Text("Przyjąć aktualny plan z serwera?") },
            text = { Text("Jeśli powiązany plan lub sesja zmieniły się, lokalny szkic zostanie wyczyszczony. Jeśli są niezmienione, wpisane dane pozostaną. Zapis trzeba zatwierdzić ponownie.") },
            confirmButton = {
                TextButton(onClick = { conflictConfirmation = false; onAcceptConflict() }) {
                    Text("Potwierdzam")
                }
            },
            dismissButton = {
                TextButton(onClick = { conflictConfirmation = false }) { Text("Anuluj") }
            }
        )
    }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(onClick = ::back, enabled = !saving) { Text("Wróć") }
            TextButton(onClick = onRefresh, enabled = !saving) { Text("Odśwież") }
        }
        ReadPanel(state, onRefresh, { false }) { context ->
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text("Realizacja sesji", style = MaterialTheme.typography.headlineMedium)
                Text(context.sessionTitle?.takeIf { it.isNotBlank() } ?: "Sesja treningowa",
                    style = MaterialTheme.typography.titleLarge)
                context.participantName?.takeIf { it.isNotBlank() }?.let { Text(it) }
                if (context.recordedExecution != null || save == ExecutionSaveState.Saved) {
                    Text("Realizacja została zapisana na serwerze.",
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.titleMedium)
                    context.recordedExecution?.outcome?.let { Text("Wynik: $it") }
                    TextButton(onClick = onBack) { Text("Wróć do spotkania") }
                } else if (!context.recordingAllowed || context.appointmentStatus != "IN_PROGRESS") {
                    Text("Zapis jest niedostępny w obecnym stanie spotkania lub sesji. Sprawdź aktualny stan w spotkaniu.",
                        color = MaterialTheme.colorScheme.error)
                } else if (draft == null) {
                    Text("Nie udało się przygotować formularza planowanej sesji. Odśwież kontekst.",
                        color = MaterialTheme.colorScheme.error)
                } else {
                    val matches = matchesExecutionDraft(draft, context)
                    val editing = !saving && save !in setOf(
                        ExecutionSaveState.Uncertain, ExecutionSaveState.Conflict, ExecutionSaveState.Saved
                    ) && matches
                    when (save) {
                        ExecutionSaveState.Uncertain -> Text(
                            "Nie ma potwierdzenia zapisu. Najpierw odśwież kontekst; przy ręcznym ponowieniu wyślemy identyczne żądanie.",
                            color = MaterialTheme.colorScheme.error
                        )
                        ExecutionSaveState.Conflict -> Text(
                            "Konflikt 409: sprawdź, czy realizacja nie została już zapisana. Przyjmij nowy stan serwera przed kolejnym zapisem.",
                            color = MaterialTheme.colorScheme.error
                        )
                        is ExecutionSaveState.Failed -> Text(save.message,
                            color = MaterialTheme.colorScheme.error)
                        ExecutionSaveState.Saving -> LinearProgressIndicator(Modifier.fillMaxWidth())
                        else -> Unit
                    }
                    if (save == ExecutionSaveState.Conflict || !matches) {
                        Text(
                            if (matches) "Powiązana sesja nie zmieniła się. Przed kolejną próbą potwierdź aktualny stan."
                            else "Plan lub lista ćwiczeń zmieniła się; ponowne wczytanie może usunąć niezapisane fakty.",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Button(
                            onClick = { conflictConfirmation = true },
                            enabled = !saving,
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Przyjmij aktualny kontekst") }
                    }
                    context.prescriptions.sortedBy { it.position ?: Int.MAX_VALUE }
                        .forEachIndexed { index, p ->
                            val id = p.id ?: return@forEachIndexed
                            ExerciseExecutionCard(
                                prescription = p, index = index,
                                actual = draft.exercises[id] ?: ExerciseActualDraft(),
                                enabled = editing,
                                onChanged = { next ->
                                    onChange(draft.copy(exercises = draft.exercises + (id to next)))
                                }
                            )
                        }
                    HorizontalDivider()
                    Text("Reakcja po sesji", style = MaterialTheme.typography.titleLarge)
                    Text("Ból: 0–10; trudność: 1–10. Pozostałe skale są opcjonalne.",
                        style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(
                        value = draft.pain, onValueChange = { if (editing) onChange(draft.copy(pain = it.take(2))) },
                        label = { Text("Ból po sesji (0–10)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        readOnly = !editing, singleLine = true, modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = draft.difficulty,
                        onValueChange = { if (editing) onChange(draft.copy(difficulty = it.take(2))) },
                        label = { Text("Trudność (1–10)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        readOnly = !editing, singleLine = true, modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = draft.sessionRpe,
                        onValueChange = { if (editing) onChange(draft.copy(sessionRpe = it.take(2))) },
                        label = { Text("Odczuwany wysiłek RPE (opcjonalnie, 1–10)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        readOnly = !editing, singleLine = true, modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = draft.techniqueConfidence,
                        onValueChange = { if (editing) onChange(draft.copy(techniqueConfidence = it.take(2))) },
                        label = { Text("Pewność techniki (opcjonalnie, 1–10)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        readOnly = !editing, singleLine = true, modifier = Modifier.fillMaxWidth()
                    )
                    DictationReadyTextField(
                        fieldId = "execution-note",
                        title = "Krótka notatka po sesji (opcjonalnie)",
                        text = draft.note,
                        canEdit = editing,
                        keyboardType = KeyboardType.Text,
                        maxLength = 500,
                        onTextChange = { onChange(draft.copy(note = it)) }
                    )
                    if (dirty) {
                        Text("Fakty i odpowiedzi są tylko w pamięci telefonu, do czasu potwierdzenia przez serwer.",
                            style = MaterialTheme.typography.bodySmall)
                    }
                    val canSave = matches && context.recordingAllowed &&
                        (save == ExecutionSaveState.Uncertain ||
                            (editing && buildExecutionCommand(context, draft) != null))
                    Button(
                        onClick = { recordConfirmation = true },
                        enabled = canSave,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (save == ExecutionSaveState.Uncertain)
                            "Ponów zapis tym samym kluczem" else "Zapisz realizację sesji")
                    }
                    if (save != ExecutionSaveState.Uncertain && !canSave) {
                        Text("Wybierz wynik każdego ćwiczenia oraz uzupełnij wymagane dane reakcji. Przy zmianie dawki wpisz poprawne wartości.",
                            style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}
