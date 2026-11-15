package com.motionecosystem.moves.mobile.app

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.motionecosystem.moves.mobile.api.*
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

private val polish = Locale.forLanguageTag("pl-PL")
private val dateTimeFormat = DateTimeFormatter.ofPattern("d MMM, HH:mm", polish)

private fun localDateTime(time: OffsetDateTime?, zoneId: String?): String {
    if (time == null) return "Termin nieustalony"
    val zone = runCatching { ZoneId.of(zoneId ?: "UTC") }.getOrDefault(ZoneOffset.UTC)
    return time.atZoneSameInstant(zone).format(dateTimeFormat)
}

private fun eventLabel(event: TimelineEvent): String = when (event.category) {
    "APPOINTMENT" -> "Spotkanie"
    "SESSION" -> "Zaplanowana sesja"
    "EXECUTION" -> "Realizacja sesji"
    "GOAL" -> "Zmiana celu"
    "INTERVIEW" -> "Wywiad"
    "NOTE" -> "Notatka"
    else -> "Zdarzenie"
}

private fun focusLabel(kind: String?): String = when (kind) {
    "IMPORTANT_ATTENTION" -> "Sprawa wymagająca uwagi"
    "IN_PROGRESS_APPOINTMENT" -> "Trwa spotkanie"
    "NEXT_APPOINTMENT" -> "Nadchodzące spotkanie"
    "DRAFT_INTERVIEW" -> "Wywiad do dokończenia"
    "FOLLOW_UP" -> "Zadanie do wykonania"
    else -> "Brak pilnych czynności"
}

/** S03 presents text and status projections; no measurement values or raw timeline descriptions. */
@Composable
internal fun WorkspaceScreen(
    workspace: ReadState<WorkspaceView>,
    timeline: ReadState<TimelineFeed>,
    onRefresh: () -> Unit,
    onRetryTimeline: () -> Unit,
    onMoreTimeline: () -> Unit,
    onOpenAppointment: (java.util.UUID) -> Unit,
    onSchedule: () -> Unit,
    onBack: () -> Unit
) {
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(onClick = onBack) { Text("Wróć") }
            TextButton(onClick = onRefresh) { Text("Odśwież") }
        }
        ReadPanel(workspace, onRefresh, { false }) { data ->
            val zone = data.participant?.timeZoneId
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(data.participant?.displayName ?: "Kartoteka uczestnika",
                            style = MaterialTheme.typography.headlineMedium)
                        data.participant?.contextLabel?.let {
                            Text(it, style = MaterialTheme.typography.bodySmall)
                        }
                        val relationship = data.relationship?.status
                        if (relationship != null) Text("Relacja: $relationship",
                            style = MaterialTheme.typography.bodySmall)
                    }
                }
                item {
                    OutlinedButton(onClick = onSchedule, modifier = Modifier.fillMaxWidth()) {
                        Text("Umów spotkanie")
                    }
                }
                item {
                    OutlinedCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Aktualna sytuacja", style = MaterialTheme.typography.titleMedium)
                            Text(focusLabel(data.focus?.kind))
                            data.nextAppointment?.let { appointment ->
                                Text("Następne spotkanie: ${localDateTime(appointment.startsAt, zone)}")
                                appointment.status?.let { Text("Status: $it") }
                                appointment.appointmentId?.let { appointmentId ->
                                    TextButton(onClick = { onOpenAppointment(appointmentId) }) {
                                        Text("Otwórz spotkanie")
                                    }
                                }
                            }
                        }
                    }
                }
                item {
                    HorizontalDivider()
                    Text("Historia", style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.padding(top = 8.dp))
                    Text("Ostatnie 14 dni • bez pomiarów biomedycznych",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                when (timeline) {
                    ReadState.Idle, ReadState.Loading -> item {
                        CircularProgressIndicator(modifier = Modifier.padding(12.dp))
                    }
                    ReadState.Forbidden -> item { Text("Brak uprawnień do historii.") }
                    ReadState.Offline -> item {
                        OutlinedButton(onClick = onRetryTimeline) { Text("Brak sieci — ponów historię") }
                    }
                    ReadState.Error, ReadState.Conflict -> item {
                        OutlinedButton(onClick = onRetryTimeline) { Text("Ponów pobieranie historii") }
                    }
                    is ReadState.Ready -> {
                        if (timeline.value.items.isEmpty()) item { Text("Brak zdarzeń w historii.") }
                        items(timeline.value.items) { event ->
                            ListItem(
                                headlineContent = { Text(eventLabel(event)) },
                                supportingContent = {
                                    Text(localDateTime(event.effectiveFrom, zone))
                                },
                                trailingContent = {
                                    event.status?.let { Text(it, style = MaterialTheme.typography.labelSmall) }
                                }
                            )
                        }
                        if (timeline.value.nextCursor != null) item {
                            if (timeline.value.loadMoreFailed) {
                                Text("Nie udało się pobrać kolejnej strony.",
                                    color = MaterialTheme.colorScheme.error)
                            }
                            OutlinedButton(onClick = onMoreTimeline,
                                enabled = !timeline.value.loadingMore) {
                                Text(if (timeline.value.loadingMore) "Pobieranie…" else "Pokaż starsze zdarzenia")
                            }
                        }
                    }
                }
                item {
                    HorizontalDivider()
                    Text("Aktywny plan", style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.padding(top = 8.dp))
                }
                item {
                    val plan = data.activePlan
                    if (plan == null) Text("Brak aktywnego planu.")
                    else OutlinedCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(plan.name ?: "Plan", style = MaterialTheme.typography.titleMedium)
                            plan.status?.let { Text("Status: $it") }
                            val validity = listOfNotNull(plan.validFrom?.toString(), plan.validTo?.toString()).joinToString(" – ")
                            if (validity.isNotEmpty()) Text("Okres: $validity")
                            Text("Zaplanowane sesje: ${plan.activeSessionCount ?: 0}")
                        }
                    }
                }
                item {
                    HorizontalDivider()
                    Text("Cele", style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.padding(top = 8.dp))
                }
                if (data.goals.isNullOrEmpty()) item { Text("Brak zdefiniowanych celów.") }
                else items(data.goals.orEmpty()) { goal ->
                    OutlinedCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(goal.title ?: "Cel", style = MaterialTheme.typography.titleMedium)
                            goal.status?.let { Text("Status: $it") }
                            goal.targetDate?.let { Text("Termin: $it") }
                        }
                    }
                }
            }
        }
    }
}
