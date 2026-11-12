package com.motionecosystem.moves.mobile.repository

import com.motionecosystem.moves.mobile.api.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

interface SpecialistRepository { suspend fun identity(): RemoteResult<IdentityResponse>; suspend fun today(): RemoteResult<TodayView>; suspend fun participants(): RemoteResult<List<ActiveParticipantView>>; suspend fun worklist(context: ActingContext, purpose: WorklistPurpose): RemoteResult<List<WorklistItemView>> }
class NetworkSpecialistRepository(private val api: MovesApi, private val calls: RemoteCall) : SpecialistRepository {
    override suspend fun identity() = io { calls.execute { api.currentIdentity() } }
    override suspend fun today() = io { calls.execute { api.today() } }
    override suspend fun participants() = io { calls.execute { api.activeParticipants() } }
    override suspend fun worklist(context: ActingContext, purpose: WorklistPurpose) = io { calls.execute { api.worklist(context, purpose) } }
    private suspend fun <T> io(block: suspend () -> RemoteResult<T>) = withContext(Dispatchers.IO) { block() }
}
