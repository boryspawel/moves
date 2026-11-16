package com.motionecosystem.moves.mobile.app

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.motionecosystem.moves.mobile.api.*
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID

private val polish = Locale.forLanguageTag("pl-PL")
private val labelDate = DateTimeFormatter.ofPattern("EEE, d MMM yyyy", polish)
private val labelHours = DateTimeFormatter.ofPattern("HH:mm", polish)

internal fun slotLabel(slot: BookableSlotView, zoneName: String?): String {
    val zone = runCatching { ZoneId.of(zoneName ?: "UTC") }.getOrDefault(ZoneOffset.UTC)
    val start = slot.startsAt ?: return "Nieznana godzina"
    val end = slot.endsAt ?: return "Nieznana godzina"
    return "${start.atZoneSameInstant(zone).format(labelHours)}–${end.atZoneSameInstant(zone).format(labelHours)}"
}

internal fun eligibleCalendarClients(clients: List<ClientView>): List<ClientView> =
    clients.filter { it.participantId != null && it.recordStatus != ClientRecordStatus.ARCHIVED &&
        "SCHEDULE_APPOINTMENT" in it.availableActions.orEmpty() }

internal fun validBookableSlot(slot: BookableSlotView?, day: TodayView): Boolean =
    slot?.startsAt != null && slot.endsAt != null &&
        slot.endsAt.toInstant().isAfter(slot.startsAt.toInstant()) &&
        day.bookableSlots.orEmpty().any {
            it.startsAt?.toInstant() == slot.startsAt.toInstant() &&
                it.endsAt?.toInstant() == slot.endsAt.toInstant()
        }

/** Never reuse another participant's cached appointment when opening a new calendar entry.
 * An edit may read fields only from the exact appointment and participant being edited.
 */
internal fun scheduleDetailsFor(target: ScheduleTarget, state: ReadState<AppointmentView>): AppointmentView? {
    val edit = target as? ScheduleTarget.Edit ?: return null
    val detail = (state as? ReadState.Ready)?.value ?: return null
    return detail.takeIf {
        it.appointmentId == edit.appointmentId && it.participantId == edit.participantId
    }
}

