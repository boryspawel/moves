package com.motionecosystem.moves.mobile.repository

import com.motionecosystem.moves.mobile.api.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.time.LocalDate

interface SpecialistRepository {
    suspend fun identity(): RemoteResult<IdentityResponse>
    suspend fun onboarding(): RemoteResult<MobileOnboardingState>
    suspend fun today(date: LocalDate? = null): RemoteResult<TodayView>
    suspend fun participants(): RemoteResult<List<ActiveParticipantView>>
    suspend fun clients(): RemoteResult<List<ClientView>>
    /** Mutations must never be auto-retried; the caller preserves the UUID key for explicit retries. */
    suspend fun createClient(command: ClientCommand, idempotencyKey: String): RemoteResult<ClientView>
    suspend fun interviews(participantId: java.util.UUID, actingContext: ActingContext): RemoteResult<List<InterviewView>>
    suspend fun notes(participantId: java.util.UUID, actingContext: ActingContext): RemoteResult<List<NoteView>>
    suspend fun createNote(participantId: java.util.UUID, actingContext: ActingContext, key: String,
        command: CreateNoteCommand): RemoteResult<NoteView>
    suspend fun updateNote(participantId: java.util.UUID, noteId: java.util.UUID,
        actingContext: ActingContext, command: UpdateNoteCommand): RemoteResult<NoteView>
    suspend fun finaliseNote(participantId: java.util.UUID, noteId: java.util.UUID,
        actingContext: ActingContext, command: InterviewVersionCommand, key: String): RemoteResult<NoteView>
    suspend fun archiveNote(participantId: java.util.UUID, noteId: java.util.UUID,
        actingContext: ActingContext, command: InterviewVersionCommand, key: String): RemoteResult<NoteView>
    /** POST endpoints use the original key on explicit retries; PUT is versioned and single-shot. */
    suspend fun startInterview(participantId: java.util.UUID, actingContext: ActingContext, key: String): RemoteResult<InterviewView>
    suspend fun saveInterviewDraft(
        participantId: java.util.UUID, interviewId: java.util.UUID,
        actingContext: ActingContext, command: SaveInterviewDraftCommand
    ): RemoteResult<InterviewView>
    suspend fun completeInterview(
        participantId: java.util.UUID, interviewId: java.util.UUID,
        actingContext: ActingContext, command: InterviewVersionCommand, key: String
    ): RemoteResult<InterviewView>
    suspend fun workspace(participantId: java.util.UUID): RemoteResult<WorkspaceView>
    suspend fun timeline(participantId: java.util.UUID, cursor: String? = null, from: String? = null, to: String? = null): RemoteResult<TimelinePage>
    /** Context is always fetched by the bound appointment, never by an arbitrary participant. */
    suspend fun executionContext(appointmentId: java.util.UUID): RemoteResult<SpecialistExecutionContext>
    /** POST is single-shot; manual retry must reuse both exact payload and key. */
    suspend fun recordExecution(appointmentId: java.util.UUID, key: String,
        command: RecordSessionExecutionCommand): RemoteResult<RecordedSessionExecution>
    suspend fun appointment(id: java.util.UUID): RemoteResult<AppointmentView>
    /** Calendar writes are single-shot; retry is explicitly initiated by the caller with the original key. */
    suspend fun createAppointment(command: AppointmentSaveCommand, key: String): RemoteResult<AppointmentView>
    suspend fun updateAppointment(id: java.util.UUID, command: AppointmentSaveCommand, key: String): RemoteResult<AppointmentView>
    /** No automatic retries: a failed POST may have been committed remotely. */
    suspend fun startAppointment(id: java.util.UUID, version: Long, key: String): RemoteResult<AppointmentView>
    suspend fun worklist(context: ActingContext, purpose: WorklistPurpose): RemoteResult<List<WorklistItemView>>
}

