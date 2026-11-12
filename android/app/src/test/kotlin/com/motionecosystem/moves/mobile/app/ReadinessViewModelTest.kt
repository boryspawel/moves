package com.motionecosystem.moves.mobile.app

import com.motionecosystem.moves.mobile.api.*
import com.motionecosystem.moves.mobile.repository.SpecialistRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReadinessViewModelTest {
    @Test fun `missing token leaves an empty session state without invoking repository`() {
        val repository = RecordingRepository()
        val viewModel = ReadinessViewModel(NoTokenProvider, repository)

        viewModel.refresh()

        assertEquals(0, repository.identityCalls)
        assertTrue(viewModel.state.value is ReadinessState.Empty)
    }

    private class RecordingRepository : SpecialistRepository {
        var identityCalls = 0
        override suspend fun identity(): RemoteResult<IdentityResponse> { identityCalls++; return RemoteResult.Success(IdentityResponse()) }
        override suspend fun today(): RemoteResult<TodayView> = RemoteResult.Success(TodayView())
        override suspend fun participants(): RemoteResult<List<ActiveParticipantView>> = RemoteResult.Success(emptyList())
        override suspend fun worklist(context: ActingContext, purpose: WorklistPurpose): RemoteResult<List<WorklistItemView>> = RemoteResult.Success(emptyList())
    }
}
