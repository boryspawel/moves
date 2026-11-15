package com.motionecosystem.moves.mobile.app

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.motionecosystem.moves.mobile.api.*
import java.util.UUID

/** These are read-only navigation shells. Their writes are intentionally deferred to M4/M5. */
internal enum class VisitStage(val route: String, val title: String) {
    EXECUTION("visit-execution", "Realizacja sesji"),
    DOCUMENTATION("visit-documentation", "Dokumentacja"),
    CLOSEOUT("visit-closeout", "Zakończenie")
}

internal data class VerifiedVisit(
    val appointmentId: UUID,
    val participantId: UUID,
    val plannedSessionId: UUID?,
    val status: AppointmentStatus
)

/** An in-memory route is never an authorization grant; a fresh appointment GET must match both IDs. */
internal fun verifiedVisit(
    selection: ContextSelection?,
    appointment: ReadState<AppointmentView>
): VerifiedVisit? {
    val selected = selection as? ContextSelection.Appointment ?: return null
    val detail = (appointment as? ReadState.Ready)?.value ?: return null
    val participant = detail.participantId ?: return null
    if (detail.appointmentId != selected.appointmentId ||
        (selected.participantId != null && selected.participantId != participant)) return null
    return VerifiedVisit(detail.appointmentId ?: return null, participant,
        detail.plannedSessionId, detail.status ?: return null)
}

internal fun canOpenVisitStage(stage: VisitStage, visit: VerifiedVisit): Boolean = when (stage) {
    VisitStage.EXECUTION -> visit.status == AppointmentStatus.IN_PROGRESS && visit.plannedSessionId != null
    VisitStage.DOCUMENTATION -> visit.status !in setOf(AppointmentStatus.CANCELLED, AppointmentStatus.NO_SHOW)
    VisitStage.CLOSEOUT -> visit.status == AppointmentStatus.IN_PROGRESS
}

/** S07 may also be opened directly from an authorized participant workspace (S03). */
internal fun verifiedDocumentationParticipant(
    selection: ContextSelection?,
    appointment: ReadState<AppointmentView>,
    workspace: ReadState<WorkspaceView>
): UUID? = when (selection) {
    is ContextSelection.Appointment -> verifiedVisit(selection, appointment)
        ?.takeIf { canOpenVisitStage(VisitStage.DOCUMENTATION, it) }?.participantId
    is ContextSelection.Participant -> {
        val participant = (workspace as? ReadState.Ready)?.value?.participant?.participantId
        participant?.takeIf { it == selection.participantId }
    }
    null -> null
}

@Composable
internal fun VisitStageScreen(
    stage: VisitStage,
    selection: ContextSelection?,
    appointment: ReadState<AppointmentView>,
    workspace: ReadState<WorkspaceView>,
    onRefresh: () -> Unit,
    onBack: () -> Unit
) {
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(onClick = onBack) { Text("Wróć do poprzedniego ekranu") }
            TextButton(onClick = onRefresh) { Text("Sprawdź dostęp") }
        }
        when (selection) {
            is ContextSelection.Participant -> {
                if (stage == VisitStage.DOCUMENTATION) {
                    ReadPanel(workspace, onRefresh, { false }) {
                        if (verifiedDocumentationParticipant(selection, appointment, workspace) != null) {
                            StageBody(stage, "Kartoteka uczestnika")
                        } else {
                            AccessFailure()
                        }
                    }
                } else AccessFailure()
            }
            is ContextSelection.Appointment -> {
                ReadPanel(appointment, onRefresh, { false }) {
                    val verified = verifiedVisit(selection, appointment)
                    if (verified == null || !canOpenVisitStage(stage, verified)) {
                        AccessFailure()
                    } else {
                        StageBody(stage, when (verified.status) {
                            AppointmentStatus.IN_PROGRESS -> "Spotkanie w toku"
                            AppointmentStatus.COMPLETED -> "Spotkanie zakończone"
                            else -> "Spotkanie"
                        })
                    }
                }
            }
            null -> AccessFailure()
        }
    }
}

@Composable
private fun AccessFailure() {
    Text("Brak aktualnie potwierdzonego kontekstu. Wróć do spotkania lub kartoteki.",
        modifier = Modifier.padding(20.dp))
}

@Composable
private fun StageBody(stage: VisitStage, contextLabel: String) {
    Column(Modifier.fillMaxWidth().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(stage.title, style = MaterialTheme.typography.headlineMedium)
        Text(contextLabel, style = MaterialTheme.typography.titleMedium)
        Text(when (stage) {
            VisitStage.EXECUTION ->
                "Powiązana sesja została potwierdzona. Rejestrowanie wykonania ćwiczeń będzie dostępne w MOB-050."
            VisitStage.DOCUMENTATION ->
                "Kontekst uczestnika został zweryfikowany. Wywiady i notatki zostaną podłączone w MOB-040/041."
            VisitStage.CLOSEOUT ->
                "Spotkanie jest w toku. Kontrola kompletności i zakończenie będą dostępne w MOB-051."
        })
        Text("Ten ekran nie zapisuje ani nie wysyła danych. Powrót nie zmienia stanu spotkania.",
            style = MaterialTheme.typography.bodySmall)
    }
}
