package com.motionecosystem.moves.mobile.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.motionecosystem.moves.mobile.api.*
import com.motionecosystem.moves.mobile.repository.SpecialistRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.UUID

sealed interface ReadState<out T> {
    data object Idle : ReadState<Nothing>
    data object Loading : ReadState<Nothing>
    data class Ready<T>(val value: T) : ReadState<T>
    data object Forbidden : ReadState<Nothing>
    data object Offline : ReadState<Nothing>
    data object Conflict : ReadState<Nothing>
    data object Error : ReadState<Nothing>
}

sealed interface ContextSelection {
    data class Participant(val participantId: UUID) : ContextSelection
    data class Appointment(val participantId: UUID?, val appointmentId: UUID) : ContextSelection
}

/** Online-only, in-memory projections. Never retain results across session changes or denied access. */
class OperationalViewModel(
    private val repository: SpecialistRepository,
    private val freshToken: suspend () -> String?,
    private val invalidateSession: () -> Unit
) : ViewModel() {
    private val mutableToday = MutableStateFlow<ReadState<TodayView>>(ReadState.Idle)
    val today = mutableToday.asStateFlow()
    private val mutableRequestedDate = MutableStateFlow<LocalDate?>(null)
    val requestedDate = mutableRequestedDate.asStateFlow()
    private var todayRequest = 0
    private val mutableParticipants = MutableStateFlow<ReadState<List<ActiveParticipantView>>>(ReadState.Idle)
    val participants = mutableParticipants.asStateFlow()
    private val mutableWorklist = MutableStateFlow<ReadState<List<WorklistItemView>>>(ReadState.Idle)
    val worklist = mutableWorklist.asStateFlow()
    private val mutableSelection = MutableStateFlow<ContextSelection?>(null)
    val selection = mutableSelection.asStateFlow()
    private var generation = 0

    fun selectParticipant(id: UUID) { mutableSelection.value = ContextSelection.Participant(id) }
    fun selectAppointment(participantId: UUID?, appointmentId: UUID) {
        mutableSelection.value = ContextSelection.Appointment(participantId, appointmentId)
    }

    fun chooseDate(date: LocalDate?) {
        mutableRequestedDate.value = date
        loadToday()
    }

    /** Replace in-flight day requests; only the newest response may update the screen. */
    fun loadToday() {
        val requestId = ++todayRequest
        val requested = mutableRequestedDate.value
        read(mutableToday, skipIfLoading = false, isCurrent = { requestId == todayRequest }) {
            repository.today(requested)
        }
    }
    fun loadParticipants() = read(mutableParticipants) { repository.participants() }
    fun loadWorklist(context: ActingContext) = read(mutableWorklist) {
        val purpose = when (context) {
            ActingContext.TRAINER -> WorklistPurpose.PERFORMANCE_PLANNING
            ActingContext.PHYSIOTHERAPIST -> WorklistPurpose.FUNCTIONAL_RECOVERY
        }
        repository.worklist(context, purpose)
    }

    /** Switching professional context also invalidates projections from the previous context. */
    fun clearWorklist() = clear()

    fun clear() {
        generation++
        todayRequest++
        mutableRequestedDate.value = null
        mutableToday.value = ReadState.Idle
        mutableParticipants.value = ReadState.Idle
        mutableWorklist.value = ReadState.Idle
        mutableSelection.value = null
    }

    private fun <T> read(
        target: MutableStateFlow<ReadState<T>>,
        skipIfLoading: Boolean = true,
        isCurrent: () -> Boolean = { true },
        request: suspend () -> RemoteResult<T>
    ) {
        if (skipIfLoading && target.value == ReadState.Loading) return
        val currentGeneration = generation
        target.value = ReadState.Loading
        viewModelScope.launch {
            if (freshToken().isNullOrBlank()) {
                if (currentGeneration == generation && isCurrent()) {
                    clear()
                    invalidateSession()
                }
                return@launch
            }
            val result = request()
            if (currentGeneration != generation || !isCurrent()) return@launch
            target.value = when (result) {
                is RemoteResult.Success -> ReadState.Ready(result.value)
                is RemoteResult.Failure -> when (result.error) {
                    RemoteError.Unauthorized -> {
                        clear()
                        invalidateSession()
                        ReadState.Idle
                    }
                    RemoteError.Forbidden -> {
                        clear()
                        ReadState.Forbidden
                    }
                    RemoteError.Conflict -> ReadState.Conflict
                    is RemoteError.Network -> ReadState.Offline
                    else -> ReadState.Error
                }
            }
        }
    }
}
