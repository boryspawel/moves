package com.motionecosystem.moves.mobile.app

import com.motionecosystem.moves.mobile.api.*
import com.motionecosystem.moves.mobile.repository.SpecialistRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReadinessViewModelTest {
    @Test fun missingTokenDoesNotInvokeRepository() {
        val repository = RecordingRepository()
        val viewModel = ReadinessViewModel(NoTokenProvider, repository)
        viewModel.refresh()
        assertEquals(0, repository.identityCalls)
        assertTrue(viewModel.state.value is ReadinessState.Empty)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test fun unauthorizedInvalidatesLocalSession() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val repository = RecordingRepository()
            repository.result = RemoteResult.Failure(RemoteError.Unauthorized)
            var invalidated = false
            val vm = ReadinessViewModel(TokenProvider { "access" }, repository) { invalidated = true }
            vm.refresh()
            advanceUntilIdle()
            assertTrue(invalidated)
            assertTrue(vm.state.value is ReadinessState.Empty)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test fun forbiddenKeepsLocalSessionForAllowedEndpoints() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val repository = RecordingRepository()
            repository.result = RemoteResult.Failure(RemoteError.Forbidden)
            var invalidated = false
            val vm = ReadinessViewModel(TokenProvider { "access" }, repository) { invalidated = true }
            vm.refresh()
            advanceUntilIdle()
            assertFalse(invalidated)
            assertTrue(vm.state.value is ReadinessState.Error)
        } finally {
            Dispatchers.resetMain()
        }
    }

    private class RecordingRepository : SpecialistRepository {
        var identityCalls = 0
        var result: RemoteResult<IdentityResponse> = RemoteResult.Success(IdentityResponse())
        override suspend fun identity(): RemoteResult<IdentityResponse> { identityCalls++; return result }
        override suspend fun today(): RemoteResult<TodayView> = RemoteResult.Success(TodayView())
        override suspend fun participants(): RemoteResult<List<ActiveParticipantView>> = RemoteResult.Success(emptyList())
        override suspend fun worklist(context: ActingContext, purpose: WorklistPurpose): RemoteResult<List<WorklistItemView>> = RemoteResult.Success(emptyList())
    }
}
