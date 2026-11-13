package com.motionecosystem.moves.mobile.app

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.motionecosystem.moves.mobile.BuildConfig
import com.motionecosystem.moves.mobile.api.*
import com.motionecosystem.moves.mobile.auth.OidcSession
import com.motionecosystem.moves.mobile.auth.SessionStatus
import com.motionecosystem.moves.mobile.repository.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

private enum class Section(val label: String) { TODAY("Dzień"), PARTICIPANTS("Uczestnicy"), CASES("Sprawy") }
sealed interface ReadinessState { data object Loading : ReadinessState; data class Empty(val message: String) : ReadinessState; data class Error(val message: String) : ReadinessState }
class ReadinessViewModel(private val tokenProvider: TokenProvider, private val repository: SpecialistRepository) : ViewModel() {
    private val mutableState = MutableStateFlow<ReadinessState>(ReadinessState.Empty("Zaloguj się, aby sprawdzić połączenie."))
    val state: StateFlow<ReadinessState> = mutableState.asStateFlow()
    fun clear() { mutableState.value = ReadinessState.Empty("Brak sesji.") }
    fun refresh() {
        if (tokenProvider.token().isNullOrBlank()) { clear(); return }
        viewModelScope.launch {
            mutableState.value = ReadinessState.Loading
            mutableState.value = when (val result = repository.identity()) {
                is RemoteResult.Success -> ReadinessState.Empty("Połączono jako " + (result.value.subject ?: "użytkownik") + ". Ekrany biznesowe nie są jeszcze gotowe.")
                is RemoteResult.Failure -> ReadinessState.Error("Nie można odczytać sesji: " + result.error.javaClass.simpleName)
            }
        }
    }
}

@Composable
fun MovesApp(session: OidcSession, onLogin: () -> Unit, onLogout: () -> Unit) {
    val api = remember(session) { createMovesApi(BuildConfig.BASE_URL, session) }
    val repository = remember(api) {
        NetworkSpecialistRepository(api, RemoteCall(kotlinx.serialization.json.Json { ignoreUnknownKeys = true }))
    }
    val viewModel = remember(repository) { ReadinessViewModel(session, repository) }
    val authStatus by session.status.collectAsStateWithLifecycle()
    val readiness by viewModel.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var selected by remember { mutableStateOf(Section.TODAY) }
    LaunchedEffect(authStatus) {
        if (authStatus == SessionStatus.SignedIn) {
            if (session.freshAccessToken() != null) viewModel.refresh() else viewModel.clear()
        } else viewModel.clear()
    }
    MaterialTheme {
        Scaffold(bottomBar = {
            NavigationBar {
                Section.entries.forEach { section ->
                    NavigationBarItem(selected = selected == section, onClick = { selected = section },
                        icon = { Text(section.label.take(1)) }, label = { Text(section.label) })
                }
            }
        }) { padding ->
            Column(Modifier.padding(padding).padding(24.dp)) {
                Text(selected.label, style = MaterialTheme.typography.headlineMedium)
                Spacer(Modifier.height(16.dp))
                when (val status = authStatus) {
                    SessionStatus.SignedOut -> Button(onClick = onLogin) { Text("Zaloguj przez Keycloak") }
                    SessionStatus.Authorizing -> CircularProgressIndicator()
                    SessionStatus.SignedIn -> {
                        when (val current = readiness) {
                            ReadinessState.Loading -> CircularProgressIndicator()
                            is ReadinessState.Empty -> Text(current.message)
                            is ReadinessState.Error -> Text(current.message)
                        }
                        Spacer(Modifier.height(16.dp))
                        OutlinedButton(onClick = {
                            scope.launch {
                                if (session.freshAccessToken() != null) viewModel.refresh() else viewModel.clear()
                            }
                        }) { Text("Sprawdź sesję") }
                        TextButton(onClick = onLogout) { Text("Wyloguj") }
                    }
                    is SessionStatus.Failed -> {
                        Text(status.message)
                        Button(onClick = onLogin) { Text("Spróbuj ponownie") }
                    }
                }
            }
        }
    }
}
