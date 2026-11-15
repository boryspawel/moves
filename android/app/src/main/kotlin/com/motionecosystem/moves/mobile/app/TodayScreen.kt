package com.motionecosystem.moves.mobile.app

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.motionecosystem.moves.mobile.api.*
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID

private val polish = Locale.forLanguageTag("pl-PL")
private val dayFormat = DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy", polish)
private val clockFormat = DateTimeFormatter.ofPattern("HH:mm", polish)

/** The server's IANA zone, rather than the device zone, controls the displayed agenda. */
internal fun appointmentHours(appointment: AppointmentView, timeZoneId: String?): String {
    val start = appointment.startsAt ?: return "Godzina nieustalona"
    val zone = runCatching { ZoneId.of(timeZoneId ?: "UTC") }.getOrDefault(ZoneOffset.UTC)
    val first = start.atZoneSameInstant(zone).format(clockFormat)
    val last = appointment.endsAt?.atZoneSameInstant(zone)?.format(clockFormat)
    return if (last == null) first else "$first–$last"
}

internal fun orderedAgenda(today: TodayView): List<AppointmentView> =
    today.appointments.orEmpty().sortedWith(
        compareBy<AppointmentView> { it.startsAt?.toInstant() ?: Instant.MAX }
            .thenBy { it.appointmentId?.toString().orEmpty() }
    )

private fun priorityLabel(priority: String): String = when (priority) {
    "HIGH" -> "Pilne"
    "MEDIUM" -> "Średnie"
    "LOW" -> "Niskie"
    else -> "Do sprawdzenia"
}

private fun appointmentStatus(status: AppointmentStatus?): String = when (status) {
    AppointmentStatus.SCHEDULED -> "Zaplanowane"
    AppointmentStatus.CONFIRMED -> "Potwierdzone"
    AppointmentStatus.IN_PROGRESS -> "W trakcie"
    AppointmentStatus.COMPLETED -> "Zakończone"
    AppointmentStatus.CANCELLED -> "Odwołane"
    AppointmentStatus.NO_SHOW -> "Nieodbyte"
    null -> "Status nieznany"
}

private fun appointmentType(type: AppointmentType?): String? = when (type) {
    AppointmentType.TRAINING -> "Trening"
    AppointmentType.PHYSIOTHERAPY -> "Fizjoterapia"
    AppointmentType.ASSESSMENT -> "Ocena"
    AppointmentType.CONSULTATION -> "Konsultacja"
    null -> null
}

private fun locationLabel(mode: AppointmentLocationMode?): String? = when (mode) {
    AppointmentLocationMode.IN_PERSON -> "Na miejscu"
    AppointmentLocationMode.REMOTE -> "Zdalnie"
    AppointmentLocationMode.PHONE -> "Telefonicznie"
    null -> null
}

@Composable
internal fun TodayScreen(
    state: ReadState<TodayView>,
    requestedDate: LocalDate?,
    onChooseDate: (LocalDate?) -> Unit,
    onRefresh: () -> Unit,
    onAppointment: (UUID?, UUID) -> Unit,
    onCases: () -> Unit
) {
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Dzień", style = MaterialTheme.typography.headlineMedium)
            TextButton(onClick = onRefresh) { Text("Odśwież") }
        }
        if (requestedDate != null && state !is ReadState.Ready) {
            TextButton(onClick = { onChooseDate(null) }, modifier = Modifier.padding(start = 8.dp)) {
                Text("Wróć do dzisiaj")
            }
        }
        ReadPanel(state, onRefresh, { false }) { today ->
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                val day = today.localDate ?: requestedDate
                if (day != null) {
                    Text(day.format(dayFormat).replaceFirstChar { it.titlecase(polish) },
                        style = MaterialTheme.typography.titleMedium)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        TextButton(onClick = { onChooseDate(day.minusDays(1)) }) { Text("Poprzedni") }
                        TextButton(onClick = { onChooseDate(null) }, enabled = requestedDate != null) {
                            Text("Dzisiaj")
                        }
                        TextButton(onClick = { onChooseDate(day.plusDays(1)) }) { Text("Następny") }
                    }
                }
                today.timeZoneId?.let { Text("Strefa kalendarza: $it",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant) }

                today.currentAppointment?.let { item ->
                    Text("Bieżące spotkanie", style = MaterialTheme.typography.titleMedium)
                    AppointmentCard(item, today.timeZoneId, onAppointment)
                }
                today.nextAppointment?.let { item ->
                    if (item.appointmentId != today.currentAppointment?.appointmentId) {
                        Text("Następne spotkanie", style = MaterialTheme.typography.titleMedium)
                        AppointmentCard(item, today.timeZoneId, onAppointment)
                    }
                }

                val agenda = orderedAgenda(today)
                Text("Agenda (${agenda.size})", style = MaterialTheme.typography.titleMedium)
                if (agenda.isEmpty()) Text("Brak spotkań w wybranym dniu.")
                agenda.forEach { appointment ->
                    AppointmentCard(appointment, today.timeZoneId, onAppointment)
                }

                HorizontalDivider()
                val attentions = today.attentionItems.orEmpty()
                val tasks = today.operationalTasks.orEmpty()
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Sprawy i zadania", style = MaterialTheme.typography.titleMedium)
                    TextButton(onClick = onCases) { Text("Otwórz sprawy") }
                }
                Text("Do sprawdzenia: ${attentions.size} • Zadania: ${tasks.size}",
                    style = MaterialTheme.typography.bodyMedium)
                if (attentions.isEmpty() && tasks.isEmpty()) Text("Brak bieżących spraw.")
                attentions.forEach { attention ->
                    ListItem(
                        headlineContent = {
                            Text(if (attention.type == "PARTICIPANT_ISSUE") "Zgłoszenie uczestnika"
                                else "Sygnał wymagający uwagi")
                        },
                        supportingContent = { attention.participantLabel?.let { Text(it) } },
                        trailingContent = { attention.priority?.let { Text(priorityLabel(it)) } }
                    )
                }
                tasks.forEach { task ->
                    ListItem(headlineContent = { Text(task.title ?: "Zadanie operacyjne") })
                }
            }
        }
    }
}

@Composable
private fun AppointmentCard(
    appointment: AppointmentView,
    timeZoneId: String?,
    onOpen: (UUID?, UUID) -> Unit
) {
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(appointmentHours(appointment, timeZoneId), style = MaterialTheme.typography.titleMedium)
                Text(appointmentStatus(appointment.status), style = MaterialTheme.typography.labelMedium)
            }
            Text(appointment.participantLabel ?: "Uczestnik",
                style = MaterialTheme.typography.bodyLarge)
            val description = listOfNotNull(
                appointmentType(appointment.type),
                appointment.shortPurpose?.takeIf { it.isNotBlank() },
                locationLabel(appointment.locationMode)
            ).joinToString(" • ")
            if (description.isNotBlank()) Text(description, style = MaterialTheme.typography.bodySmall)
            appointment.location?.takeIf { it.isNotBlank() }?.let {
                Text(it, style = MaterialTheme.typography.bodySmall)
            }
            appointment.appointmentId?.let { id ->
                TextButton(onClick = { onOpen(appointment.participantId, id) }) {
                    Text("Otwórz spotkanie")
                }
            }
        }
    }
}
