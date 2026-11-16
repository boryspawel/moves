package com.motionecosystem.moves.mobile.api

import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Body
import retrofit2.http.Header

interface MovesApi {
    @GET("api/v1/identity/me") suspend fun currentIdentity(): Response<IdentityResponse>
    @GET("api/v1/onboarding") suspend fun onboarding(): Response<MobileOnboardingState>
    @GET("api/v1/specialist/today") suspend fun today(@Query("date") date: String? = null): Response<TodayView>
    @GET("api/v1/specialist/participants") suspend fun activeParticipants(): Response<List<ActiveParticipantView>>
    @GET("api/v1/specialist/clients") suspend fun clients(): Response<List<ClientView>>
    @POST("api/v1/specialist/clients")
    suspend fun createClient(
        @Header("Idempotency-Key") idempotencyKey: String,
        @Body command: ClientCommand
    ): Response<ClientView>

    @GET("api/v1/specialist/clients/{participantId}/records/interviews")
    suspend fun interviews(
        @Path("participantId") participantId: java.util.UUID,
        @Query("actingContext") actingContext: ActingContext
    ): Response<List<InterviewView>>

    @GET("api/v1/specialist/participants/{participantId}/workspace")
    suspend fun workspace(@Path("participantId") participantId: java.util.UUID): Response<WorkspaceView>

    @GET("api/v1/specialist/participants/{participantId}/timeline")
    suspend fun timeline(
        @Path("participantId") participantId: java.util.UUID,
        @Query("types") types: String = MOBILE_TIMELINE_TYPES,
        @Query("granularity") granularity: String = "DETAIL",
        @Query("limit") limit: Int = 20,
        @Query("cursor") cursor: String? = null,
        @Query("from") from: String? = null,
        @Query("to") to: String? = null
    ): Response<TimelinePage>

    @POST("api/v1/specialist/appointments")
    suspend fun createAppointment(
        @Header("Idempotency-Key") key: String,
        @Body command: AppointmentSaveCommand
    ): Response<AppointmentView>

    @PUT("api/v1/specialist/appointments/{id}")
    suspend fun updateAppointment(
        @Path("id") id: java.util.UUID,
        @Header("Idempotency-Key") key: String,
        @Body command: AppointmentSaveCommand
    ): Response<AppointmentView>

    @GET("api/v1/specialist/appointments/{id}")
    suspend fun appointment(@Path("id") id: java.util.UUID): Response<AppointmentView>

    @POST("api/v1/specialist/appointments/{id}/start")
    suspend fun startAppointment(
        @Path("id") id: java.util.UUID,
        @Header("Idempotency-Key") key: String,
        @Body command: AppointmentVersionCommand
    ): Response<AppointmentView>

    @GET("api/v1/specialist/worklist") suspend fun worklist(@Query("actingContext") actingContext: ActingContext, @Query("purpose") purpose: WorklistPurpose): Response<List<WorklistItemView>>
}
