package com.motionecosystem.moves.mobile.app

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.motionecosystem.moves.mobile.api.InterviewQuestion
import com.motionecosystem.moves.mobile.api.InterviewView
import java.util.UUID

private fun interviewStatus(status: String?): String = when (status) {
    "DRAFT" -> "Szkic"
    "COMPLETED" -> "Ukończony"
    "SUPERSEDED" -> "Zastąpiony"
    else -> "Stan nieznany"
}

/** Labels are localized by stable question codes. The server remains the source of questions
 * and their types: this map is presentation only, never an editable answer dictionary.
 */
private fun questionTitle(question: InterviewQuestion): String = when (question.code) {
    "presenting_concern" -> "Powód zgłoszenia"
    "goals" -> "Oczekiwania i cele"
    "medical_history" -> "Istotne informacje z wywiadu medycznego"
    "medications" -> "Przyjmowane leki"
    "pain_level" -> "Nasilenie bólu"
    "injury_date" -> "Data urazu"
    "activity_level" -> "Poziom aktywności"
    "sleep" -> "Sen"
    "consents" -> "Omówione zgody (nie zastępuje formalnego grantu)"
    else -> question.title
}

private fun sectionTitle(value: String): String = when (value) {
    "Presenting concern" -> "Powód wizyty"
    "Goals" -> "Cele"
    "Medical history" -> "Wywiad medyczny"
    "Medications" -> "Leki"
    "Pain" -> "Ból"
    "Injury history" -> "Urazy"
    "Activity" -> "Aktywność"
    "Lifestyle" -> "Styl życia"
    "Consent" -> "Informacje o zgodach"
    else -> value
}

/** An explicit speech insertion point for future on-device-only recognition.
 * Null means no speech engine; never invokes cloud recognition or asks for microphone access.
 * Gboard's own microphone is independent of this interface and must be assessed separately.
 */
