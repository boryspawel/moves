package com.motionecosystem.moves.mobile.app

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
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
    // Activity ViewModelStore survives configuration changes. Factories run only for a new
    // owner, so ongoing coroutines and RAM-only drafts are not orphaned on rotation.
    val readiness: ReadinessViewModel = viewModel(factory = remember(session, repository) {
        viewModelFactory { initializer { ReadinessViewModel(session, repository, session::clear) } }
    })
    val operations: OperationalViewModel = viewModel(factory = remember(session, repository) {
        viewModelFactory { initializer {
            OperationalViewModel(repository, session::freshAccessToken, session::clear)
        } }
    })
    val status by session.status.collectAsStateWithLifecycle()
    val readinessState by readiness.state.collectAsStateWithLifecycle()
    val professionalContext by operations.professionalContext.collectAsStateWithLifecycle()
    val accessEpoch by operations.accessEpoch.collectAsStateWithLifecycle()
    // A retained ViewModel can still hold the previously authorized profile after rotation.
    // Never render it until a NEW access check succeeds in this Activity composition.
    val requiredAccessEpoch = remember(operations) { operations.accessEpoch.value + 1 }
    val lastVerifiedRole = remember(session) { mutableStateOf<ActingContext?>(null) }
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val isAuthorized = status == SessionStatus.SignedIn && accessEpoch >= requiredAccessEpoch &&
        professionalContext is ReadState.Ready && lastVerifiedRole.value ==
        (professionalContext as ReadState.Ready<ActingContext>).value
    LaunchedEffect(isAuthorized) {
        if (!isAuthorized) {
            focusManager.clearFocus(force = true)
            keyboard?.hide()
        }
    }

    LaunchedEffect(status, professionalContext) {
        if (status != SessionStatus.SignedIn || professionalContext == ReadState.Forbidden) {
            lastVerifiedRole.value = null
        } else if (professionalContext is ReadState.Ready) {
            lastVerifiedRole.value = (professionalContext as ReadState.Ready<ActingContext>).value
        }
    }

    LaunchedEffect(status) {
        if (status == SessionStatus.SignedIn) {
            readiness.clear()
            // The ViewModel owns the access gate and distinguishes temporary OIDC outages
            // from revoked sessions. Never sign out merely because Keycloak is offline.
            operations.revalidateAccess()
        } else {
            readiness.clear()
            operations.clear()
        }
    }
    LaunchedEffect(status, accessEpoch) {
        // The new token was refreshed and the specialist profile was authorized.
        if (status == SessionStatus.SignedIn && accessEpoch > 0 &&
            operations.professionalContext.value is ReadState.Ready) readiness.refresh()
    }
    // Re-check after returning from background; never reveal stale participant data while offline.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        if (session.status.value == SessionStatus.SignedIn &&
            operations.professionalContext.value is ReadState.Ready) operations.revalidateAccess()
    }
    MaterialTheme {
        when (status) {
            SessionStatus.SignedIn -> {
                val role = lastVerifiedRole.value
                val granted = accessEpoch >= requiredAccessEpoch &&
                    professionalContext is ReadState.Ready &&
                    (professionalContext as ReadState.Ready<ActingContext>).value == role
                Box(Modifier.fillMaxSize()) {
                    // Keep the NavHost and its Compose draft fields alive while gated.
                    // Hidden routes are not interactive or exposed to accessibility services.
                    if (role != null) {
                        key(role) {
                            MovesNavigation(
                                operations, readinessState, role, onLogout,
                                accessEpoch = accessEpoch,
                                modifier = if (granted) Modifier
                                    else Modifier.alpha(0f).clearAndSetSemantics { }
                            )
                        }
                    }
                    if (!granted) {
                        BackHandler { } // A denied/unfinished check must not navigate stale routes.
                        // Separate blocker is behind the gate controls, but above the hidden NavHost.
                        Box(
                            Modifier.fillMaxSize()
                                .background(MaterialTheme.colorScheme.surface)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null
                                ) { }
                        )
                        Column(
                            Modifier.fillMaxSize().padding(24.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Text("Moves", style = MaterialTheme.typography.headlineMedium)
                            Text(when (professionalContext) {
                                ReadState.Idle, ReadState.Loading ->
                                    "Sprawdzanie kontekstu zawodowego i dostępu…"
                                ReadState.Forbidden ->
                                    "Serwer odmówił dostępu. Sprawdź profil zawodowy, relację z uczestnikiem lub zgodę."
                                ReadState.Offline ->
                                    "Brak połączenia. Dane uczestników są ukryte do czasu potwierdzenia dostępu."
                                else -> "Nie udało się potwierdzić dostępu. Dane uczestników są ukryte."
                            })
                            if (professionalContext != ReadState.Loading) {
                                OutlinedButton(onClick = operations::revalidateAccess) {
                                    Text("Sprawdź ponownie")
                                }
                            }
                            TextButton(onClick = onLogout) { Text("Wyloguj") }
                        }
                    }
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
