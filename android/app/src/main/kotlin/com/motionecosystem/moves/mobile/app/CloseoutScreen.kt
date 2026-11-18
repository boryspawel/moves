package com.motionecosystem.moves.mobile.app

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.motionecosystem.moves.mobile.api.AppointmentStatus
import com.motionecosystem.moves.mobile.api.AppointmentView
import com.motionecosystem.moves.mobile.api.CloseoutContextView

/** The closeout operation has no free-text request body; summaries are persisted as S07 notes. */
internal fun canCompleteVisit(
    appointment: AppointmentView?,
    closeout: CloseoutContextView?,
    save: CloseoutSaveState
): Boolean = appointment != null && closeout != null &&
    appointment.appointmentId != null &&
    appointment.appointmentId == closeout.appointmentId &&
    appointment.participantId != null &&
    appointment.participantId == closeout.participantId &&
    appointment.version != null && appointment.version == closeout.version &&
    appointment.status == AppointmentStatus.IN_PROGRESS &&
    closeout.status == AppointmentStatus.IN_PROGRESS &&
    "COMPLETE" in appointment.availableActions.orEmpty() &&
    closeout.canComplete &&
    (!closeout.executionRequired || closeout.executionRecorded) &&
    save !in setOf(CloseoutSaveState.Saving, CloseoutSaveState.Conflict, CloseoutSaveState.Saved)

@Composable
internal fun CloseoutScreen(
    appointment: ReadState<AppointmentView>,
    context: ReadState<CloseoutContextView>,
    save: CloseoutSaveState,
    onRefresh: () -> Unit,
    onComplete: () -> Unit,
    onResolveConflict: () -> Unit,
    onBack: () -> Unit,
    onFinished: () -> Unit
) {
    val saving = save == CloseoutSaveState.Saving
    var confirm by remember { mutableStateOf(false) }
    BackHandler(enabled = saving) { }
    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text("Zakończyć spotkanie?") },
            text = { Text(
                if (save == CloseoutSaveState.Uncertain)
                    "Wynik poprzedniego zapisu jest niepewny. Ponowienie użyje tego samego klucza i wersji spotkania."
                else "Zakończenie zapisze stan wizyty na serwerze. Nie można go cofnąć."
            ) },
            confirmButton = {
                TextButton(onClick = { confirm = false; onComplete() }) {
                    Text("Potwierdź zakończenie")
                }
            },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("Anuluj") } }
        )
    }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(onClick = onBack, enabled = !saving) { Text("Wróć") }
            TextButton(onClick = onRefresh, enabled = !saving) { Text("Odśwież") }
        }
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("Zakończenie spotkania", style = MaterialTheme.typography.headlineMedium)
            if (save == CloseoutSaveState.Saved) {
                Text("Spotkanie zakończone na serwerze.", style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary)
                Button(onClick = onFinished, modifier = Modifier.fillMaxWidth()) {
                    Text("Wróć do Dnia")
                }
            } else {
                when (save) {
                    CloseoutSaveState.Saving -> LinearProgressIndicator(Modifier.fillMaxWidth())
                    CloseoutSaveState.Uncertain -> Text(
                        "Nie otrzymano potwierdzenia. Odśwież stan spotkania przed ewentualnym ponowieniem tym samym kluczem.",
                        color = MaterialTheme.colorScheme.error)
                    CloseoutSaveState.Conflict -> Text(
                        "Konflikt 409. Sprawdź aktualny status i wersję wizyty; zapis nie zostanie ponowiony automatycznie.",
                        color = MaterialTheme.colorScheme.error)
                    is CloseoutSaveState.Failed -> Text(save.message, color = MaterialTheme.colorScheme.error)
                    else -> Unit
                }
                ReadPanel(appointment, onRefresh, { false }) { detail ->
                    ReadPanel(context, onRefresh, { false }) { closeout ->
                        if (detail.appointmentId != closeout.appointmentId ||
                            detail.participantId != closeout.participantId) {
                            Text("Niezgodny kontekst spotkania. Odśwież dane.",
                                color = MaterialTheme.colorScheme.error)
                        } else {
                            Text("Kontrola kompletności", style = MaterialTheme.typography.titleLarge)
                            ElevatedCard(Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Text(if (!closeout.executionRequired) "Realizacja sesji: niewymagana"
                                        else if (closeout.executionRecorded) "Realizacja sesji: zapisana"
                                        else "Realizacja sesji: wymagana przed zakończeniem")
                                    closeout.execution?.outcome?.let { Text("Wynik realizacji: $it") }
                                    Text(if (closeout.canComplete && closeout.status == AppointmentStatus.IN_PROGRESS)
                                        "Serwer zezwala na zakończenie."
                                        else "Serwer nie zezwala obecnie na zakończenie.")
                                }
                            }
                            Text("Podsumowanie wizyty", style = MaterialTheme.typography.titleLarge)
                            val summary = closeout.summaryNote
                            if (summary == null) {
                                Text("Brak podsumowania. Można je zapisać w Dokumentacji jako notatkę podsumowującą to spotkanie.")
                            } else {
                                summary.title?.let { Text(it, style = MaterialTheme.typography.titleMedium) }
                                summary.content?.let { Text(it) }
                            }
                            Text("Podsumowanie jest odczytywane z dokumentacji. Zamknięcie nie zapisuje ani nie modyfikuje notatek.",
                                style = MaterialTheme.typography.bodySmall)
                            if (save == CloseoutSaveState.Conflict &&
                                detail.version == closeout.version &&
                                closeout.status == AppointmentStatus.IN_PROGRESS) {
                                OutlinedButton(onClick = onResolveConflict, modifier = Modifier.fillMaxWidth()) {
                                    Text("Przyjmij aktualny stan")
                                }
                            }
                            val enabled = canCompleteVisit(detail, closeout, save)
                            Button(onClick = { confirm = true }, enabled = enabled,
                                modifier = Modifier.fillMaxWidth()) {
                                Text(if (save == CloseoutSaveState.Uncertain)
                                    "Ponów zakończenie tym samym kluczem" else "Zakończ spotkanie")
                            }
                            if (detail.version != closeout.version) {
                                Text("Wersja spotkania zmieniła się. Odśwież oba odczyty przed zakończeniem.",
                                    color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
        }
    }
}
