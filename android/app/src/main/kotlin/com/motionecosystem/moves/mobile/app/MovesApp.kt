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

    LaunchedEffect(status) {
        if (status == SessionStatus.SignedIn) {
            if (session.freshAccessToken() != null) readiness.refresh() else readiness.clear()
        } else {
            readiness.clear()
            operations.clear()
        }
    }
    MaterialTheme {
        when (status) {
            SessionStatus.SignedIn -> MovesNavigation(operations, readinessState, onLogout)
            else -> Column(Modifier.fillMaxSize().padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("Moves", style = MaterialTheme.typography.headlineMedium)
                when (status) {
                    SessionStatus.SignedOut -> Button(onClick = onLogin) { Text("Zaloguj przez Keycloak") }
                    SessionStatus.Authorizing -> CircularProgressIndicator()
                    is SessionStatus.Failed -> {
                        Text(status.message, color = MaterialTheme.colorScheme.error)
                        Button(onClick = onLogin) { Text("Spróbuj ponownie") }
                    }
                    else -> Unit
                }
            }
        }
    }
}
