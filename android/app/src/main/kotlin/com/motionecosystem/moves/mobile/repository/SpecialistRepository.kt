package com.motionecosystem.moves.mobile.repository

import com.motionecosystem.moves.mobile.api.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

interface SpecialistRepository {
    suspend fun identity(): RemoteResult<IdentityResponse>
    suspend fun today(): RemoteResult<TodayView>
    suspend fun participants(): RemoteResult<List<ActiveParticipantView>>
    suspend fun worklist(context: ActingContext, purpose: WorklistPurpose): RemoteResult<List<WorklistItemView>>
}

class NetworkSpecialistRepository(private val api: MovesApi, private val calls: RemoteCall) : SpecialistRepository {
    override suspend fun identity() = read { api.currentIdentity() }
    override suspend fun today() = read { api.today() }
    override suspend fun participants() = read { api.activeParticipants() }
    override suspend fun worklist(context: ActingContext, purpose: WorklistPurpose) =
        read { api.worklist(context, purpose) }

    /** Only idempotent GETs are retried, once, for transient transport/server errors. */
    private suspend fun <T> read(request: suspend () -> retrofit2.Response<T>): RemoteResult<T> =
        withContext(Dispatchers.IO) {
            val first = calls.execute(request)
            val retryable = first is RemoteResult.Failure && when (val error = first.error) {
                is RemoteError.Network -> true
                is RemoteError.Http -> error.status in setOf(502, 503, 504)
                else -> false
            }
            if (!retryable) first else {
                delay(300)
                calls.execute(request)
            }
        }
}
