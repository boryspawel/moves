package com.motionecosystem.moves.mobile.app

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.motionecosystem.moves.mobile.api.AppointmentStatus
import com.motionecosystem.moves.mobile.api.AppointmentType
import com.motionecosystem.moves.mobile.api.AppointmentView
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private val dateTime = DateTimeFormatter.ofPattern("d MMMM yyyy, HH:mm", Locale.forLanguageTag("pl-PL"))

/** When the specialist zone is unknown, retain the offset received from the server
 * and mark it explicitly. This is never a guessed local appointment time.
 */
internal fun appointmentTimeLabel(value: OffsetDateTime, timeZoneId: String?): String {
    val zone = specialistCalendarZone(timeZoneId)
    if (zone != null) return value.atZoneSameInstant(zone).format(dateTime)
    val offset = value.offset.id.takeUnless { it == "Z" } ?: "+00:00"
    return "${value.format(dateTime)} (UTC$offset)"
}

internal fun mayStartAppointment(item: AppointmentView): Boolean =
    item.appointmentId != null && item.participantId != null && item.version != null &&
        item.status in setOf(AppointmentStatus.SCHEDULED, AppointmentStatus.CONFIRMED) &&
        "START" in item.availableActions.orEmpty()

private fun statusText(status: AppointmentStatus?): String = when (status) {
    AppointmentStatus.SCHEDULED -> "Zaplanowane"
    AppointmentStatus.CONFIRMED -> "Potwierdzone"
    AppointmentStatus.IN_PROGRESS -> "W toku"
    AppointmentStatus.COMPLETED -> "Zakończone"
    AppointmentStatus.CANCELLED -> "Odwołane"
    AppointmentStatus.NO_SHOW -> "Nieodbyte"
    null -> "Status nieznany"
}

private fun appointmentTypeText(type: AppointmentType?): String = when (type) {
    AppointmentType.TRAINING -> "Trening"
    AppointmentType.PHYSIOTHERAPY -> "Fizjoterapia"
    AppointmentType.ASSESSMENT -> "Ocena"
    AppointmentType.CONSULTATION -> "Konsultacja"
    null -> "Spotkanie"
}

