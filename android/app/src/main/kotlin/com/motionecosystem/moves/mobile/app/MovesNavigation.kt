package com.motionecosystem.moves.mobile.app

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
    DAY("day", "Dzień"), PARTICIPANTS("participants", "Uczestnicy"), CASES("cases", "Sprawy")
}
private const val CONTEXT_ROUTE = "context"

@Composable
fun MovesNavigation(viewModel: OperationalViewModel, readiness: ReadinessState, onLogout: () -> Unit) {
    val navigation = rememberNavController()
    val route = navigation.currentBackStackEntryAsState().value?.destination?.route
    Scaffold(
        topBar = {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text("Moves", style = MaterialTheme.typography.titleLarge)
                    when (readiness) {
                        ReadinessState.Loading -> Text("Łączenie…", style = MaterialTheme.typography.labelSmall)
                        is ReadinessState.Error -> Text(readiness.message, color = MaterialTheme.colorScheme.error)
                        is ReadinessState.Empty -> if (readiness.message.startsWith("Połączono")) {
                            Text("Połączono", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
                TextButton(onClick = onLogout) { Text("Wyloguj") }
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
                        navigation.navigate(CONTEXT_ROUTE)
                    },
                    onCases = { navigation.navigate(Tab.CASES.route) { launchSingleTop = true } }
                )
            }
            composable(Tab.PARTICIPANTS.route) {
                ParticipantsSection(viewModel) { id ->
                    viewModel.selectParticipant(id)
                    navigation.navigate(CONTEXT_ROUTE)
                }
            }
            composable(Tab.CASES.route) {
                CasesSection(viewModel) { id ->
                    viewModel.selectParticipant(id)
                    navigation.navigate(CONTEXT_ROUTE)
                }
            }
            composable(CONTEXT_ROUTE) {
                val selection by viewModel.selection.collectAsStateWithLifecycle()
                Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(
                        when (selection) {
                            is ContextSelection.Appointment -> "Spotkanie"
                            is ContextSelection.Participant -> "Kartoteka uczestnika"
                            null -> "Brak kontekstu"
                        },
                        style = MaterialTheme.typography.headlineMedium
                    )
                    Text(
                        when (selection) {
                            is ContextSelection.Appointment -> "Szczegóły i rozpoczęcie spotkania pojawią się w M3."
                            is ContextSelection.Participant -> "Szczegóły kartoteki i historia pojawią się w M2."
                            null -> "Wybierz element ponownie z listy."
                        }
                    )
                    OutlinedButton(onClick = { navigation.popBackStack() }) { Text("Wróć") }
                }
            }
        }
    }
}

@Composable
private fun TodaySection(
    viewModel: OperationalViewModel,
    onAppointment: (java.util.UUID?, java.util.UUID) -> Unit,
    onCases: () -> Unit
) {
    val state by viewModel.today.collectAsStateWithLifecycle()
    val requestedDate by viewModel.requestedDate.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) { if (viewModel.today.value == ReadState.Idle) viewModel.loadToday() }
    TodayScreen(state, requestedDate, viewModel::chooseDate, viewModel::loadToday, onAppointment, onCases)
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
private fun CasesSection(viewModel: OperationalViewModel, onParticipant: (java.util.UUID) -> Unit) {
    var context by remember { mutableStateOf<ActingContext?>(null) }
    val state by viewModel.worklist.collectAsStateWithLifecycle()
    LaunchedEffect(context) {
        viewModel.clearWorklist()
        context?.let(viewModel::loadWorklist)
    }
    Column {
        Text("Sprawy", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(16.dp))
        Text("Kontekst zawodowy", modifier = Modifier.padding(horizontal = 16.dp))
        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = context == ActingContext.TRAINER,
                onClick = { context = ActingContext.TRAINER }, label = { Text("Trener") })
            FilterChip(selected = context == ActingContext.PHYSIOTHERAPIST,
                onClick = { context = ActingContext.PHYSIOTHERAPIST }, label = { Text("Fizjoterapeuta") })
        }
        if (context == null) Text("Wybierz rolę, w której pracujesz.", modifier = Modifier.padding(16.dp))
        else {
            val selected = context!!
            TextButton(onClick = { viewModel.loadWorklist(selected) }, modifier = Modifier.padding(start = 8.dp)) {
                Text("Odśwież")
            }
            ReadPanel(state, { viewModel.loadWorklist(selected) }, { it.isEmpty() }) { items ->
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items.forEach { item ->
                        val label = listOfNotNull(item.category, item.priority, item.status).joinToString(" • ")
                        val id = item.participantId
                        if (id != null) OutlinedButton(onClick = { onParticipant(id) },
                            modifier = Modifier.fillMaxWidth()) { Text(label.ifBlank { "Sprawa" }) }
                        else Text(label.ifBlank { "Sprawa" })
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeading(title: String, onRefresh: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween) {
        Text(title, style = MaterialTheme.typography.headlineMedium)
        TextButton(onClick = onRefresh) { Text("Odśwież") }
    }
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
