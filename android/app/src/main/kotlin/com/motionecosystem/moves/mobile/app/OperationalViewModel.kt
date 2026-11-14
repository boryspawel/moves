package com.motionecosystem.moves.mobile.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.motionecosystem.moves.mobile.api.*
import com.motionecosystem.moves.mobile.repository.SpecialistRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface ReadState<out T> {
    data object Idle : ReadState<Nothing>
    data object Loading : ReadState<Nothing>
    data class Ready<T>(val value: T) : ReadState<T>
    data object Forbidden : ReadState<Nothing>
    data object Offline : ReadState<Nothing>
    data object Conflict : ReadState<Nothing>
    data object Error : ReadState<Nothing>
}

/** Online-only, in-memory projections. Never retain results across session changes or denied access. */
class OperationalViewModel(
    private val repository: SpecialistRepository,
    private val freshToken: suspend () -> String?,
    private val invalidateSession: () -> Unit
) : ViewModel() {
    private val mutableToday = MutableStateFlow<ReadState<TodayView>>(ReadState.Idle)
    val today = mutableToday.asStateFlow()
    private val mutableParticipants = MutableStateFlow<ReadState<List<ActiveParticipantView>>>(ReadState.Idle)
    val participants = mutableParticipants.asStateFlow()
    private val mutableWorklist = MutableStateFlow<ReadState<List<WorklistItemView>>>(ReadState.Idle)
    val worklist = mutableWorklist.asStateFlow()
    private var generation = 0

    fun loadToday() = read(mutableToday) { repository.today() }
    fun loadParticipants() = read(mutableParticipants) { repository.participants() }
    fun loadWorklist(context: ActingContext) = read(mutableWorklist) {
        val purpose = when (context) {
            ActingContext.TRAINER -> WorklistPurpose.PERFORMANCE_PLANNING
            ActingContext.PHYSIOTHERAPIST -> WorklistPurpose.FUNCTIONAL_RECOVERY
        }
        repository.worklist(context, purpose)
    }

    fun clearWorklist() {
        generation++
        mutableWorklist.value = ReadState.Idle
    }

    fun clear() {
        generation++
        mutableToday.value = ReadState.Idle
        mutableParticipants.value = ReadState.Idle
        mutableWorklist.value = ReadState.Idle
    }

    private fun <T> read(target: MutableStateFlow<ReadState<T>>, request: suspend () -> RemoteResult<T>) {
        if (target.value == ReadState.Loading) return
        val currentGeneration = generation
        target.value = ReadState.Loading
        viewModelScope.launch {
            if (freshToken().isNullOrBlank()) {
                if (currentGeneration == generation) {
                    clear()
                    invalidateSession()
                }
                return@launch
            }
            val result = request()
            if (currentGeneration != generation) return@launch
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