@Composable
internal fun AppointmentScreen(
    state: ReadState<AppointmentView>,
    startState: AppointmentStartState,
    participantLabel: String?,
    timeZoneId: String?,
    onRefresh: () -> Unit,
    onStart: () -> Unit,
    onEdit: () -> Unit,
    onExecution: () -> Unit,
    onDocumentation: () -> Unit,
    onCloseout: () -> Unit,
    onBack: () -> Unit
) {
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            TextButton(onClick = onBack, enabled = canLeaveAppointment(startState)) { Text("Wróć") }
            TextButton(onClick = onRefresh, enabled = startState != AppointmentStartState.SAVING) {
                Text("Odśwież")
            }
        }
        ReadPanel(state, onRefresh, { false }) { item ->
            val knownZone = specialistCalendarZone(timeZoneId) != null
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text("Spotkanie", style = MaterialTheme.typography.headlineMedium)
                participantLabel?.takeIf { it.isNotBlank() }?.let {
                    Text(it, style = MaterialTheme.typography.titleMedium)
                }
                ElevatedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(statusText(item.status), style = MaterialTheme.typography.titleLarge)
                        Text(appointmentTypeText(item.type), style = MaterialTheme.typography.titleMedium)
                        item.startsAt?.let {
                            Text("Od: ${appointmentTimeLabel(it, timeZoneId)}")
                        }
                        item.endsAt?.let {
                            Text("Do: ${appointmentTimeLabel(it, timeZoneId)}")
                        }
                        Text(
                            if (knownZone) "Strefa czasu: $timeZoneId"
                            else "Strefa specjalisty nieustalona — pokazano offset z odpowiedzi serwera.",
                            style = MaterialTheme.typography.labelMedium
                        )
                        item.shortPurpose?.takeIf { it.isNotBlank() }?.let {
                            Text("Cel: $it", style = MaterialTheme.typography.bodyMedium)
                        }
                        Text(if (item.plannedSessionId == null) "Bez powiązanej sesji ćwiczeń"
                            else "Powiązana sesja ćwiczeń",
                            style = MaterialTheme.typography.bodyMedium)
                    }
                }

                if (item.status == AppointmentStatus.IN_PROGRESS) {
                    Text("Spotkanie jest rozpoczęte. Wyjście z ekranu nie kończy wizyty.",
                        style = MaterialTheme.typography.bodyMedium)
                }
                when (startState) {
                    AppointmentStartState.UNCERTAIN -> Text(
                        "Nie otrzymano pewnego potwierdzenia. Sprawdź stan spotkania lub ponów próbę tym samym kluczem.",
                        color = MaterialTheme.colorScheme.error
                    )
                    AppointmentStartState.CONFLICT -> Text(
                        "Dane spotkania zmieniły się (409). Pobrano aktualny stan z serwera.",
                        color = MaterialTheme.colorScheme.error
                    )
                    AppointmentStartState.FAILED -> Text(
                        "Serwer odrzucił rozpoczęcie. Odśwież dane i spróbuj ponownie.",
                        color = MaterialTheme.colorScheme.error
                    )
                    else -> Unit
                }
                if (mayStartAppointment(item)) {
                    Button(
                        onClick = onStart,
                        enabled = startState != AppointmentStartState.SAVING,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(when (startState) {
                            AppointmentStartState.SAVING -> "Rozpoczynanie…"
                            AppointmentStartState.UNCERTAIN -> "Ponów rozpoczęcie"
                            else -> "Rozpocznij spotkanie"
                        })
                    }
                } else if (item.status in setOf(AppointmentStatus.SCHEDULED, AppointmentStatus.CONFIRMED)) {
                    Text("Rozpoczęcie nie jest obecnie dostępne. Decyzja należy do serwera.",
                        style = MaterialTheme.typography.bodyMedium)
                }
                if ("UPDATE" in item.availableActions.orEmpty() && item.version != null) {
                    OutlinedButton(onClick = onEdit, enabled = startState != AppointmentStartState.SAVING) {
                        Text("Zmień termin")
                    }
                }
                HorizontalDivider()
                Text("Przebieg spotkania", style = MaterialTheme.typography.titleLarge)
                val visit = VerifiedVisit(
                    appointmentId = item.appointmentId ?: return@ReadPanel,
                    participantId = item.participantId ?: return@ReadPanel,
                    plannedSessionId = item.plannedSessionId,
                    status = item.status ?: return@ReadPanel
                )
                if (canOpenVisitStage(VisitStage.EXECUTION, visit)) {
                    OutlinedButton(onClick = onExecution, enabled = startState != AppointmentStartState.SAVING,
                        modifier = Modifier.fillMaxWidth()) {
                        Text("Realizacja sesji")
                    }
                } else {
                    Text(if (item.plannedSessionId == null) "Brak powiązanej sesji ćwiczeń."
                        else "Realizacja dostępna po rozpoczęciu spotkania.",
                        style = MaterialTheme.typography.bodyMedium)
                }
                if (canOpenVisitStage(VisitStage.DOCUMENTATION, visit)) {
                    OutlinedButton(onClick = onDocumentation,
                        enabled = startState != AppointmentStartState.SAVING,
                        modifier = Modifier.fillMaxWidth()) {
                        Text("Dokumentacja")
                    }
                }
                if (canOpenVisitStage(VisitStage.CLOSEOUT, visit)) {
                    OutlinedButton(onClick = onCloseout,
                        enabled = startState != AppointmentStartState.SAVING,
                        modifier = Modifier.fillMaxWidth()) {
                        Text("Przejdź do zakończenia")
                    }
                }
                Text("Realizacja ćwiczeń i dokumentacja zapisują dane na serwerze. " +
                    "Zamknięcie spotkania będzie dostępne w kolejnym etapie.",
                    style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
