package com.motionecosystem.moves.mobile.app

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.motionecosystem.moves.mobile.api.*

private enum class Tab(val route: String, val title: String) {
    DAY("day", "Dzień"), PARTICIPANTS("participants", "Uczestnicy")
}
private const val CONTEXT_ROUTE = "context"
private const val APPOINTMENT_ROUTE = "appointment"
private const val SCHEDULE_ROUTE = "schedule"

/** The shared system/UI Back policy. This restores S03 only for a visit entered from S03. */
internal fun participantToRestoreOnAppointmentBack(previousRoute: String?, selection: ContextSelection?): java.util.UUID? =
    if (previousRoute == CONTEXT_ROUTE) (selection as? ContextSelection.Appointment)?.participantId else null

/** A pending write must remain in the foreground; success navigates via its completion handler. */
internal fun canLeaveAppointment(state: AppointmentStartState): Boolean =
    state != AppointmentStartState.SAVING

internal fun canLeaveSchedule(state: ScheduleSaveState): Boolean =
    state != ScheduleSaveState.Saving && state !is ScheduleSaveState.Saved

/** A manual access recheck invalidates pending writes. Do not allow it to interrupt a
 * mutation or consume a just-saved result before its current route handles completion.
 * Explicit logout must remain available and still invalidates all local state.
 */
internal fun canManuallyRevalidate(
    appointment: AppointmentStartState,
    schedule: ScheduleSaveState,
    client: CreateClientState,
    interview: InterviewWriteState = InterviewWriteState.Idle,
    note: NoteWriteState = NoteWriteState.Idle
): Boolean = appointment != AppointmentStartState.SAVING &&
    canLeaveSchedule(schedule) &&
    client != CreateClientState.Saving && client !is CreateClientState.Saved &&
    interview !is InterviewWriteState.Saving && note !is NoteWriteState.Saving

