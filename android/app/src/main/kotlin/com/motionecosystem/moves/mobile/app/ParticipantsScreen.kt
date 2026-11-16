package com.motionecosystem.moves.mobile.app

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.motionecosystem.moves.mobile.api.ClientCommand
import com.motionecosystem.moves.mobile.api.ClientRecordStatus
import com.motionecosystem.moves.mobile.api.ClientRelationship
import com.motionecosystem.moves.mobile.api.ClientView
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID

private val dateFormat = DateTimeFormatter.ofPattern("d MMM, HH:mm", Locale.forLanguageTag("pl-PL"))

internal fun filteredClients(records: List<ClientView>, query: String): List<ClientView> =
    records.filter { it.displayName.orEmpty().contains(query.trim(), ignoreCase = true) }
        .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.displayName.orEmpty() })

private fun relationshipLabel(role: ClientRelationship?): String = when (role) {
    ClientRelationship.CLIENT -> "Klient"
    ClientRelationship.PATIENT -> "Pacjent"
    ClientRelationship.ATHLETE -> "Sportowiec"
    null -> "Automatycznie (według profilu specjalisty)"
}

@Composable
internal fun ParticipantsScreen(
    state: ReadState<List<ClientView>>,
    creation: CreateClientState,
    draft: ClientFormDraft,
    query: String,
    onSearch: (String) -> Unit,
    onUpdateDraft: (ClientFormDraft) -> Unit,
    onOpenCreate: () -> Unit,
    onRefresh: () -> Unit,
    onCreate: (ClientCommand, String) -> Unit,
    onCancel: () -> Unit,
    onOpen: (UUID) -> Unit
) {
    BackHandler(enabled = draft.open) {
        if (creation != CreateClientState.Saving) onCancel()
    }

    if (draft.open) {
        ClientCreateForm(
            state = creation,
            draft = draft,
            onUpdateDraft = onUpdateDraft,
            onSave = onCreate,
            onCancel = onCancel
        )
    } else {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Uczestnicy", style = MaterialTheme.typography.headlineMedium)
                TextButton(onClick = onRefresh) { Text("Odśwież") }
            }
            Button(onClick = onOpenCreate,
                modifier = Modifier.padding(horizontal = 16.dp)) {
                Text("Dodaj kartotekę")
            }
            ReadPanel(state, onRefresh, { false }) { clients ->
                Column(Modifier.fillMaxSize()) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = onSearch,
                        label = { Text("Szukaj po nazwie") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(16.dp)
                    )
                    val visible = filteredClients(clients, query)
                    if (visible.isEmpty()) {
                        Text(
                            if (clients.isEmpty()) "Brak kartotek. Dodaj pierwszego uczestnika."
                            else "Brak wyników wyszukiwania.",
                            modifier = Modifier.padding(20.dp)
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(visible) { client ->
                                ClientRow(client, onOpen)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ClientRow(client: ClientView, onOpen: (UUID) -> Unit) {
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(client.displayName ?: "Kartoteka", style = MaterialTheme.typography.titleMedium)
            val status = if (client.recordStatus == ClientRecordStatus.ARCHIVED) "Archiwalna" else "Aktywna"
            val access = if (client.accessStatus == "ACTIVE") "Konto aktywne" else "Bez aktywnego konta"
            Text("$status • ${relationshipLabel(client.relationshipContext)} • $access",
                style = MaterialTheme.typography.bodySmall)
            val appointment = client.nextAppointment?.startsAt
            Text(
                if (appointment == null) "Następny termin: brak"
                else "Następny termin: ${dateFormat.format(appointment.atZoneSameInstant(ZoneId.systemDefault()))} (czas telefonu)",
                style = MaterialTheme.typography.bodySmall
            )
            val id = client.participantId
            if (id != null && client.availableActions.orEmpty().contains("OPEN_WORKSPACE")) {
                TextButton(onClick = { onOpen(id) }) { Text("Otwórz kartotekę") }
            }
        }
    }
}

@Composable
private fun ClientCreateForm(
    state: CreateClientState,
    draft: ClientFormDraft,
    onUpdateDraft: (ClientFormDraft) -> Unit,
    onSave: (ClientCommand, String) -> Unit,
    onCancel: () -> Unit
) {
    val displayName = draft.displayName
    val relationship = draft.relationship
    val email = draft.email
    val phone = draft.phone
    val requestKey = draft.requestKey
    var expanded by remember { mutableStateOf(false) }
    val saving = state == CreateClientState.Saving
    val valid = displayName.trim().length in 1..80 && email.length <= 254 && phone.length <= 40

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Nowa kartoteka", style = MaterialTheme.typography.headlineMedium)
        Text("Uczestnik nie musi posiadać konta Moves.",
            style = MaterialTheme.typography.bodyMedium)
        OutlinedTextField(
            value = displayName,
            onValueChange = {
                onUpdateDraft(draft.copy(displayName = it, requestKey = UUID.randomUUID().toString()))
            },
            label = { Text("Imię i nazwisko lub nazwa kartoteki *") },
            singleLine = true, enabled = !saving,
            modifier = Modifier.fillMaxWidth()
        )
        Box {
            OutlinedButton(onClick = { expanded = true }, enabled = !saving) {
                Text("Relacja: ${relationshipLabel(relationship)}")
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                listOf(null, ClientRelationship.CLIENT, ClientRelationship.PATIENT, ClientRelationship.ATHLETE)
                    .forEach { option ->
                        DropdownMenuItem(
                            text = { Text(relationshipLabel(option)) },
                            onClick = {
                                onUpdateDraft(draft.copy(relationship = option, requestKey = UUID.randomUUID().toString()))
                                expanded = false
                            }
                        )
                    }
            }
        }
        OutlinedTextField(
            value = email, onValueChange = {
                onUpdateDraft(draft.copy(email = it, requestKey = UUID.randomUUID().toString()))
            },
            label = { Text("E-mail (opcjonalnie)") }, singleLine = true, enabled = !saving,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = phone, onValueChange = {
                onUpdateDraft(draft.copy(phone = it, requestKey = UUID.randomUUID().toString()))
            },
            label = { Text("Telefon (opcjonalnie)") }, singleLine = true, enabled = !saving,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            modifier = Modifier.fillMaxWidth()
        )
        if (state is CreateClientState.Failed) {
            Text(state.message, color = MaterialTheme.colorScheme.error)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onCancel, enabled = !saving) { Text("Anuluj") }
            Button(onClick = {
                onSave(
                    ClientCommand(
                        displayName = displayName.trim(),
                        relationshipContext = relationship,
                        email = email.trim().ifBlank { null },
                        phone = phone.trim().ifBlank { null }
                    ),
                    requestKey
                )
            }, enabled = valid && !saving) {
                Text(if (saving) "Zapisywanie…" else "Dodaj kartotekę")
            }
        }
    }
}
