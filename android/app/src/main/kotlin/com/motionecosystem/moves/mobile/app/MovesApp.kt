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
import com.motionecosystem.moves.mobile.repository.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

private enum class Section(val label: String) { TODAY("Dzień"), PARTICIPANTS("Uczestnicy"), CASES("Sprawy") }
sealed interface ReadinessState { data object Loading : ReadinessState; data class Empty(val message: String) : ReadinessState; data class Error(val message: String) : ReadinessState }
class ReadinessViewModel(private val tokenProvider: TokenProvider, private val repository: SpecialistRepository) : ViewModel() {
    private val mutableState = MutableStateFlow<ReadinessState>(ReadinessState.Empty("Logowanie zostanie podłączone w kolejnym etapie."))
    val state: StateFlow<ReadinessState> = mutableState.asStateFlow()
    fun refresh() { val token = tokenProvider.token(); if (token.isNullOrBlank()) { mutableState.value = ReadinessState.Empty("Brak sesji. Logowanie OIDC nie jest jeszcze częścią M1."); return }; viewModelScope.launch { mutableState.value = ReadinessState.Loading; mutableState.value = when (val result = repository.identity()) { is RemoteResult.Success -> ReadinessState.Empty("Połączono jako ${result.value.subject ?: "użytkownik"}. Ekrany biznesowe nie są jeszcze gotowe."); is RemoteResult.Failure -> ReadinessState.Error("Nie można odczytać sesji: ${result.error.javaClass.simpleName}") } } }
}
@Composable fun MovesApp() {
    val api = remember { createMovesApi(BuildConfig.BASE_URL, NoTokenProvider) }
    val repository = remember { NetworkSpecialistRepository(api, RemoteCall(kotlinx.serialization.json.Json { ignoreUnknownKeys = true })) }
    val viewModel = remember { ReadinessViewModel(NoTokenProvider, repository) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    var selected by remember { mutableStateOf(Section.TODAY) }
    MaterialTheme { Scaffold(bottomBar = { NavigationBar { Section.entries.forEach { section -> NavigationBarItem(selected = selected == section, onClick = { selected = section }, icon = { Text(section.label.take(1)) }, label = { Text(section.label) }) } } }) { padding -> Column(Modifier.padding(padding).padding(24.dp)) { Text(selected.label, style = MaterialTheme.typography.headlineMedium); Spacer(Modifier.height(16.dp)); when (state) { ReadinessState.Loading -> CircularProgressIndicator(); is ReadinessState.Empty -> Text((state as ReadinessState.Empty).message); is ReadinessState.Error -> { Text((state as ReadinessState.Error).message); Button(onClick = viewModel::refresh) { Text("Ponów") } } }; Spacer(Modifier.height(16.dp)); if (state !is ReadinessState.Loading) OutlinedButton(onClick = viewModel::refresh) { Text("Sprawdź sesję") } } } }
}