@Composable
fun MovesNavigation(
    viewModel: OperationalViewModel,
    readiness: ReadinessState,
    actingContext: ActingContext,
    onLogout: () -> Unit,
    accessEpoch: Int = 0,
    modifier: Modifier = Modifier
) {
    val navigation = rememberNavController()
    val route = navigation.currentBackStackEntryAsState().value?.destination?.route
    val appointmentWrite by viewModel.appointmentStart.collectAsStateWithLifecycle()
    val scheduleWrite by viewModel.scheduleSave.collectAsStateWithLifecycle()
    val clientWrite by viewModel.createClientState.collectAsStateWithLifecycle()
    val interviewWrite by viewModel.interviewWrite.collectAsStateWithLifecycle()
    val noteWrite by viewModel.noteWrite.collectAsStateWithLifecycle()
    val mayRevalidate = canManuallyRevalidate(
        appointmentWrite, scheduleWrite, clientWrite, interviewWrite, noteWrite
    )
    // The authorization gate blanked all cached server projections. Restore only the
    // current route using fresh, authorized GETs; never replay mutations.
    LaunchedEffect(accessEpoch) {
        if (accessEpoch <= 1) return@LaunchedEffect
        when (route) {
            Tab.DAY.route -> viewModel.loadToday()
            Tab.PARTICIPANTS.route -> viewModel.loadClients()
            CONTEXT_ROUTE -> (viewModel.selection.value as? ContextSelection.Participant)
                ?.let { viewModel.loadWorkspace(it.participantId) }
            APPOINTMENT_ROUTE -> (viewModel.selection.value as? ContextSelection.Appointment)
                ?.let { viewModel.loadAppointment(it.appointmentId) }
            SCHEDULE_ROUTE -> {
                viewModel.loadClients()
                (viewModel.selection.value as? ContextSelection.Appointment)
                    ?.let { viewModel.loadAppointment(it.appointmentId) }
                viewModel.loadScheduleDay()
            }
            else -> if (VisitStage.entries.any { it.route == route }) {
                when (val current = viewModel.selection.value) {
                    is ContextSelection.Appointment -> viewModel.loadAppointment(current.appointmentId)
                    is ContextSelection.Participant -> viewModel.loadWorkspace(current.participantId)
                    null -> Unit
                }
            }
        }
    }
    Scaffold(modifier = modifier,
        topBar = {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text("Moves", style = MaterialTheme.typography.titleLarge)
                    Text(if (actingContext == ActingContext.TRAINER) "Trener" else "Fizjoterapeuta",
                        style = MaterialTheme.typography.labelSmall)
                    when (readiness) {
                        ReadinessState.Loading -> Text("Łączenie…", style = MaterialTheme.typography.labelSmall)
                        is ReadinessState.Error -> Text(readiness.message, color = MaterialTheme.colorScheme.error)
                        is ReadinessState.Empty -> if (readiness.message.startsWith("Połączono")) {
                            Text("Połączono", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
                Row {
                    TextButton(
                        onClick = {
                            // Check the latest state as well as the Compose enabled flag:
                            // a rapid second tap must not invalidate a newly started write.
                            if (canManuallyRevalidate(
                                    viewModel.appointmentStart.value,
                                    viewModel.scheduleSave.value,
                                    viewModel.createClientState.value,
                                    viewModel.interviewWrite.value,
                                    viewModel.noteWrite.value
                                )
                            ) viewModel.revalidateAccess()
                        },
                        enabled = mayRevalidate
                    ) { Text("Sprawdź dostęp") }
                    TextButton(onClick = onLogout) { Text("Wyloguj") }
                }
            }
        },
        bottomBar = {
            if (Tab.entries.any { it.route == route }) {
                NavigationBar {
                    Tab.entries.forEach { tab ->
                        NavigationBarItem(
                            selected = route == tab.route,
                            onClick = {
                                navigation.navigate(tab.route) {
                                    popUpTo(navigation.graph.findStartDestination().id) { saveState = false }
                                    launchSingleTop = true
                                    restoreState = false
                                }
                            },
                            icon = { Text(tab.title.take(1)) },
                            label = { Text(tab.title) }
                        )
                    }
                }
            }
        }
    ) { padding ->
        NavHost(navigation, startDestination = Tab.DAY.route, modifier = Modifier.padding(padding)) {
            composable(Tab.DAY.route) {
                TodaySection(
                    viewModel = viewModel,
                    onAppointment = { participantId, appointmentId ->
                        viewModel.selectAppointment(participantId, appointmentId)
                        navigation.navigate(APPOINTMENT_ROUTE)
                    },
                    onNewAppointment = {
                        viewModel.openNewSchedule()
                        navigation.navigate(SCHEDULE_ROUTE)
                    }
                )
            }
            composable(Tab.PARTICIPANTS.route) {
                ParticipantsSection(viewModel) { id ->
                    viewModel.selectParticipant(id)
                    navigation.navigate(CONTEXT_ROUTE)
                }
            }
            composable(CONTEXT_ROUTE) {
                val selection by viewModel.selection.collectAsStateWithLifecycle()
                when (val selected = selection) {
                    is ContextSelection.Participant -> ParticipantWorkspaceSection(
                        viewModel = viewModel,
                        participantId = selected.participantId,
                        onBack = { navigation.popBackStack() },
                        onAppointment = { appointmentId ->
                            viewModel.selectAppointment(selected.participantId, appointmentId)
                            navigation.navigate(APPOINTMENT_ROUTE)
                        },
                        onSchedule = {
                            viewModel.openNewSchedule(selected.participantId)
                            navigation.navigate(SCHEDULE_ROUTE)
                        },
                        onDocumentation = {
                            val participant = verifiedDocumentationParticipant(
                                viewModel.selection.value, viewModel.appointment.value, viewModel.workspace.value
                            )
                            if (participant == selected.participantId) {
                                viewModel.prepareParticipantDocumentationStage()
                                navigation.navigate(VisitStage.DOCUMENTATION.route)
                            }
                        }
                    )
                    else -> Column(Modifier.padding(20.dp)) {
                        Text("Brak dostępu do kartoteki albo zakończona sesja.")
                        OutlinedButton(onClick = { navigation.popBackStack() }) { Text("Wróć") }
                    }
                }
            }
            composable(APPOINTMENT_ROUTE) {
                val selection by viewModel.selection.collectAsStateWithLifecycle()
                val startState by viewModel.appointmentStart.collectAsStateWithLifecycle()
                val selected = selection as? ContextSelection.Appointment
                val goBack = {
                    if (canLeaveAppointment(startState)) {
                        participantToRestoreOnAppointmentBack(
                            navigation.previousBackStackEntry?.destination?.route,
                            viewModel.selection.value
                        )?.let { participantId ->
                            // The appointment selection replaced S03's participant selection.
                            // Restore S03 and its authorized GET before showing the old route.
                            viewModel.selectParticipant(participantId)
                            viewModel.loadWorkspace(participantId)
                        }
                        navigation.popBackStack()
                    }
                }
                BackHandler { goBack() }
                if (selected == null) {
                    Column(Modifier.padding(20.dp)) {
                        Text("Wybierz spotkanie z agendy.")
                        OutlinedButton(onClick = goBack) { Text("Wróć") }
                    }
                } else {
                    AppointmentSection(
                        viewModel = viewModel,
                        appointmentId = selected.appointmentId,
                        onEdit = {
                            if (viewModel.openEditSchedule()) navigation.navigate(SCHEDULE_ROUTE)
                        },
                        onStage = { stage ->
                            val visit = verifiedVisit(viewModel.selection.value, viewModel.appointment.value)
                            if (visit != null && canOpenVisitStage(stage, visit) &&
                                viewModel.appointmentStart.value != AppointmentStartState.SAVING) {
                                viewModel.prepareVisitStage()
                                navigation.navigate(stage.route) { launchSingleTop = true }
                            }
                        },
                        onBack = goBack
                    )
                }
            }
            VisitStage.entries.forEach { stage ->
                composable(stage.route) {
                    VisitStageSection(viewModel, stage, onBack = { navigation.popBackStack() })
                }
            }
            composable(SCHEDULE_ROUTE) {
                val target by viewModel.scheduleTarget.collectAsStateWithLifecycle()
                val saveState by viewModel.scheduleSave.collectAsStateWithLifecycle()
                val closeForm = {
                    if (canLeaveSchedule(saveState)) {
                        viewModel.closeSchedule()
                        when (val selected = viewModel.selection.value) {
                            is ContextSelection.Participant -> {
                                if (navigation.previousBackStackEntry?.destination?.route == CONTEXT_ROUTE) {
                                    viewModel.loadWorkspace(selected.participantId)
                                }
                            }
                            is ContextSelection.Appointment -> {
                                if (navigation.previousBackStackEntry?.destination?.route == APPOINTMENT_ROUTE) {
                                    viewModel.loadAppointment(selected.appointmentId)
                                }
                            }
                            null -> Unit
                        }
                        navigation.popBackStack()
                    }
                }
                BackHandler { closeForm() }
                if (target == null) {
                    Column(Modifier.padding(20.dp)) {
                        Text("Wróć do Dnia, aby wybrać termin.")
                        OutlinedButton(onClick = closeForm) { Text("Wróć") }
                    }
                } else {
                    ScheduleSection(viewModel, target!!,
                        onBack = closeForm,
                        onSaved = { saved ->
                            // A delayed result must not navigate from an obsolete form,
                            // after a route change, or after access invalidation.
                            if (navigation.currentBackStackEntry?.destination?.route == SCHEDULE_ROUTE &&
                                viewModel.scheduleTarget.value == target &&
                                (viewModel.scheduleSave.value as? ScheduleSaveState.Saved)?.appointment == saved &&
                                viewModel.professionalContext.value is ReadState.Ready
                            ) {
                                val wasEditing = target is ScheduleTarget.Edit
                                val id = saved.appointmentId
                                val participantId = saved.participantId
                                if (id != null && participantId != null) {
                                    viewModel.closeSchedule()
                                    viewModel.selectAppointment(participantId, id)
                                    if (wasEditing) {
                                        navigation.popBackStack()
                                        // S05 may still be composed; reload the new server detail.
                                        viewModel.loadAppointment(id)
                                    } else navigation.navigate(APPOINTMENT_ROUTE) {
                                        popUpTo(SCHEDULE_ROUTE) { inclusive = true }
                                    }
                                }
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun VisitStageSection(
    viewModel: OperationalViewModel,
    stage: VisitStage,
    onBack: () -> Unit
) {
    val selection by viewModel.selection.collectAsStateWithLifecycle()
    val appointment by viewModel.appointment.collectAsStateWithLifecycle()
    val workspace by viewModel.workspace.collectAsStateWithLifecycle()
    val interviews by viewModel.interviews.collectAsStateWithLifecycle()
    val interviewDraft by viewModel.interviewDraft.collectAsStateWithLifecycle()
    val interviewSelection by viewModel.interviewSelection.collectAsStateWithLifecycle()
    val interviewWrite by viewModel.interviewWrite.collectAsStateWithLifecycle()
    val notes by viewModel.notes.collectAsStateWithLifecycle()
    val noteDraft by viewModel.noteDraft.collectAsStateWithLifecycle()
    val noteSelection by viewModel.noteSelection.collectAsStateWithLifecycle()
    val noteWrite by viewModel.noteWrite.collectAsStateWithLifecycle()
    LaunchedEffect(stage, selection) {
        when (val current = selection) {
            is ContextSelection.Appointment -> viewModel.loadAppointment(current.appointmentId)
            is ContextSelection.Participant -> if (stage == VisitStage.DOCUMENTATION) {
                viewModel.loadWorkspace(current.participantId)
            }
            null -> Unit
        }
    }
    val refresh = {
        when (val current = viewModel.selection.value) {
            is ContextSelection.Appointment -> viewModel.loadAppointment(current.appointmentId)
            is ContextSelection.Participant -> if (stage == VisitStage.DOCUMENTATION) {
                viewModel.loadWorkspace(current.participantId)
            }
            null -> Unit
        }
    }
    val documentationParticipant = if (stage == VisitStage.DOCUMENTATION) {
        verifiedDocumentationParticipant(selection, appointment, workspace)
    } else null
    LaunchedEffect(stage, documentationParticipant) {
        if (documentationParticipant != null) viewModel.loadInterviews(documentationParticipant)
    }
    if (stage == VisitStage.DOCUMENTATION && documentationParticipant != null) {
        DocumentationScreen(
            interviews = interviews,
            notes = notes,
            selectedNoteId = noteSelection,
            noteDraft = noteDraft,
            noteWrite = noteWrite,
            onLoadNotes = { viewModel.loadNotes(documentationParticipant) },
            onNewNote = { viewModel.openNewNote(documentationParticipant) },
            onOpenNote = { viewModel.openNote(documentationParticipant, it) },
            onCloseNote = viewModel::closeNote,
            onChangeNote = viewModel::changeNoteDraft,
            onCreateNote = viewModel::createNote,
            onUpdateNote = viewModel::updateNote,
            onFinaliseNote = viewModel::finaliseNote,
            onArchiveNote = viewModel::archiveNote,
            onReconcileNote = viewModel::reconcileNote,
            selectedInterviewId = interviewSelection,
            draft = interviewDraft,
            writeState = interviewWrite,
            onStartInterview = { viewModel.startInterview(documentationParticipant) },
            onSaveInterview = viewModel::saveInterviewDraft,
            onCompleteInterview = viewModel::completeInterview,
            onReconcileInterview = viewModel::reconcileInterviewVersion,
            onOpenInterview = { viewModel.openInterview(documentationParticipant, it) },
            onCloseInterview = viewModel::closeInterview,
            onUpdateText = viewModel::updateInterviewText,
            onUpdateSelections = viewModel::updateInterviewSelections,
            onRefresh = { viewModel.loadInterviews(documentationParticipant) },
            onBack = onBack
            // On-device recognizer is a separate feature. No cloud fallback or mic permission.
        )
    } else {
        VisitStageScreen(
            stage = stage, selection = selection, appointment = appointment, workspace = workspace,
            onRefresh = refresh, onBack = onBack
        )
    }
}

@Composable
private fun ScheduleSection(
    viewModel: OperationalViewModel,
    target: ScheduleTarget,
    onBack: () -> Unit,
    onSaved: (AppointmentView) -> Unit
) {
    val day by viewModel.scheduleDay.collectAsStateWithLifecycle()
    val chosenDate by viewModel.scheduleDate.collectAsStateWithLifecycle()
    val resolvingDate by viewModel.scheduleDateResolving.collectAsStateWithLifecycle()
    val save by viewModel.scheduleSave.collectAsStateWithLifecycle()
    val draft by viewModel.scheduleDraft.collectAsStateWithLifecycle()
    val clients by viewModel.clients.collectAsStateWithLifecycle()
    val appointment by viewModel.appointment.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel, target) {
        if (viewModel.clients.value == ReadState.Idle) viewModel.loadClients()
    }
    LaunchedEffect(target, resolvingDate, day, appointment) {
        if (target is ScheduleTarget.Edit && resolvingDate &&
            day is ReadState.Ready && appointment is ReadState.Ready) {
            viewModel.resolveEditScheduleDate((day as ReadState.Ready<TodayView>).value)
        }
    }
    val access by viewModel.professionalContext.collectAsStateWithLifecycle()
    val completed = save as? ScheduleSaveState.Saved
    LaunchedEffect(target, completed, access) {
        // Do not navigate a hidden screen while its authorization is being
        // revalidated, or after a different form replaced the original target.
        if (access is ReadState.Ready && completed != null &&
            viewModel.scheduleTarget.value == target && viewModel.scheduleSave.value == completed) {
            onSaved(completed.appointment)
        }
    }
    if (draft == null) return
    ScheduleScreen(
        target = target, scheduleDay = day, requestedDate = chosenDate, clients = clients,
        appointment = appointment, saveState = save, draft = draft!!,
        dateResolving = resolvingDate,
        onDraftChange = viewModel::changeScheduleDraft,
        onChooseDate = viewModel::chooseScheduleDate,
        onRefresh = viewModel::loadScheduleDay,
        onSave = viewModel::saveSchedule,
        onBack = onBack
    )
}

@Composable
private fun AppointmentSection(
    viewModel: OperationalViewModel,
    appointmentId: java.util.UUID,
    onEdit: () -> Unit,
    onStage: (VisitStage) -> Unit,
    onBack: () -> Unit
) {
    val appointment by viewModel.appointment.collectAsStateWithLifecycle()
    val startState by viewModel.appointmentStart.collectAsStateWithLifecycle()
    val today by viewModel.today.collectAsStateWithLifecycle()
    val clients by viewModel.clients.collectAsStateWithLifecycle()
    LaunchedEffect(appointmentId) { viewModel.loadAppointment(appointmentId) }
    val detail = (appointment as? ReadState.Ready)?.value
    val participantId = detail?.participantId
    val todayView = (today as? ReadState.Ready)?.value
    val label = todayView?.appointments.orEmpty()
        .firstOrNull { it.appointmentId == appointmentId }?.participantLabel
        ?: (clients as? ReadState.Ready)?.value.orEmpty()
            .firstOrNull { it.participantId == participantId }?.displayName
    AppointmentScreen(
        state = appointment,
        startState = startState,
        participantLabel = label,
        timeZoneId = todayView?.timeZoneId,
        onRefresh = { viewModel.loadAppointment(appointmentId) },
        onStart = { viewModel.startAppointment(appointmentId) },
        onEdit = onEdit,
        onExecution = { onStage(VisitStage.EXECUTION) },
        onDocumentation = { onStage(VisitStage.DOCUMENTATION) },
        onCloseout = { onStage(VisitStage.CLOSEOUT) },
        onBack = onBack
    )
}

@Composable
private fun ParticipantWorkspaceSection(
    viewModel: OperationalViewModel,
    participantId: java.util.UUID,
    onBack: () -> Unit,
    onAppointment: (java.util.UUID) -> Unit,
    onSchedule: () -> Unit,
    onDocumentation: () -> Unit
) {
    val workspace by viewModel.workspace.collectAsStateWithLifecycle()
    val timeline by viewModel.timeline.collectAsStateWithLifecycle()
    LaunchedEffect(participantId) {
        if (viewModel.workspace.value == ReadState.Idle) viewModel.loadWorkspace(participantId)
    }
    LaunchedEffect(participantId, workspace) {
        if (workspace is ReadState.Ready && viewModel.timeline.value == ReadState.Idle) {
            viewModel.loadTimeline(participantId)
        }
    }
    WorkspaceScreen(
        workspace = workspace,
        timeline = timeline,
        onRefresh = { viewModel.loadWorkspace(participantId) },
        onRetryTimeline = { viewModel.loadTimeline(participantId) },
        onMoreTimeline = { viewModel.loadMoreTimeline(participantId) },
        onOpenAppointment = onAppointment,
        onSchedule = onSchedule,
        onDocumentation = onDocumentation,
        onBack = onBack
    )
}

@Composable
private fun TodaySection(
    viewModel: OperationalViewModel,
    onAppointment: (java.util.UUID?, java.util.UUID) -> Unit,
    onNewAppointment: () -> Unit
) {
    val state by viewModel.today.collectAsStateWithLifecycle()
    val requestedDate by viewModel.requestedDate.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) { viewModel.loadToday() }
    TodayScreen(state, requestedDate, viewModel::chooseDate, viewModel::loadToday, onAppointment, onNewAppointment)
}

@Composable
private fun ParticipantsSection(viewModel: OperationalViewModel, onParticipant: (java.util.UUID) -> Unit) {
    val clients by viewModel.clients.collectAsStateWithLifecycle()
    val createState by viewModel.createClientState.collectAsStateWithLifecycle()
    val authorized by viewModel.professionalContext.collectAsStateWithLifecycle()
    val draft by viewModel.clientDraft.collectAsStateWithLifecycle()
    val query by viewModel.clientQuery.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) { if (viewModel.clients.value == ReadState.Idle) viewModel.loadClients() }
    // State-based completion survives rotation; no retained coroutine captures an old NavController.
    LaunchedEffect(createState, authorized) {
        if (authorized !is ReadState.Ready) return@LaunchedEffect
        val saved = createState as? CreateClientState.Saved ?: return@LaunchedEffect
        viewModel.resetClientForm()
        onParticipant(saved.participantId)
    }
    ParticipantsScreen(
        state = clients, creation = createState, draft = draft, query = query,
        onSearch = viewModel::searchClients,
        onUpdateDraft = viewModel::updateClientForm,
        onOpenCreate = viewModel::openClientForm,
        onRefresh = viewModel::loadClients,
        onCreate = viewModel::createClient,
        onCancel = viewModel::resetClientForm,
        onOpen = onParticipant
    )
}

@Composable
internal fun <T> ReadPanel(
    state: ReadState<T>, onRetry: () -> Unit, isEmpty: (T) -> Boolean,
    content: @Composable (T) -> Unit
) {
    when (state) {
        ReadState.Idle, ReadState.Loading -> Box(Modifier.padding(24.dp)) { CircularProgressIndicator() }
        ReadState.Forbidden -> FailureNotice("Brak uprawnień do tych danych.", onRetry)
        ReadState.Offline -> FailureNotice("Brak połączenia z serwerem.", onRetry)
        ReadState.Conflict -> FailureNotice("Nie można odczytać danych w aktualnym stanie profilu.", onRetry)
        ReadState.Error -> FailureNotice("Nie udało się pobrać danych.", onRetry)
        is ReadState.Ready -> if (isEmpty(state.value)) {
            Text("Brak elementów do wyświetlenia.", modifier = Modifier.padding(24.dp))
        } else content(state.value)
    }
}

@Composable
private fun FailureNotice(message: String, onRetry: () -> Unit) {
    Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(message)
        OutlinedButton(onClick = onRetry) { Text("Spróbuj ponownie") }
    }
}
