package com.motionecosystem.moves.mobile.app

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

@Composable
fun MovesNavigation(viewModel: OperationalViewModel, readiness: ReadinessState, actingContext: ActingContext, onLogout: () -> Unit) {
    val navigation = rememberNavController()
    val route = navigation.currentBackStackEntryAsState().value?.destination?.route
    Scaffold(
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
                    TextButton(onClick = viewModel::revalidateAccess) { Text("Sprawdź dostęp") }
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
                val selected = selection as? ContextSelection.Appointment
                if (selected == null) {
                    Column(Modifier.padding(20.dp)) {
                        Text("Wybierz spotkanie z agendy.")
                        OutlinedButton(onClick = { navigation.popBackStack() }) { Text("Wróć") }
                    }
                } else {
                    AppointmentSection(
                        viewModel = viewModel,
                        appointmentId = selected.appointmentId,
                        onEdit = {
                            if (viewModel.openEditSchedule()) navigation.navigate(SCHEDULE_ROUTE)
                        },
                        onBack = {
                            if (navigation.previousBackStackEntry?.destination?.route == CONTEXT_ROUTE) {
                                selected.participantId?.let(viewModel::selectParticipant)
                            }
                            navigation.popBackStack()
                        }
                    )
                }
            }
            composable(SCHEDULE_ROUTE) {
                val target by viewModel.scheduleTarget.collectAsStateWithLifecycle()
                if (target == null) {
                    Text("Wróć do Dnia, aby wybrać termin.", modifier = Modifier.padding(20.dp))
                } else {
                    ScheduleSection(viewModel, target!!,
                        onBack = { viewModel.closeSchedule(); navigation.popBackStack() },
                        onSaved = { saved ->
                            val wasEditing = target is ScheduleTarget.Edit
                            val id = saved.appointmentId
                            val participantId = saved.participantId
                            if (id != null && participantId != null) {
                                viewModel.closeSchedule()
                                viewModel.selectAppointment(participantId, id)
                                if (wasEditing) navigation.popBackStack()
                                else navigation.navigate(APPOINTMENT_ROUTE) {
                                    popUpTo(SCHEDULE_ROUTE) { inclusive = true }
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
private fun ScheduleSection(
    viewModel: OperationalViewModel,
    target: ScheduleTarget,
    onBack: () -> Unit,
    onSaved: (AppointmentView) -> Unit
) {
    val day by viewModel.scheduleDay.collectAsStateWithLifecycle()
    val chosenDate by viewModel.scheduleDate.collectAsStateWithLifecycle()
    val save by viewModel.scheduleSave.collectAsStateWithLifecycle()
    val clients by viewModel.clients.collectAsStateWithLifecycle()
    val appointment by viewModel.appointment.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel, target) {
        if (viewModel.clients.value == ReadState.Idle) viewModel.loadClients()
    }
    val completed = save as? ScheduleSaveState.Saved
    LaunchedEffect(completed) {
        completed?.let { onSaved(it.appointment) }
    }
    ScheduleScreen(
        target = target, scheduleDay = day, requestedDate = chosenDate, clients = clients,
        appointment = appointment, saveState = save,
        onChooseDate = viewModel::scheduleDate,
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
        onBack = onBack
    )
}

@Composable
private fun ParticipantWorkspaceSection(
    viewModel: OperationalViewModel,
    participantId: java.util.UUID,
    onBack: () -> Unit,
    onAppointment: (java.util.UUID) -> Unit,
    onSchedule: () -> Unit
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
    LaunchedEffect(viewModel) { if (viewModel.clients.value == ReadState.Idle) viewModel.loadClients() }
    ParticipantsScreen(
        state = clients,
        creation = createState,
        onRefresh = viewModel::loadClients,
        onCreate = { command, key, onCreated -> viewModel.createClient(command, key, onCreated) },
        onCancel = viewModel::resetClientForm,
        onOpen = onParticipant,
        onResetCreate = viewModel::resetClientForm
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