@Composable
internal fun ScheduleScreen(
    target: ScheduleTarget,
    scheduleDay: ReadState<TodayView>,
    requestedDate: LocalDate?,
    clients: ReadState<List<ClientView>>,
    appointment: ReadState<AppointmentView>,
    saveState: ScheduleSaveState,
    onChooseDate: (LocalDate?) -> Unit,
    onRefresh: () -> Unit,
    onSave: (AppointmentSaveCommand) -> Unit,
    onBack: () -> Unit
) {
    val existing = scheduleDetailsFor(target, appointment)
    val edit = target as? ScheduleTarget.Edit
    val saving = saveState == ScheduleSaveState.Saving
    val clientOptions = eligibleCalendarClients((clients as? ReadState.Ready)?.value.orEmpty())
    var participant by remember(target) {
        mutableStateOf(
            when (target) {
                is ScheduleTarget.New -> target.participantId
                is ScheduleTarget.Edit -> target.participantId
            }
        )
    }
    var type by remember(target, existing?.version) {
        mutableStateOf(existing?.type ?: AppointmentType.CONSULTATION)
    }
    var mode by remember(target, existing?.version) {
        mutableStateOf(existing?.locationMode ?: AppointmentLocationMode.IN_PERSON)
    }
    var location by remember(target, existing?.version) {
        mutableStateOf(existing?.location.orEmpty())
    }
    var purpose by remember(target, existing?.version) {
        mutableStateOf(existing?.shortPurpose.orEmpty())
    }
    var selectedSlot by remember(target, requestedDate) { mutableStateOf<BookableSlotView?>(null) }
    var clientMenu by remember { mutableStateOf(false) }
    var typeMenu by remember { mutableStateOf(false) }
    var modeMenu by remember { mutableStateOf(false) }
    val editable = edit == null || (existing?.appointmentId == edit.appointmentId &&
        existing.participantId == edit.participantId &&
        existing.version != null && "UPDATE" in existing.availableActions.orEmpty())

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(onClick = onBack, enabled = !saving) { Text("Wróć") }
            TextButton(onClick = onRefresh, enabled = !saving) { Text("Odśwież terminy") }
        }
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(if (edit == null) "Dodaj termin" else "Zmień termin",
                style = MaterialTheme.typography.headlineMedium)
            if (edit != null) {
                Text("Zmiana istniejącego terminu zachowa powiązanie z sesją ćwiczeń.")
                if (!editable) Text("Brak aktualnego uprawnienia UPDATE. Wróć do spotkania i odśwież jego stan.",
                    color = MaterialTheme.colorScheme.error)
            }
            if (clients !is ReadState.Ready && edit == null) {
                Text("Pobieranie uprawnionych kartotek…")
            }
            if (edit == null) {
                Box {
                    OutlinedButton(onClick = { clientMenu = true }, enabled = !saving) {
                        Text(clientOptions.firstOrNull { it.participantId == participant }?.displayName
                            ?: "Wybierz uczestnika")
                    }
                    DropdownMenu(expanded = clientMenu, onDismissRequest = { clientMenu = false }) {
                        clientOptions.forEach { item ->
                            DropdownMenuItem(text = { Text(item.displayName ?: "Uczestnik") },
                                onClick = { participant = item.participantId; clientMenu = false },
                                enabled = !saving)
                        }
                    }
                }
                if (clientOptions.isEmpty() && clients is ReadState.Ready) {
                    Text("Brak aktywnych kartotek pozwalających planować terminy.")
                }
            } else {
                Text("Uczestnik: " + (clientOptions.firstOrNull { it.participantId == participant }?.displayName
                    ?: "wybrana kartoteka"))
            }

            ReadPanel(scheduleDay, onRefresh, { false }) { day ->
                val actualDate = day.localDate ?: requestedDate
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(actualDate?.format(labelDate)?.replaceFirstChar { it.titlecase(polish) }
                        ?: "Wybierz dzień", style = MaterialTheme.typography.titleMedium)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        TextButton(onClick = { actualDate?.let { onChooseDate(it.minusDays(1)) } },
                            enabled = !saving && actualDate != null) { Text("Poprzedni") }
                        TextButton(onClick = { onChooseDate(null) }, enabled = !saving && requestedDate != null) {
                            Text("Dzisiaj")
                        }
                        TextButton(onClick = { actualDate?.let { onChooseDate(it.plusDays(1)) } },
                            enabled = !saving && actualDate != null) { Text("Następny") }
                    }
                    Text("Strefa kalendarza: ${day.timeZoneId ?: "UTC"}",
                        style = MaterialTheme.typography.labelSmall)
                    Text("Wolne terminy", style = MaterialTheme.typography.titleMedium)
                    val slots = day.bookableSlots.orEmpty()
                        .filter { it.startsAt != null && it.endsAt != null &&
                            it.endsAt.toInstant().isAfter(it.startsAt.toInstant()) }
                        .sortedBy { it.startsAt?.toInstant() }
                    if (slots.isEmpty()) Text("Brak dostępnych godzin w wybranym dniu.")
                    slots.forEach { slot ->
                        val checked = selectedSlot == slot
                        FilterChip(
                            selected = checked,
                            enabled = !saving,
                            onClick = { selectedSlot = slot },
                            label = { Text(slotLabel(slot, day.timeZoneId)) }
                        )
                    }
                    Text("Ostateczne potwierdzenie dostępności i konfliktów wykonuje serwer.",
                        style = MaterialTheme.typography.bodySmall)
                }
            }
            HorizontalDivider()
            Text("Rodzaj i miejsce", style = MaterialTheme.typography.titleMedium)
            Box {
                OutlinedButton(onClick = { typeMenu = true }, enabled = !saving) {
                    Text("Rodzaj: ${when (type) {
                        AppointmentType.TRAINING -> "Trening"
                        AppointmentType.PHYSIOTHERAPY -> "Fizjoterapia"
                        AppointmentType.ASSESSMENT -> "Ocena"
                        AppointmentType.CONSULTATION -> "Konsultacja"
                    }}")
                }
                DropdownMenu(expanded = typeMenu, onDismissRequest = { typeMenu = false }) {
                    AppointmentType.entries.forEach { item ->
                        DropdownMenuItem(text = { Text(item.name) },
                            onClick = { type = item; typeMenu = false })
                    }
                }
            }
            Box {
                OutlinedButton(onClick = { modeMenu = true }, enabled = !saving) {
                    Text("Forma: ${when (mode) {
                        AppointmentLocationMode.IN_PERSON -> "Na miejscu"
                        AppointmentLocationMode.REMOTE -> "Zdalnie"
                        AppointmentLocationMode.PHONE -> "Telefon"
                    }}")
                }
                DropdownMenu(expanded = modeMenu, onDismissRequest = { modeMenu = false }) {
                    AppointmentLocationMode.entries.forEach { item ->
                        DropdownMenuItem(text = { Text(item.name) },
                            onClick = { mode = item; modeMenu = false })
                    }
                }
            }
            OutlinedTextField(value = location, onValueChange = { location = it },
                label = { Text("Miejsce (opcjonalnie)") }, singleLine = true,
                enabled = !saving, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = purpose, onValueChange = { purpose = it },
                label = { Text("Cel spotkania (opcjonalnie)") },
                enabled = !saving, modifier = Modifier.fillMaxWidth(), maxLines = 3)
            when (saveState) {
                ScheduleSaveState.Uncertain ->
                    Text("Niepewna odpowiedź serwera. Ponowienie bez zmian użyje tego samego klucza.",
                        color = MaterialTheme.colorScheme.error)
                ScheduleSaveState.Conflict ->
                    Text("Konflikt 409: odświeżono wolne sloty i stan spotkania. Wybierz termin ponownie.",
                        color = MaterialTheme.colorScheme.error)
                is ScheduleSaveState.Failed ->
                    Text(saveState.message, color = MaterialTheme.colorScheme.error)
                else -> Unit
            }
            val today = (scheduleDay as? ReadState.Ready)?.value
            val slot = selectedSlot?.takeIf { today != null && validBookableSlot(it, today) }
            val allowedParticipant = if (edit == null) {
                clientOptions.any { it.participantId == participant }
            } else editable
            Button(
                onClick = {
                    val id = participant
                    val start = slot?.startsAt
                    val end = slot?.endsAt
                    if (id != null && start != null && end != null) {
                        onSave(AppointmentSaveCommand(
                            participantId = id, startsAt = start, endsAt = end,
                            type = type, locationMode = mode,
                            location = location.trim().ifBlank { null },
                            shortPurpose = purpose.trim().ifBlank { null },
                            plannedSessionId = if (edit != null) existing?.plannedSessionId else null,
                            version = if (edit != null) existing?.version else null
                        ))
                    }
                },
                enabled = !saving && participant != null && allowedParticipant && slot != null &&
                    location.length <= 160 && purpose.length <= 500 && saveState !is ScheduleSaveState.Saved,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(when {
                    saving -> "Zapisywanie…"
                    saveState == ScheduleSaveState.Uncertain -> "Ponów ten sam zapis"
                    edit == null -> "Dodaj termin"
                    else -> "Zapisz zmianę"
                })
            }
        }
    }
}