class NetworkSpecialistRepository(private val api: MovesApi, private val calls: RemoteCall) : SpecialistRepository {
    override suspend fun identity() = read { api.currentIdentity() }
    override suspend fun onboarding() = read { api.onboarding() }
    override suspend fun today(date: LocalDate?) = read { api.today(date?.toString()) }
    override suspend fun participants() = read { api.activeParticipants() }
    override suspend fun clients() = read { api.clients() }
    override suspend fun createClient(command: ClientCommand, idempotencyKey: String): RemoteResult<ClientView> =
        withContext(Dispatchers.IO) { calls.execute { api.createClient(idempotencyKey, command) } }
    override suspend fun interviews(participantId: java.util.UUID, actingContext: ActingContext) =
        read { api.interviews(participantId, actingContext) }
    override suspend fun startInterview(participantId: java.util.UUID, actingContext: ActingContext, key: String) =
        withContext(Dispatchers.IO) { calls.execute { api.startInterview(participantId, actingContext, key) } }
    override suspend fun saveInterviewDraft(
        participantId: java.util.UUID, interviewId: java.util.UUID,
        actingContext: ActingContext, command: SaveInterviewDraftCommand
    ) = withContext(Dispatchers.IO) {
        calls.execute { api.saveInterviewDraft(participantId, interviewId, actingContext, command) }
    }
    override suspend fun completeInterview(
        participantId: java.util.UUID, interviewId: java.util.UUID,
        actingContext: ActingContext, command: InterviewVersionCommand, key: String
    ) = withContext(Dispatchers.IO) {
        calls.execute { api.completeInterview(participantId, interviewId, actingContext, key, command) }
    }
    override suspend fun notes(participantId: java.util.UUID, actingContext: ActingContext) =
        read { api.notes(participantId, actingContext) }
    override suspend fun createNote(participantId: java.util.UUID, actingContext: ActingContext,
        key: String, command: CreateNoteCommand
    ): RemoteResult<NoteView> = withContext(Dispatchers.IO) {
        calls.execute { api.createNote(participantId, actingContext, key, command) }
    }
    override suspend fun updateNote(participantId: java.util.UUID, noteId: java.util.UUID,
        actingContext: ActingContext, command: UpdateNoteCommand
    ): RemoteResult<NoteView> = withContext(Dispatchers.IO) {
        calls.execute { api.updateNote(participantId, noteId, actingContext, command) }
    }
    override suspend fun finaliseNote(participantId: java.util.UUID, noteId: java.util.UUID,
        actingContext: ActingContext, command: InterviewVersionCommand, key: String
    ): RemoteResult<NoteView> = withContext(Dispatchers.IO) {
        calls.execute { api.finaliseNote(participantId, noteId, actingContext, key, command) }
    }
    override suspend fun archiveNote(participantId: java.util.UUID, noteId: java.util.UUID,
        actingContext: ActingContext, command: InterviewVersionCommand, key: String
    ): RemoteResult<NoteView> = withContext(Dispatchers.IO) {
        calls.execute { api.archiveNote(participantId, noteId, actingContext, key, command) }
    }
    override suspend fun workspace(participantId: java.util.UUID) = read { api.workspace(participantId) }
    override suspend fun timeline(participantId: java.util.UUID, cursor: String?, from: String?, to: String?) =
        read { api.timeline(participantId, cursor = cursor, from = from, to = to) }
    override suspend fun executionContext(appointmentId: java.util.UUID) =
        read { api.executionContext(appointmentId) }
    override suspend fun recordExecution(appointmentId: java.util.UUID, key: String,
        command: RecordSessionExecutionCommand
    ): RemoteResult<RecordedSessionExecution> = withContext(Dispatchers.IO) {
        calls.execute { api.recordExecution(appointmentId, key, command) }
    }
    override suspend fun appointment(id: java.util.UUID) = read { api.appointment(id) }
    override suspend fun createAppointment(command: AppointmentSaveCommand, key: String): RemoteResult<AppointmentView> =
        withContext(Dispatchers.IO) { calls.execute { api.createAppointment(key, command) } }
    override suspend fun updateAppointment(id: java.util.UUID, command: AppointmentSaveCommand, key: String): RemoteResult<AppointmentView> =
        withContext(Dispatchers.IO) { calls.execute { api.updateAppointment(id, key, command) } }
    override suspend fun startAppointment(id: java.util.UUID, version: Long, key: String): RemoteResult<AppointmentView> =
        withContext(Dispatchers.IO) { calls.execute { api.startAppointment(id, key, AppointmentVersionCommand(version)) } }
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
