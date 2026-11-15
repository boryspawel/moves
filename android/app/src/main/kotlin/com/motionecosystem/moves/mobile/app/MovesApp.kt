package com.motionecosystem.moves.mobile.app

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.motionecosystem.moves.mobile.BuildConfig
import com.motionecosystem.moves.mobile.api.*
import com.motionecosystem.moves.mobile.auth.OidcSession
import com.motionecosystem.moves.mobile.auth.SessionStatus
import com.motionecosystem.moves.mobile.repository.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

sealed interface ReadinessState {
    data object Loading : ReadinessState
    data class Empty(val message: String) : ReadinessState
    data class Error(val message: String) : ReadinessState
}

class ReadinessViewModel(
    private val tokenProvider: TokenProvider,
    private val repository: SpecialistRepository,
    private val onUnauthorized: () -> Unit = {}
) : ViewModel() {
    private val mutableState = MutableStateFlow<ReadinessState>(ReadinessState.Empty("Zaloguj się, aby sprawdzić połączenie."))
    val state: StateFlow<ReadinessState> = mutableState
    private var generation = 0
    fun clear() { generation++; mutableState.value = ReadinessState.Empty("Brak sesji.") }
    fun refresh() {
        if (tokenProvider.token().isNullOrBlank()) { clear(); return }
        val current = generation
        viewModelScope.launch {
            mutableState.value = ReadinessState.Loading
            val result = repository.identity()
            if (current != generation) return@launch
            mutableState.value = when (result) {
                is RemoteResult.Success -> ReadinessState.Empty("Połączono jako " + (result.value.subject ?: "użytkownik") + ".")
                is RemoteResult.Failure -> if (result.error is RemoteError.Unauthorized) {
                    onUnauthorized()
                    ReadinessState.Empty("Sesja wygasła. Zaloguj się ponownie.")
                } else ReadinessState.Error("Nie można odczytać sesji: " + result.error.javaClass.simpleName)
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
    val readiness = remember(repository) { ReadinessViewModel(session, repository, session::clear) }
    val operations = remember(repository) { OperationalViewModel(repository, session::freshAccessToken, session::clear) }
    val status by session.status.collectAsStateWithLifecycle()
    val readinessState by readiness.state.collectAsStateWithLifecycle()
    val professionalContext by operations.professionalContext.collectAsStateWithLifecycle()

    LaunchedEffect(status) {
        if (status == SessionStatus.SignedIn) {
            if (session.freshAccessToken() != null) {
                readiness.refresh()
                operations.revalidateAccess()
            } else {
                readiness.clear()
                operations.clear()
            }
        } else {
            readiness.clear()
            operations.clear()
        }
    }
    // Re-check after returning from background; never reveal stale participant data while offline.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        if (session.status.value == SessionStatus.SignedIn &&
            operations.professionalContext.value is ReadState.Ready) operations.revalidateAccess()
    }
    MaterialTheme {
        when (status) {
            SessionStatus.SignedIn -> when (val access = professionalContext) {
                is ReadState.Ready -> MovesNavigation(operations, readinessState, access.value, onLogout)
                else -> Column(Modifier.fillMaxSize().padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text("Moves", style = MaterialTheme.typography.headlineMedium)
                    Text(when (access) {
                        ReadState.Idle, ReadState.Loading -> "Sprawdzanie kontekstu zawodowego i dostępu…"
                        ReadState.Forbidden -> "Serwer odmówił dostępu. Sprawdź profil zawodowy, relację z uczestnikiem lub zgodę."
                        ReadState.Offline -> "Brak połączenia. Ze względów bezpieczeństwa ukryto dane uczestników."
                        else -> "Nie udało się potwierdzić dostępu. Dane uczestników są ukryte."
                    })
                    if (access != ReadState.Loading) {
                        OutlinedButton(onClick = operations::revalidateAccess) { Text("Sprawdź ponownie") }
                    }
                    TextButton(onClick = onLogout) { Text("Wyloguj") }
                }
            }
            else -> Column(Modifier.fillMaxSize().padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("Moves", style = MaterialTheme.typography.headlineMedium)
                when (val currentStatus = status) {
                    SessionStatus.SignedOut -> Button(onClick = onLogin) { Text("Zaloguj przez Keycloak") }
                    SessionStatus.Authorizing -> CircularProgressIndicator()
                    is SessionStatus.Failed -> {
                        Text(currentStatus.message, color = MaterialTheme.colorScheme.error)
                        Button(onClick = onLogin) { Text("Spróbuj ponownie") }
                    }
                    else -> Unit
                }
            }
        }
    }
}
