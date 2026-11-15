package com.motionecosystem.moves.mobile.api

import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query
import retrofit2.http.POST
import retrofit2.http.Body
import retrofit2.http.Header

interface MovesApi {
    @GET("api/v1/identity/me") suspend fun currentIdentity(): Response<IdentityResponse>
    @GET("api/v1/specialist/today") suspend fun today(@Query("date") date: String? = null): Response<TodayView>
    @GET("api/v1/specialist/participants") suspend fun activeParticipants(): Response<List<ActiveParticipantView>>
    @GET("api/v1/specialist/clients") suspend fun clients(): Response<List<ClientView>>
    @POST("api/v1/specialist/clients")
    suspend fun createClient(
        @Header("Idempotency-Key") idempotencyKey: String,
        @Body command: ClientCommand
    ): Response<ClientView>

    @GET("api/v1/specialist/worklist") suspend fun worklist(@Query("actingContext") actingContext: ActingContext, @Query("purpose") purpose: WorklistPurpose): Response<List<WorklistItemView>>
}