@Composable
internal fun DictationReadyTextField(
    fieldId: String,
    title: String,
    text: String,
    canEdit: Boolean,
    keyboardType: KeyboardType,
    onTextChange: (String) -> Unit,
    onRequestOnDeviceDictation: ((String) -> Unit)? = null
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        OutlinedTextField(
            value = text,
            onValueChange = { onTextChange(it.take(4000)) },
            label = { Text(title) },
            readOnly = !canEdit,
            minLines = if (keyboardType == KeyboardType.Text) 3 else 1,
            maxLines = if (keyboardType == KeyboardType.Text) 6 else 1,
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Sentences,
                keyboardType = keyboardType
            ),
            modifier = Modifier.fillMaxWidth()
        )
        if (keyboardType == KeyboardType.Text && canEdit) {
            OutlinedButton(
                onClick = { onRequestOnDeviceDictation?.invoke(fieldId) },
                enabled = onRequestOnDeviceDictation != null
            ) { Text("Dyktuj lokalnie") }
            if (onRequestOnDeviceDictation == null) {
                Text(
                    "Dyktowanie lokalne jeszcze niedostępne. Mikrofon klawiatury może korzystać z usług zewnętrznych.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
internal fun DocumentationScreen(
    interviews: ReadState<List<InterviewView>>,
    selectedInterviewId: UUID?,
    draft: InterviewDraft?,
    onOpenInterview: (UUID) -> Unit,
    onCloseInterview: () -> Unit,
    onUpdateText: (String, String) -> Unit,
    onRefresh: () -> Unit,
    onBack: () -> Unit,
    onRequestOnDeviceDictation: ((String) -> Unit)? = null
) {
    var showNotes by remember { mutableStateOf(false) }
    var exitConfirmation by remember { mutableStateOf(false) }
    var discardConfirmation by remember { mutableStateOf(false) }
    val dirty = draft?.dirty == true

    fun back() {
        if (dirty) exitConfirmation = true else onBack()
    }
    BackHandler(enabled = dirty) { back() }

    if (exitConfirmation) {
        AlertDialog(
            onDismissRequest = { exitConfirmation = false },
            title = { Text("Niezapisane odpowiedzi") },
            text = { Text("Zmiany nie zostały wysłane na serwer. Pozostaną tylko w pamięci aplikacji do wylogowania lub jej zamknięcia.") },
            confirmButton = {
                TextButton(onClick = { exitConfirmation = false; onBack() }) { Text("Wróć do poprzedniego ekranu") }
            },
            dismissButton = {
                TextButton(onClick = { exitConfirmation = false }) { Text("Pozostań") }
            }
        )
    }
    if (discardConfirmation) {
        AlertDialog(
            onDismissRequest = { discardConfirmation = false },
            title = { Text("Porzucić lokalne zmiany?") },
            text = { Text("Wpisane odpowiedzi nie zostały zapisane na serwerze.") },
            confirmButton = {
                TextButton(onClick = {
                    discardConfirmation = false
                    onCloseInterview()
                }) { Text("Porzuć zmiany") }
            },
            dismissButton = { TextButton(onClick = { discardConfirmation = false }) { Text("Anuluj") } }
        )
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            TextButton(onClick = ::back) { Text("Wróć") }
            TextButton(onClick = onRefresh) { Text("Odśwież") }
        }
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Dokumentacja", style = MaterialTheme.typography.headlineMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FilterChip(
                    selected = !showNotes,
                    onClick = { if (!dirty) showNotes = false },
                    label = { Text("Wywiady") }
                )
                FilterChip(
                    selected = showNotes,
                    onClick = { if (!dirty) showNotes = true },
                    label = { Text("Notatki") }
                )
            }
            if (showNotes) {
                Text("Notatki", style = MaterialTheme.typography.titleLarge)
                Text("Widok notatek i operacje zapisu zostaną podłączone w MOB-041.")
                Text("Bez załączników, nagrań i danych zapisanych na urządzeniu.",
                    style = MaterialTheme.typography.bodySmall)
            } else {
                ReadPanel(interviews, onRefresh, { false }) { records ->
                    if (selectedInterviewId == null) {
                        Text("Wywiady", style = MaterialTheme.typography.titleLarge)
                        if (records.isEmpty()) {
                            Text("Brak wywiadów w aktualnej kartotece.")
                        } else {
                            records.forEach { record ->
                                ElevatedCard(Modifier.fillMaxWidth()) {
                                    Column(
                                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text(interviewStatus(record.status), style = MaterialTheme.typography.titleMedium)
                                        Text("Szablon: ${record.templateCode ?: "nieznany"}, wersja ${record.templateVersion ?: "?"}",
                                            style = MaterialTheme.typography.bodySmall)
                                        OutlinedButton(
                                            onClick = { record.id?.let(onOpenInterview) },
                                            enabled = record.id != null
                                        ) { Text(if (record.status == "DRAFT") "Otwórz szkic" else "Przejrzyj wywiad") }
                                    }
                                }
                            }
                        }
                        Button(onClick = {}, enabled = false, modifier = Modifier.fillMaxWidth()) {
                            Text("Rozpocznij nowy wywiad")
                        }
                        Text("Rozpoczęcie i zapis wywiadu zostaną podłączone w kolejnym etapie MOB-040.",
                            style = MaterialTheme.typography.bodySmall)
                    } else {
                        val selected = records.firstOrNull { it.id == selectedInterviewId }
                        if (selected == null) {
                            Text("Wywiad nie jest już dostępny w aktualnej kartotece.",
                                color = MaterialTheme.colorScheme.error)
                            TextButton(onClick = onCloseInterview) { Text("Wróć do listy") }
                        } else {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Wywiad — ${interviewStatus(selected.status)}",
                                    style = MaterialTheme.typography.titleLarge)
                                TextButton(onClick = {
                                    if (dirty) discardConfirmation = true else onCloseInterview()
                                }) { Text("Lista") }
                            }
                            Text("Pytania i sekcje są pobierane z backendu. Ukończony wywiad jest nieedytowalny.",
                                style = MaterialTheme.typography.bodySmall)
                            val answers = selected.answers.associateBy { it.code }
                            val canEdit = selected.status == "DRAFT" &&
                                "SAVE_DRAFT" in selected.availableActions &&
                                draft?.interviewId == selected.id &&
                                draft.participantId == selected.participantId
                            selected.questions.groupBy { it.section }.forEach { (section, questions) ->
                                Text(sectionTitle(section), style = MaterialTheme.typography.titleMedium)
                                questions.forEach { question ->
                                    val title = questionTitle(question) + if (question.required) " *" else ""
                                    val fromServer = interviewAnswerText(answers[question.code], question)
                                    val value = if (canEdit && editableInterviewAnswer(question)) {
                                        draft?.values?.get(question.code) ?: fromServer
                                    } else fromServer
                                    if (editableInterviewAnswer(question)) {
                                        DictationReadyTextField(
                                            fieldId = question.code,
                                            title = title,
                                            text = value,
                                            canEdit = canEdit,
                                            keyboardType = when (question.type) {
                                                "NUMBER" -> KeyboardType.Decimal
                                                "DATE" -> KeyboardType.Ascii
                                                else -> KeyboardType.Text
                                            },
                                            onTextChange = { onUpdateText(question.code, it) },
                                            onRequestOnDeviceDictation = if (question.type == "TEXT" && canEdit)
                                                onRequestOnDeviceDictation else null
                                        )
                                        if (question.type == "DATE") {
                                            Text("Data w formacie RRRR-MM-DD", style = MaterialTheme.typography.bodySmall)
                                        }
                                    } else {
                                        Text(title, style = MaterialTheme.typography.labelLarge)
                                        Text(value.ifBlank { "Nie uzupełniono" })
                                        Text("Opcje wyboru nie są jeszcze udostępniane przez API; nie wprowadzamy domyślnych wartości.",
                                            style = MaterialTheme.typography.bodySmall)
                                    }
                                    HorizontalDivider()
                                }
                            }
                            if (canEdit) {
                                if (dirty) Text("Zmiany tylko w pamięci telefonu — jeszcze niezapisane.",
                                    color = MaterialTheme.colorScheme.error)
                                Button(onClick = {}, enabled = false, modifier = Modifier.fillMaxWidth()) {
                                    Text("Zapisz szkic")
                                }
                                OutlinedButton(onClick = {}, enabled = false, modifier = Modifier.fillMaxWidth()) {
                                    Text("Zakończ wywiad")
                                }
                                Text("Operacje zapisu i finalizacji zostaną podłączone w kolejnym kroku.",
                                    style = MaterialTheme.typography.bodySmall)
                            } else if ("START_NEXT_INTERVIEW" in selected.availableActions) {
                                OutlinedButton(onClick = {}, enabled = false, modifier = Modifier.fillMaxWidth()) {
                                    Text("Rozpocznij kolejny wywiad")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
