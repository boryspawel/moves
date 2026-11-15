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
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val dateTime = DateTimeFormatter.ofPattern("d MMMM yyyy, HH:mm", Locale.forLanguageTag("pl-PL"))

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
    onBack: () -> Unit
) {
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            TextButton(onClick = onBack) { Text("Wróć") }
            TextButton(onClick = onRefresh, enabled = startState != AppointmentStartState.SAVING) {
                Text("Odśwież")
            }
        }
        ReadPanel(state, onRefresh, { false }) { item ->
            val zone = runCatching {
                ZoneId.of(timeZoneId ?: ZoneId.systemDefault().id)
            }.getOrDefault(ZoneId.systemDefault())
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
                            Text("Od: ${it.atZoneSameInstant(zone).format(dateTime)}")
                        }
                        item.endsAt?.let {
                            Text("Do: ${it.atZoneSameInstant(zone).format(dateTime)}")
                        }
                        Text("Strefa czasu: ${timeZoneId ?: "czas telefonu"}",
                            style = MaterialTheme.typography.labelMedium)
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
                HorizontalDivider()
                Text("Przebieg spotkania", style = MaterialTheme.typography.titleLarge)
                Text(
                    if (item.plannedSessionId != null) "Realizacja powiązanej sesji — kolejny etap."
                    else "Brak powiązanej sesji do realizacji.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text("Dokumentacja i zakończenie spotkania zostaną podłączone w kolejnych etapach.",
                    style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
