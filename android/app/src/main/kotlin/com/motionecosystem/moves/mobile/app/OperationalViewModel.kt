package com.motionecosystem.moves.mobile.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.motionecosystem.moves.mobile.api.*
import com.motionecosystem.moves.mobile.repository.SpecialistRepository
import com.motionecosystem.moves.mobile.auth.TokenRefreshTemporarilyUnavailable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
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

data class TimelineFeed(
    val items: List<TimelineEvent>,
    val nextCursor: String?,
    val from: String?,
    val to: String?,
    val loadingMore: Boolean = false,
    val loadMoreFailed: Boolean = false
)

/** S08 is not enabled; only the six explicit event categories are permitted in S03. */
private fun visibleTimelineEvents(items: List<TimelineEvent>): List<TimelineEvent> =
    items.filterNot { it.category == "MEASUREMENT" || it.eventType == "MEASUREMENT" }

sealed interface CreateClientState {
    data object Idle : CreateClientState
    data object Saving : CreateClientState
    data class Saved(val participantId: UUID) : CreateClientState
    data class Failed(val message: String) : CreateClientState
}

/** Sensitive draft data lives only in the lifecycle-retained ViewModel, not SavedState/Bundle. */
data class ClientFormDraft(
    val open: Boolean = false,
    val displayName: String = "",
    val relationship: ClientRelationship? = null,
    val email: String = "",
    val phone: String = "",
    val requestKey: String = UUID.randomUUID().toString()
)

sealed interface ScheduleTarget {
    data class New(val participantId: UUID? = null) : ScheduleTarget
    data class Edit(val appointmentId: UUID, val participantId: UUID) : ScheduleTarget
}

/** Calendar dates are in the specialist's persisted IANA time zone, never UTC or device time.
 * The API may omit the zone on an invalid profile; refuse to guess.
 */
internal fun appointmentDateInSpecialistZone(start: OffsetDateTime?, timeZoneId: String?): LocalDate? {
    if (start == null || timeZoneId.isNullOrBlank()) return null
    val zone = runCatching { ZoneId.of(timeZoneId) }.getOrNull() ?: return null
    return start.atZoneSameInstant(zone).toLocalDate()
}

/** Updating metadata may reuse the authorized appointment's own occupied time.
 * A different time still requires an exact server-provided free slot.
 * The backend remains responsible for overlap and availability checks.
 */
internal fun validScheduleWindow(
    target: ScheduleTarget,
    command: AppointmentSaveCommand,
    detail: AppointmentView?,
    day: TodayView?
): Boolean {
    val start = command.startsAt.toInstant()
    val end = command.endsAt.toInstant()
    if (!end.isAfter(start)) return false
    if (target is ScheduleTarget.Edit) {
        if (detail?.appointmentId != target.appointmentId ||
            detail.participantId != target.participantId ||
            command.participantId != target.participantId) return false
        if (detail.startsAt?.toInstant() == start && detail.endsAt?.toInstant() == end) {
            return true
        }
    }
    return day?.bookableSlots.orEmpty().any {
        it.startsAt?.toInstant() == start && it.endsAt?.toInstant() == end
    }
}

/** RAM-only scheduling draft, kept through rotation and 409 but deleted on logout/403. */
internal data class ScheduleDraft(
    val participantId: UUID?,
    val type: AppointmentType = AppointmentType.CONSULTATION,
    val locationMode: AppointmentLocationMode = AppointmentLocationMode.IN_PERSON,
    val location: String = "",
    val shortPurpose: String = "",
    val baseline: ScheduleEditBaseline? = null,
    val selectedSlot: BookableSlotView? = null
)

sealed interface ScheduleSaveState {
    data object Idle : ScheduleSaveState
    data object Saving : ScheduleSaveState
    data object Uncertain : ScheduleSaveState
    data object Conflict : ScheduleSaveState
    data class Failed(val message: String) : ScheduleSaveState
    data class Saved(val appointment: AppointmentView) : ScheduleSaveState
}

/** Manual retry after an uncertain START reuses exactly the same request key and version. */
enum class AppointmentStartState { IDLE, SAVING, UNCERTAIN, CONFLICT, FAILED }

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
    private val mutableProfessionalContext = MutableStateFlow<ReadState<ActingContext>>(ReadState.Idle)
    val professionalContext = mutableProfessionalContext.asStateFlow()
    private var profileRequest = 0
    private var verifiedRole: ActingContext? = null
    private val mutableAccessEpoch = MutableStateFlow(0)
    val accessEpoch = mutableAccessEpoch.asStateFlow()
    val today = mutableToday.asStateFlow()
    private val mutableRequestedDate = MutableStateFlow<LocalDate?>(null)
    val requestedDate = mutableRequestedDate.asStateFlow()
    private var todayRequest = 0
    private val mutableClients = MutableStateFlow<ReadState<List<ClientView>>>(ReadState.Idle)
    val clients = mutableClients.asStateFlow()
    private val mutableCreateClient = MutableStateFlow<CreateClientState>(CreateClientState.Idle)
    val createClientState = mutableCreateClient.asStateFlow()
    private val mutableClientDraft = MutableStateFlow(ClientFormDraft())
    val clientDraft = mutableClientDraft.asStateFlow()
    private val mutableClientQuery = MutableStateFlow("")
    val clientQuery = mutableClientQuery.asStateFlow()
    fun searchClients(query: String) { mutableClientQuery.value = query }
    private var clientRequest = 0
    private val mutableParticipants = MutableStateFlow<ReadState<List<ActiveParticipantView>>>(ReadState.Idle)
    val participants = mutableParticipants.asStateFlow()
    private val mutableWorklist = MutableStateFlow<ReadState<List<WorklistItemView>>>(ReadState.Idle)
    val worklist = mutableWorklist.asStateFlow()
    private val mutableWorkspace = MutableStateFlow<ReadState<WorkspaceView>>(ReadState.Idle)
    val workspace = mutableWorkspace.asStateFlow()
    private val mutableTimeline = MutableStateFlow<ReadState<TimelineFeed>>(ReadState.Idle)
    val timeline = mutableTimeline.asStateFlow()
    private val mutableInterviews = MutableStateFlow<ReadState<List<InterviewView>>>(ReadState.Idle)
    val interviews = mutableInterviews.asStateFlow()
    private val mutableInterviewDraft = MutableStateFlow<InterviewDraft?>(null)
    internal val interviewDraft = mutableInterviewDraft.asStateFlow()
    private val mutableInterviewSelection = MutableStateFlow<UUID?>(null)
    internal val interviewSelection = mutableInterviewSelection.asStateFlow()
    private val mutableInterviewWrite = MutableStateFlow<InterviewWriteState>(InterviewWriteState.Idle)
    internal val interviewWrite = mutableInterviewWrite.asStateFlow()
    private var interviewMutationRequest = 0
    private data class PendingInterviewStart(val participant: UUID, val key: String)
    private data class PendingInterviewComplete(
        val participant: UUID, val interview: UUID, val version: Long, val key: String
    )
    private var pendingInterviewStart: PendingInterviewStart? = null
    private var pendingInterviewComplete: PendingInterviewComplete? = null
    private var interviewRequest = 0
    private val mutableNotes = MutableStateFlow<ReadState<List<NoteView>>>(ReadState.Idle)
    internal val notes = mutableNotes.asStateFlow()
    private val mutableNoteDraft = MutableStateFlow<NoteDraft?>(null)
    internal val noteDraft = mutableNoteDraft.asStateFlow()
    private val mutableNoteSelection = MutableStateFlow<UUID?>(null)
    internal val noteSelection = mutableNoteSelection.asStateFlow()
    private val mutableNoteWrite = MutableStateFlow<NoteWriteState>(NoteWriteState.Idle)
    internal val noteWrite = mutableNoteWrite.asStateFlow()
    private var noteRequest = 0
    private var noteMutationRequest = 0
    private data class NotePost(
        val operation: NoteOperation, val participant: UUID, val noteId: UUID,
        val version: Long, val key: String
    )
    private var pendingNotePost: NotePost? = null
    private var workspaceRequest = 0
    private var timelineRequest = 0

    private val mutableAppointment = MutableStateFlow<ReadState<AppointmentView>>(ReadState.Idle)
    val appointment = mutableAppointment.asStateFlow()
    private val mutableAppointmentStart = MutableStateFlow(AppointmentStartState.IDLE)
    val appointmentStart = mutableAppointmentStart.asStateFlow()
    private data class PendingStart(val id: UUID, val participantId: UUID, val version: Long, val key: String)
    private var pendingStart: PendingStart? = null
    private var appointmentRequest = 0
    private var startRequest = 0

    private val mutableScheduleTarget = MutableStateFlow<ScheduleTarget?>(null)
    val scheduleTarget = mutableScheduleTarget.asStateFlow()
    private val mutableScheduleDraft = MutableStateFlow<ScheduleDraft?>(null)
    internal val scheduleDraft = mutableScheduleDraft.asStateFlow()
    internal fun changeScheduleDraft(next: ScheduleDraft) {
        val current = mutableScheduleDraft.value ?: return
        val target = mutableScheduleTarget.value ?: return
        if (mutableScheduleSave.value == ScheduleSaveState.Saving ||
            mutableScheduleSave.value is ScheduleSaveState.Saved || next.baseline != current.baseline) return
        if (target is ScheduleTarget.Edit && next.participantId != target.participantId) return
        mutableScheduleDraft.value = next
        // Editing after a failed or uncertain submission creates a new payload/key at save time.
        if (mutableScheduleSave.value is ScheduleSaveState.Failed ||
            mutableScheduleSave.value == ScheduleSaveState.Uncertain) {
            mutableScheduleSave.value = ScheduleSaveState.Idle
        }
    }
    private val mutableScheduleDate = MutableStateFlow<LocalDate?>(null)
    val scheduleDate = mutableScheduleDate.asStateFlow()
    // True only while discovering the specialist's calendar zone for an existing visit.
    private val mutableScheduleDateResolving = MutableStateFlow(false)
    val scheduleDateResolving = mutableScheduleDateResolving.asStateFlow()
    private val mutableScheduleDay = MutableStateFlow<ReadState<TodayView>>(ReadState.Idle)
    val scheduleDay = mutableScheduleDay.asStateFlow()
    private val mutableScheduleSave = MutableStateFlow<ScheduleSaveState>(ScheduleSaveState.Idle)
    val scheduleSave = mutableScheduleSave.asStateFlow()
    private data class PendingSchedule(val target: ScheduleTarget, val command: AppointmentSaveCommand, val key: String)
    private var pendingSchedule: PendingSchedule? = null
    private var scheduleRequest = 0
    private var scheduleSaveRequest = 0

    private val mutableSelection = MutableStateFlow<ContextSelection?>(null)
    val selection = mutableSelection.asStateFlow()
    private var generation = 0

    /** A failed refresh can mean offline Keycloak, not invalid credentials.
     * Mark the entire professional view offline without retaining authorized projections.
     */
    private fun blockForTemporaryIdentityFailure() {
        if (verifiedRole != null) suspendAuthorizedReads() else clear()
        mutableProfessionalContext.value = ReadState.Offline
    }

    /** Refresh never retries a mutation; all callers must stop before their HTTP request on failure. */
    private suspend fun ensureFreshToken(current: () -> Boolean): Boolean {
        val token = try {
            freshToken()
        } catch (_: TokenRefreshTemporarilyUnavailable) {
            if (current()) blockForTemporaryIdentityFailure()
            return false
        }
        if (!current()) return false
        if (token.isNullOrBlank()) {
            clear()
            invalidateSession()
            return false
        }
        return true
    }

    /** Hide all previously authorized responses immediately, but retain the route and unsent drafts.
     * The UI is completely gated until the server confirms the SAME professional role again.
     * On denial or a role change, even the in-memory navigation/draft context is destroyed.
     */
    fun revalidateAccess() {
        if (verifiedRole == null) clear() else suspendAuthorizedReads()
        val requestId = ++profileRequest
        val currentGeneration = generation
        mutableProfessionalContext.value = ReadState.Loading
        viewModelScope.launch {
            if (!ensureFreshToken { currentGeneration == generation && requestId == profileRequest }) return@launch
            val response = repository.onboarding()
            if (generation != currentGeneration || requestId != profileRequest) return@launch
            when (response) {
                is RemoteResult.Success -> {
                    val state = response.value
                    val role = state.profile?.specialistKind
                    if (state.stage != "READY" || state.profileType != "SPECIALIST" || role == null) {
                        clear()
                        mutableProfessionalContext.value = ReadState.Forbidden
                    } else {
                        if (verifiedRole != null && verifiedRole != role) clear()
                        verifiedRole = role
                        mutableProfessionalContext.value = ReadState.Ready(role)
                        mutableAccessEpoch.value++
                    }
                }
                is RemoteResult.Failure -> when (response.error) {
                    RemoteError.Unauthorized -> { clear(); invalidateSession() }
                    RemoteError.Forbidden -> {
                        clear()
                        mutableProfessionalContext.value = ReadState.Forbidden
                    }
                    is RemoteError.Network -> mutableProfessionalContext.value = ReadState.Offline
                    else -> mutableProfessionalContext.value = ReadState.Error
                }
            }
        }
    }

    /** Drop old PHI and invalidate outstanding GETs before a foreground authorization check.
     * Only non-authorizing identifiers and unsent RAM-only drafts survive a transient check.
     * In-flight mutations become uncertain; they must never be automatically retried.
     */
    private fun suspendAuthorizedReads() {
        generation++
        profileRequest++
        todayRequest++
        clientRequest++
        resetParticipantReads()
        appointmentRequest++
        startRequest++
        mutableAppointment.value = ReadState.Idle
        if (mutableAppointmentStart.value == AppointmentStartState.SAVING) {
            mutableAppointmentStart.value = AppointmentStartState.UNCERTAIN
        }
        scheduleRequest++
        scheduleSaveRequest++
        mutableScheduleDay.value = ReadState.Idle
        if (mutableScheduleSave.value == ScheduleSaveState.Saving) {
            mutableScheduleSave.value = ScheduleSaveState.Uncertain
        }
        if (mutableCreateClient.value == CreateClientState.Saving) {
            mutableCreateClient.value = CreateClientState.Failed(
                "Niepewny wynik zapisu. Przed ponowieniem sprawdź, czy kartoteka już istnieje."
            )
        }
        val interviewWriting = mutableInterviewWrite.value as? InterviewWriteState.Saving
        if (interviewWriting != null) {
            interviewMutationRequest++
            mutableInterviewWrite.value = InterviewWriteState.Uncertain(interviewWriting.operation)
        }
        val noteWriting = mutableNoteWrite.value as? NoteWriteState.Saving
        if (noteWriting != null) {
            noteMutationRequest++
            mutableNoteWrite.value = NoteWriteState.Uncertain(noteWriting.operation)
        }
        mutableToday.value = ReadState.Idle
        mutableClients.value = ReadState.Idle
        mutableParticipants.value = ReadState.Idle
        mutableWorklist.value = ReadState.Idle
    }

    private fun selectedAppointment(): ContextSelection.Appointment? =
        mutableSelection.value as? ContextSelection.Appointment

    /** Re-fetch on every entry and after a 409; never trust the agenda snapshot as a mutation grant. */
    fun loadAppointment(id: UUID) {
        val selected = selectedAppointment()?.takeIf { it.appointmentId == id } ?: return
        if (mutableAppointmentStart.value == AppointmentStartState.SAVING) return
        val requestId = ++appointmentRequest
        read(mutableAppointment, skipIfLoading = false,
            isCurrent = { requestId == appointmentRequest && selectedAppointment()?.appointmentId == id }) {
            when (val response = repository.appointment(id)) {
                is RemoteResult.Failure -> response
                is RemoteResult.Success -> {
                    val detail = response.value
                    if (detail.appointmentId != id || detail.participantId == null ||
                        (selected.participantId != null && selected.participantId != detail.participantId)) {
                        RemoteResult.Failure(RemoteError.Forbidden)
                    } else {
                        if (selectedAppointment()?.participantId == null) {
                            mutableSelection.value = selected.copy(participantId = detail.participantId)
                        }
                        if (detail.status !in setOf(AppointmentStatus.SCHEDULED, AppointmentStatus.CONFIRMED)) {
                            pendingStart = null
                            mutableAppointmentStart.value = AppointmentStartState.IDLE
                        }
                        RemoteResult.Success(detail)
                    }
                }
            }
        }
    }

    /** Single POST per click. On uncertain outcome only an explicit retry sends the same command. */
    fun startAppointment(id: UUID) {
        val selected = selectedAppointment()?.takeIf { it.appointmentId == id } ?: return
        val detail = (mutableAppointment.value as? ReadState.Ready)?.value ?: return
        val participantId = detail.participantId ?: return
        val version = detail.version ?: return
        if (selected.participantId != null && selected.participantId != participantId) return
        if (detail.appointmentId != id || detail.status !in setOf(AppointmentStatus.SCHEDULED, AppointmentStatus.CONFIRMED)
            || !detail.availableActions.orEmpty().contains("START") ||
            mutableAppointmentStart.value == AppointmentStartState.SAVING) return
        val command = pendingStart?.takeIf {
            it.id == id && it.participantId == participantId && it.version == version
        } ?: PendingStart(id, participantId, version, UUID.randomUUID().toString())
        pendingStart = command
        val currentGeneration = generation
        val requestId = ++startRequest
        appointmentRequest++ // Ignore a GET started before this mutation.
        mutableAppointmentStart.value = AppointmentStartState.SAVING
        viewModelScope.launch {
            if (!ensureFreshToken { generation == currentGeneration && startRequest == requestId }) return@launch
            val result = repository.startAppointment(id, command.version, command.key)
            if (currentGeneration != generation || requestId != startRequest || selectedAppointment()?.appointmentId != id) return@launch
            when (result) {
                is RemoteResult.Success -> {
                    val started = result.value
                    // An idempotent replay returns the *current* server snapshot, which may
                    // already be COMPLETED if another client closed the appointment meanwhile.
                    if (started.appointmentId != id || started.participantId != participantId) {
                        clear()
                        mutableProfessionalContext.value = ReadState.Forbidden
                    } else {
                        pendingStart = null
                        mutableAppointmentStart.value = AppointmentStartState.IDLE
                        mutableAppointment.value = ReadState.Ready(started)
                        loadToday()
                    }
                }
                is RemoteResult.Failure -> when (val error = result.error) {
                    RemoteError.Unauthorized -> { clear(); invalidateSession() }
                    RemoteError.Forbidden -> { clear(); mutableProfessionalContext.value = ReadState.Forbidden }
                    RemoteError.Conflict -> {
                        pendingStart = null
                        mutableAppointmentStart.value = AppointmentStartState.CONFLICT
                        loadAppointment(id)
                    }
                    is RemoteError.Network, is RemoteError.Serialization ->
                        mutableAppointmentStart.value = AppointmentStartState.UNCERTAIN
                    is RemoteError.Http -> {
                        if (error.status >= 500) mutableAppointmentStart.value = AppointmentStartState.UNCERTAIN
                        else { pendingStart = null; mutableAppointmentStart.value = AppointmentStartState.FAILED }
                    }
                }
            }
        }
    }

    /** Only the server-provided bookable slots are selectable. All access control still belongs to the server. */
    fun openNewSchedule(participantId: UUID? = null) {
        resetSchedule()
        // A new appointment must not retain fields or a pending action from a previous participant.
        resetAppointmentReads()
        mutableScheduleTarget.value = ScheduleTarget.New(participantId)
        mutableScheduleDraft.value = ScheduleDraft(participantId)
        mutableScheduleDate.value = mutableRequestedDate.value
        loadScheduleDay()
        if (mutableClients.value == ReadState.Idle) loadClients()
    }

    fun openEditSchedule(): Boolean {
        val selection = selectedAppointment() ?: return false
        val detail = (mutableAppointment.value as? ReadState.Ready)?.value ?: return false
        if (detail.appointmentId != selection.appointmentId || detail.participantId == null ||
            (selection.participantId != null && selection.participantId != detail.participantId) ||
            detail.version == null || "UPDATE" !in detail.availableActions.orEmpty()) return false
        resetSchedule()
        val target = ScheduleTarget.Edit(selection.appointmentId, detail.participantId)
        val baseline = editBaseline(target, ReadState.Ready(detail)) ?: return false
        mutableScheduleTarget.value = target
        mutableScheduleDraft.value = ScheduleDraft(
            participantId = detail.participantId,
            type = baseline.type,
            locationMode = baseline.locationMode,
            location = baseline.location,
            shortPurpose = baseline.shortPurpose,
            baseline = baseline
        )
        val cachedZone = (mutableToday.value as? ReadState.Ready)?.value?.timeZoneId
        val date = appointmentDateInSpecialistZone(detail.startsAt, cachedZone)
        if (date == null) {
            // A detail opened from S03 may have no cached Today response. Read the
            // specialist's current calendar only to discover its authoritative zone.
            // Do not issue a day-scoped query or show UTC-derived dates before this.
            mutableScheduleDateResolving.value = true
            mutableScheduleDate.value = null
        } else {
            mutableScheduleDate.value = date
        }
        loadScheduleDay()
        return true
    }

    /** Called only after the unscoped Today GET confirms the persisted specialist zone.
     * The read remains hidden until the resolved visit day has been fetched.
     */
    fun resolveEditScheduleDate(serverToday: TodayView) {
        val target = mutableScheduleTarget.value as? ScheduleTarget.Edit ?: return
        if (!mutableScheduleDateResolving.value) return
        val detail = (mutableAppointment.value as? ReadState.Ready)?.value ?: return
        if (detail.appointmentId != target.appointmentId || detail.participantId != target.participantId) {
            mutableScheduleDay.value = ReadState.Error
            return
        }
        val date = appointmentDateInSpecialistZone(detail.startsAt, serverToday.timeZoneId)
        if (date == null) {
            mutableScheduleDay.value = ReadState.Error
            return
        }
        mutableScheduleDate.value = date
        mutableScheduleDateResolving.value = false
        loadScheduleDay()
    }

    fun chooseScheduleDate(date: LocalDate?) {
        if (mutableScheduleTarget.value == null || mutableScheduleSave.value == ScheduleSaveState.Saving ||
            mutableScheduleDateResolving.value) return
        mutableScheduleDate.value = date
        mutableScheduleDraft.value = mutableScheduleDraft.value?.copy(selectedSlot = null)
        pendingSchedule = null
        mutableScheduleSave.value = ScheduleSaveState.Idle
        loadScheduleDay()
    }

    fun loadScheduleDay() {
        val target = mutableScheduleTarget.value ?: return
        if (mutableScheduleSave.value == ScheduleSaveState.Saving) return
        val date = if (mutableScheduleDateResolving.value) null else mutableScheduleDate.value
        val requestId = ++scheduleRequest
        read(mutableScheduleDay, skipIfLoading = false,
            isCurrent = { requestId == scheduleRequest && mutableScheduleTarget.value == target }) {
            repository.today(date)
        }
    }

    /** Key is reused only for a byte-equivalent command and resource after an uncertain response. */
    fun saveSchedule(command: AppointmentSaveCommand) {
        val target = mutableScheduleTarget.value ?: return
        if (mutableScheduleSave.value == ScheduleSaveState.Saving || mutableScheduleSave.value is ScheduleSaveState.Saved) return
        if (mutableScheduleDateResolving.value) {
            mutableScheduleSave.value = ScheduleSaveState.Failed("Nie potwierdzono strefy czasu specjalisty.")
            return
        }
        val day = (mutableScheduleDay.value as? ReadState.Ready)?.value
        val currentDetail = (mutableAppointment.value as? ReadState.Ready)?.value
        if (!validScheduleWindow(target, command, currentDetail, day)) {
            mutableScheduleSave.value = ScheduleSaveState.Failed(
                "Zachowaj dotychczasowy termin albo wybierz dostępny termin z aktualnej listy."
            )
            return
        }
        val verified = when (target) {
            is ScheduleTarget.New -> {
                val clients = (mutableClients.value as? ReadState.Ready)?.value.orEmpty()
                val allowed = clients.any {
                    it.participantId == command.participantId && it.recordStatus != ClientRecordStatus.ARCHIVED &&
                        "SCHEDULE_APPOINTMENT" in it.availableActions.orEmpty()
                }
                if (target.participantId != null && target.participantId != command.participantId || !allowed) null
                else command.copy(version = null, plannedSessionId = null)
            }
            is ScheduleTarget.Edit -> {
                val detail = (mutableAppointment.value as? ReadState.Ready)?.value
                if (detail?.appointmentId != target.appointmentId || detail.participantId != target.participantId ||
                    command.participantId != target.participantId || detail.version == null ||
                    "UPDATE" !in detail.availableActions.orEmpty()) null
                else command.copy(version = detail.version, plannedSessionId = detail.plannedSessionId)
            }
        }
        if (verified == null) {
            mutableScheduleSave.value = ScheduleSaveState.Failed("Brak aktualnych uprawnień lub danych spotkania. Odśwież widok.")
            return
        }
        val operation = pendingSchedule?.takeIf { it.target == target && it.command == verified }
            ?: PendingSchedule(target, verified, UUID.randomUUID().toString())
        pendingSchedule = operation
        val currentGeneration = generation
        val requestId = ++scheduleSaveRequest
        scheduleRequest++ // An older slot response must not replace the draft after a write.
        mutableScheduleSave.value = ScheduleSaveState.Saving
        viewModelScope.launch {
            if (!ensureFreshToken { generation == currentGeneration && requestId == scheduleSaveRequest }) return@launch
            val result = when (target) {
                is ScheduleTarget.New -> repository.createAppointment(operation.command, operation.key)
                is ScheduleTarget.Edit -> repository.updateAppointment(target.appointmentId, operation.command, operation.key)
            }
            if (generation != currentGeneration || requestId != scheduleSaveRequest || mutableScheduleTarget.value != target) return@launch
            when (result) {
                is RemoteResult.Success -> {
                    val saved = result.value
                    val matches = saved.appointmentId != null && saved.participantId == verified.participantId &&
                        (target !is ScheduleTarget.Edit || saved.appointmentId == target.appointmentId)
                    if (!matches) {
                        clear()
                        mutableProfessionalContext.value = ReadState.Forbidden
                    } else {
                        pendingSchedule = null
                        mutableScheduleSave.value = ScheduleSaveState.Saved(saved)
                        loadToday()
                        loadClients()
                    }
                }
                is RemoteResult.Failure -> when (val error = result.error) {
                    RemoteError.Unauthorized -> { clear(); invalidateSession() }
                    RemoteError.Forbidden -> { clear(); mutableProfessionalContext.value = ReadState.Forbidden }
                    RemoteError.Conflict -> {
                        pendingSchedule = null
                        mutableScheduleSave.value = ScheduleSaveState.Conflict
                        loadScheduleDay()
                        if (target is ScheduleTarget.Edit) loadAppointment(target.appointmentId)
                    }
                    is RemoteError.Network, is RemoteError.Serialization ->
                        mutableScheduleSave.value = ScheduleSaveState.Uncertain
                    is RemoteError.Http -> {
                        if (error.status >= 500) mutableScheduleSave.value = ScheduleSaveState.Uncertain
                        else { pendingSchedule = null; mutableScheduleSave.value = ScheduleSaveState.Failed("Serwer odrzucił termin.") }
                    }
                }
            }
        }
    }

    fun closeSchedule() = resetSchedule()

    private fun resetSchedule() {
        scheduleRequest++
        scheduleSaveRequest++
        pendingSchedule = null
        mutableScheduleTarget.value = null
        mutableScheduleDraft.value = null
        mutableScheduleDate.value = null
        mutableScheduleDateResolving.value = false
        mutableScheduleDay.value = ReadState.Idle
        mutableScheduleSave.value = ScheduleSaveState.Idle
    }

    /** Drop authorized snapshots synchronously before pushing a child route.
     * The destination MUST perform a new GET, and no prior response may paint a frame.
     */
    fun prepareVisitStage() {
        if (selectedAppointment() == null || mutableAppointmentStart.value == AppointmentStartState.SAVING) return
        appointmentRequest++
        mutableAppointment.value = ReadState.Idle
    }

    fun prepareParticipantDocumentationStage() {
        if (selectedParticipantId() == null) return
        resetParticipantReads()
    }

    private fun selectedParticipantId(): UUID? =
        (mutableSelection.value as? ContextSelection.Participant)?.participantId

    fun selectParticipant(id: UUID) {
        resetInterviewEditor()
        resetNoteEditor()
        resetAppointmentReads()
        resetParticipantReads()
        mutableSelection.value = ContextSelection.Participant(id)
    }

    fun selectAppointment(participantId: UUID?, appointmentId: UUID) {
        resetInterviewEditor()
        resetNoteEditor()
        resetAppointmentReads()
        resetParticipantReads()
        mutableSelection.value = ContextSelection.Appointment(participantId, appointmentId)
    }

    private fun resetAppointmentReads() {
        appointmentRequest++
        startRequest++
        pendingStart = null
        mutableAppointment.value = ReadState.Idle
        mutableAppointmentStart.value = AppointmentStartState.IDLE
    }

    private fun resetParticipantReads() {
        workspaceRequest++
        timelineRequest++
        interviewRequest++
        noteRequest++
        mutableWorkspace.value = ReadState.Idle
        mutableTimeline.value = ReadState.Idle
        mutableInterviews.value = ReadState.Idle
        mutableNotes.value = ReadState.Idle
    }

    /** A documentation GET is permitted only after the current S03/S05 context was reverified. */
    fun loadInterviews(participantId: UUID) {
        if (mutableInterviewWrite.value is InterviewWriteState.Saving) return
        if (mutableProfessionalContext.value !is ReadState.Ready) return
        if (verifiedDocumentationParticipant(
                mutableSelection.value, mutableAppointment.value, mutableWorkspace.value
            ) != participantId || verifiedRole == null) return
        val role = verifiedRole ?: return
        val requestId = ++interviewRequest
        read(mutableInterviews, skipIfLoading = false,
            isCurrent = {
                requestId == interviewRequest && verifiedRole == role &&
                    verifiedDocumentationParticipant(
                        mutableSelection.value, mutableAppointment.value, mutableWorkspace.value
                    ) == participantId
            }) { repository.interviews(participantId, role) }
    }

    fun openInterview(participantId: UUID, interviewId: UUID) {
        val current = mutableInterviewDraft.value
        if (current?.dirty == true) return
        val allowed = verifiedDocumentationParticipant(
            mutableSelection.value, mutableAppointment.value, mutableWorkspace.value
        ) == participantId
        val record = (mutableInterviews.value as? ReadState.Ready)?.value
            ?.firstOrNull { it.id == interviewId && it.participantId == participantId }
        if (!allowed || record == null || mutableInterviewWrite.value is InterviewWriteState.Saving) return
        mutableInterviewSelection.value = interviewId
        mutableInterviewDraft.value = initialInterviewDraft(participantId, record)
        mutableInterviewWrite.value = InterviewWriteState.Idle
    }

    private fun resetInterviewEditor() {
        interviewMutationRequest++
        pendingInterviewStart = null
        pendingInterviewComplete = null
        mutableInterviewSelection.value = null
        mutableInterviewDraft.value = null
        mutableInterviewWrite.value = InterviewWriteState.Idle
    }

    fun closeInterview() {
        if (mutableInterviewWrite.value is InterviewWriteState.Saving) return
        resetInterviewEditor()
    }

    fun updateInterviewText(code: String, text: String) {
        val draft = mutableInterviewDraft.value ?: return
        if (mutableInterviewWrite.value is InterviewWriteState.Saving ||
            mutableProfessionalContext.value !is ReadState.Ready) return
        if (verifiedDocumentationParticipant(
                mutableSelection.value, mutableAppointment.value, mutableWorkspace.value
            ) != draft.participantId) return
        val record = (mutableInterviews.value as? ReadState.Ready)?.value
            ?.firstOrNull { it.id == draft.interviewId && it.participantId == draft.participantId }
            ?: return
        if (record.status != "DRAFT" || "SAVE_DRAFT" !in record.availableActions) return
        val question = record.questions.firstOrNull { it.code == code } ?: return
        if (!editableInterviewAnswer(question) || text.length > 4000) return
        val values = draft.values + (code to text)
        mutableInterviewDraft.value = draft.copy(
            values = values,
            dirty = values != draft.originalValues ||
                draft.selections != draft.originalSelections
        )
        if (mutableInterviewWrite.value is InterviewWriteState.Failed ||
            mutableInterviewWrite.value is InterviewWriteState.Success) {
            mutableInterviewWrite.value = InterviewWriteState.Idle
        }
    }

    /** The select vocabulary matches the existing web UI, not a guessed consent grant. */
    fun updateInterviewSelections(code: String, values: List<String>) {
        val draft = mutableInterviewDraft.value ?: return
        if (mutableInterviewWrite.value is InterviewWriteState.Saving ||
            mutableProfessionalContext.value !is ReadState.Ready ||
            authorizedInterviewParticipant() != draft.participantId) return
        val record = currentInterview(draft.participantId, draft.interviewId) ?: return
        if (record.status != "DRAFT" || "SAVE_DRAFT" !in record.availableActions) return
        val question = record.questions.firstOrNull { it.code == code && isInterviewSelection(it) }
            ?: return
        val choices = interviewOptions(code).map { it.value }.toSet()
        val unique = values.distinct()
        if (choices.isEmpty() || unique.any { it !in choices } ||
            (question.type == "SINGLE_SELECT" && unique.size > 1)) return
        val selected = draft.selections + (code to unique)
        mutableInterviewDraft.value = draft.copy(
            selections = selected,
            dirty = draft.values != draft.originalValues || selected != draft.originalSelections
        )
        if (mutableInterviewWrite.value is InterviewWriteState.Failed ||
            mutableInterviewWrite.value is InterviewWriteState.Success) {
            mutableInterviewWrite.value = InterviewWriteState.Idle
        }
    }

    private fun authorizedInterviewParticipant(): UUID? {
        if (mutableProfessionalContext.value !is ReadState.Ready || verifiedRole == null) return null
        return verifiedDocumentationParticipant(
            mutableSelection.value, mutableAppointment.value, mutableWorkspace.value
        )
    }

    private fun currentInterview(participant: UUID, id: UUID): InterviewView? =
        (mutableInterviews.value as? ReadState.Ready)?.value
            ?.firstOrNull { it.id == id && it.participantId == participant }

    private fun replaceInterview(saved: InterviewView) {
        val previous = (mutableInterviews.value as? ReadState.Ready)?.value.orEmpty()
        val updated = if (previous.any { it.id == saved.id }) {
            previous.map { if (it.id == saved.id) saved else it }
        } else listOf(saved) + previous
        interviewRequest++ // An older GET must not overwrite the committed record.
        mutableInterviews.value = ReadState.Ready(updated)
    }

    /** All new writes are single-shot; only the two POSTs may reuse a key after uncertainty. */
    fun startInterview(participantId: UUID) {
        if (mutableInterviewWrite.value is InterviewWriteState.Saving ||
            authorizedInterviewParticipant() != participantId) return
        val records = (mutableInterviews.value as? ReadState.Ready)?.value ?: return
        if (records.any { it.status == "DRAFT" }) return
        val selected = mutableInterviewSelection.value
        if (selected != null && records.none {
                it.id == selected && "START_NEXT_INTERVIEW" in it.availableActions
            }) return
        val operation = pendingInterviewStart?.takeIf { it.participant == participantId }
            ?: PendingInterviewStart(participantId, UUID.randomUUID().toString())
        pendingInterviewStart = operation
        val role = verifiedRole ?: return
        val currentGeneration = generation
        val requestId = ++interviewMutationRequest
        mutableInterviewWrite.value = InterviewWriteState.Saving(InterviewOperation.START)
        viewModelScope.launch {
            if (!ensureFreshToken {
                    generation == currentGeneration && requestId == interviewMutationRequest
                }) return@launch
            val result = repository.startInterview(participantId, role, operation.key)
            if (currentGeneration != generation || requestId != interviewMutationRequest ||
                authorizedInterviewParticipant() != participantId) return@launch
            when (result) {
                is RemoteResult.Success -> {
                    val saved = result.value
                    if (saved.participantId != participantId || saved.id == null || saved.status != "DRAFT") {
                        clear()
                        mutableProfessionalContext.value = ReadState.Forbidden
                    } else {
                        pendingInterviewStart = null
                        replaceInterview(saved)
                        mutableInterviewSelection.value = saved.id
                        mutableInterviewDraft.value = initialInterviewDraft(participantId, saved)
                        mutableInterviewWrite.value = InterviewWriteState.Success("Rozpoczęto wywiad.")
                    }
                }
                is RemoteResult.Failure -> handleInterviewFailure(result.error, InterviewOperation.START, participantId)
            }
        }
    }

    /** One versioned PUT, with all existing SELECT answers preserved. No automatic retry. */
    fun saveInterviewDraft() {
        val draft = mutableInterviewDraft.value ?: return
        if (authorizedInterviewParticipant() != draft.participantId ||
            mutableInterviewWrite.value is InterviewWriteState.Saving ||
            mutableInterviewWrite.value is InterviewWriteState.Conflict ||
            mutableInterviewWrite.value is InterviewWriteState.Uncertain) return
        val record = currentInterview(draft.participantId, draft.interviewId) ?: return
        if (draft.baseVersion != record.version) {
            mutableInterviewWrite.value = InterviewWriteState.Conflict(InterviewOperation.SAVE)
            return
        }
        val command = interviewSaveCommand(record, draft)
        if (command == null) {
            mutableInterviewWrite.value = InterviewWriteState.Failed(
                "Sprawdź odpowiedzi liczbowe i daty (RRRR-MM-DD)."
            )
            return
        }
        if (!draft.dirty) return
        val role = verifiedRole ?: return
        val currentGeneration = generation
        val requestId = ++interviewMutationRequest
        mutableInterviewWrite.value = InterviewWriteState.Saving(InterviewOperation.SAVE)
        viewModelScope.launch {
            if (!ensureFreshToken {
                    generation == currentGeneration && requestId == interviewMutationRequest
                }) return@launch
            val result = repository.saveInterviewDraft(
                draft.participantId, draft.interviewId, role, command
            )
            if (currentGeneration != generation || requestId != interviewMutationRequest ||
                authorizedInterviewParticipant() != draft.participantId ||
                mutableInterviewSelection.value != draft.interviewId) return@launch
            when (result) {
                is RemoteResult.Success -> {
                    val saved = result.value
                    if (saved.participantId != draft.participantId ||
                        saved.id != draft.interviewId || saved.status != "DRAFT") {
                        clear()
                        mutableProfessionalContext.value = ReadState.Forbidden
                    } else {
                        replaceInterview(saved)
                        mutableInterviewDraft.value = initialInterviewDraft(draft.participantId, saved)
                        mutableInterviewWrite.value = InterviewWriteState.Success("Szkic zapisano na serwerze.")
                    }
                }
                is RemoteResult.Failure -> handleInterviewFailure(result.error, InterviewOperation.SAVE, draft.participantId)
            }
        }
    }

    /** Explicit review after 409 or an uncertain PUT; local edits win only for touched fields. */
    fun reconcileInterviewVersion() {
        val state = mutableInterviewWrite.value
        if (state !is InterviewWriteState.Conflict &&
            state !is InterviewWriteState.Uncertain) return
        val draft = mutableInterviewDraft.value ?: return
        if (authorizedInterviewParticipant() != draft.participantId) return
        val latest = currentInterview(draft.participantId, draft.interviewId) ?: return
        if (latest.status == "COMPLETED" &&
            state == InterviewWriteState.Uncertain(InterviewOperation.COMPLETE)) {
            pendingInterviewComplete = null
            mutableInterviewDraft.value = null
            mutableInterviewWrite.value = InterviewWriteState.Success("Wywiad został zakończony na serwerze.")
            return
        }
        val reconciled = reconcileInterviewDraft(draft, latest) ?: return
        mutableInterviewDraft.value = reconciled
        pendingInterviewComplete = null
        mutableInterviewWrite.value = InterviewWriteState.Idle
    }

    /** Completion requires a confirmed server-side draft, no local edits and all required answers. */
    fun completeInterview() {
        val draft = mutableInterviewDraft.value ?: return
        if (authorizedInterviewParticipant() != draft.participantId || draft.dirty ||
            mutableInterviewWrite.value is InterviewWriteState.Saving ||
            mutableInterviewWrite.value is InterviewWriteState.Conflict ||
            mutableInterviewWrite.value == InterviewWriteState.Uncertain(InterviewOperation.SAVE)) return
        val record = currentInterview(draft.participantId, draft.interviewId) ?: return
        if (!canCompleteInterview(record)) return
        if (draft.baseVersion != record.version) {
            mutableInterviewWrite.value = InterviewWriteState.Conflict(InterviewOperation.COMPLETE)
            return
        }
        val version = record.version ?: return
        val operation = pendingInterviewComplete?.takeIf {
            it.participant == draft.participantId && it.interview == draft.interviewId
        } ?: PendingInterviewComplete(
            draft.participantId, draft.interviewId, version, UUID.randomUUID().toString()
        )
        pendingInterviewComplete = operation
        val role = verifiedRole ?: return
        val currentGeneration = generation
        val requestId = ++interviewMutationRequest
        mutableInterviewWrite.value = InterviewWriteState.Saving(InterviewOperation.COMPLETE)
        viewModelScope.launch {
            if (!ensureFreshToken {
                    generation == currentGeneration && requestId == interviewMutationRequest
                }) return@launch
            val result = repository.completeInterview(
                draft.participantId, draft.interviewId, role,
                InterviewVersionCommand(operation.version), operation.key
            )
            if (currentGeneration != generation || requestId != interviewMutationRequest ||
                authorizedInterviewParticipant() != draft.participantId ||
                mutableInterviewSelection.value != draft.interviewId) return@launch
            when (result) {
                is RemoteResult.Success -> {
                    val saved = result.value
                    if (saved.participantId != draft.participantId ||
                        saved.id != draft.interviewId || saved.status != "COMPLETED") {
                        clear()
                        mutableProfessionalContext.value = ReadState.Forbidden
                    } else {
                        pendingInterviewComplete = null
                        replaceInterview(saved)
                        mutableInterviewDraft.value = null
                        mutableInterviewWrite.value = InterviewWriteState.Success("Wywiad zakończony.")
                    }
                }
                is RemoteResult.Failure -> handleInterviewFailure(result.error, InterviewOperation.COMPLETE, draft.participantId)
            }
        }
    }

    private fun handleInterviewFailure(error: RemoteError, operation: InterviewOperation, participantId: UUID) {
        when (error) {
            RemoteError.Unauthorized -> { clear(); invalidateSession() }
            RemoteError.Forbidden -> { clear(); mutableProfessionalContext.value = ReadState.Forbidden }
            RemoteError.Conflict -> {
                if (operation == InterviewOperation.START) pendingInterviewStart = null
                if (operation == InterviewOperation.COMPLETE) pendingInterviewComplete = null
                mutableInterviewWrite.value = InterviewWriteState.Conflict(operation)
                loadInterviews(participantId)
            }
            is RemoteError.Network, is RemoteError.Serialization ->
                mutableInterviewWrite.value = InterviewWriteState.Uncertain(operation)
            is RemoteError.Http -> {
                if (error.status >= 500) mutableInterviewWrite.value = InterviewWriteState.Uncertain(operation)
                else {
                    if (operation == InterviewOperation.START) pendingInterviewStart = null
                    if (operation == InterviewOperation.COMPLETE) pendingInterviewComplete = null
                    mutableInterviewWrite.value = InterviewWriteState.Failed("Serwer odrzucił zapis wywiadu.")
                }
            }
        }
    }

    fun loadWorkspace(participantId: UUID) {
        if (selectedParticipantId() != participantId) return
        val requestId = ++workspaceRequest
        timelineRequest++
        mutableTimeline.value = ReadState.Idle
        read(mutableWorkspace, skipIfLoading = false,
            isCurrent = { requestId == workspaceRequest && selectedParticipantId() == participantId }) {
            repository.workspace(participantId)
        }
    }

    fun loadTimeline(participantId: UUID) {
        if (selectedParticipantId() != participantId || mutableWorkspace.value !is ReadState.Ready) return
        val requestId = ++timelineRequest
        read(mutableTimeline, skipIfLoading = false,
            isCurrent = { requestId == timelineRequest && selectedParticipantId() == participantId }) {
            when (val result = repository.timeline(participantId)) {
                is RemoteResult.Success -> RemoteResult.Success(
                    TimelineFeed(
                        items = visibleTimelineEvents(result.value.items),
                        nextCursor = result.value.nextCursor,
                        from = result.value.from?.toString(),
                        to = result.value.to?.toString()
                    )
                )
                is RemoteResult.Failure -> result
            }
        }
    }

    /** Cursor and first-page time bounds are reused so paging does not shift with the server clock. */
    fun loadMoreTimeline(participantId: UUID) {
        if (selectedParticipantId() != participantId) return
        val feed = (mutableTimeline.value as? ReadState.Ready)?.value ?: return
        val cursor = feed.nextCursor ?: return
        if (feed.loadingMore) return
        val currentGeneration = generation
        val requestId = timelineRequest
        mutableTimeline.value = ReadState.Ready(feed.copy(loadingMore = true, loadMoreFailed = false))
        viewModelScope.launch {
            if (!ensureFreshToken {
                generation == currentGeneration && requestId == timelineRequest &&
                    selectedParticipantId() == participantId
            }) return@launch
            val result = repository.timeline(participantId, cursor = cursor, from = feed.from, to = feed.to)
            if (generation != currentGeneration || requestId != timelineRequest ||
                selectedParticipantId() != participantId) return@launch
            mutableTimeline.value = when (result) {
                is RemoteResult.Success -> {
                    val known = feed.items.mapNotNull { it.eventId }.toHashSet()
                    val more = visibleTimelineEvents(result.value.items)
                        .filter { it.eventId != null && known.add(it.eventId) }
                    ReadState.Ready(feed.copy(
                        items = feed.items + more,
                        nextCursor = result.value.nextCursor?.takeUnless { it == cursor },
                        loadingMore = false,
                        loadMoreFailed = false
                    ))
                }
                is RemoteResult.Failure -> when (result.error) {
                    RemoteError.Unauthorized -> {
                        clear()
                        invalidateSession()
                        ReadState.Idle
                    }
                    RemoteError.Forbidden -> {
                        clear()
                        mutableProfessionalContext.value = ReadState.Forbidden
                        ReadState.Forbidden
                    }
                    else -> ReadState.Ready(feed.copy(loadingMore = false, loadMoreFailed = true))
                }
            }
        }
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
    fun loadClients() {
        val requestId = ++clientRequest
        read(mutableClients, skipIfLoading = false, isCurrent = { requestId == clientRequest }) {
            repository.clients()
        }
    }

    fun openClientForm() {
        if (mutableCreateClient.value == CreateClientState.Saving) return
        mutableCreateClient.value = CreateClientState.Idle
        mutableClientDraft.value = ClientFormDraft(open = true)
    }

    fun updateClientForm(next: ClientFormDraft) {
        if (mutableCreateClient.value == CreateClientState.Saving || !mutableClientDraft.value.open ||
            !next.open) return
        mutableClientDraft.value = next
        if (mutableCreateClient.value is CreateClientState.Failed) {
            mutableCreateClient.value = CreateClientState.Idle
        }
    }

    fun resetClientForm() {
        if (mutableCreateClient.value == CreateClientState.Saving) return
        mutableCreateClient.value = CreateClientState.Idle
        mutableClientDraft.value = ClientFormDraft()
    }

    /** The same key is kept for manual retries of an unchanged draft, never an automatic POST retry. */
    fun createClient(command: ClientCommand, key: String) {
        if (mutableCreateClient.value == CreateClientState.Saving) return
        val currentGeneration = generation
        mutableCreateClient.value = CreateClientState.Saving
        viewModelScope.launch {
            if (!ensureFreshToken { currentGeneration == generation }) return@launch
            val result = repository.createClient(command, key)
            if (currentGeneration != generation) return@launch
            when (result) {
                is RemoteResult.Success -> {
                    val created = result.value
                    val id = created.participantId
                    if (id == null) {
                        mutableCreateClient.value = CreateClientState.Failed("Serwer nie zwrócił identyfikatora kartoteki.")
                        return@launch
                    }
                    clientRequest++ // An older list GET must not remove the new record.
                    val old = (mutableClients.value as? ReadState.Ready)?.value.orEmpty()
                    mutableClients.value = ReadState.Ready(old.filterNot { it.participantId == id } + created)
                    mutableCreateClient.value = CreateClientState.Saved(id)
                }
                is RemoteResult.Failure -> when (val error = result.error) {
                    RemoteError.Unauthorized -> {
                        clear()
                        invalidateSession()
                    }
                    RemoteError.Forbidden -> {
                        clear()
                        mutableProfessionalContext.value = ReadState.Forbidden
                        mutableCreateClient.value = CreateClientState.Failed("Brak uprawnień do tworzenia kartotek.")
                    }
                    RemoteError.Conflict -> mutableCreateClient.value = CreateClientState.Failed(
                        "Serwer odrzucił utworzenie kartoteki (409). Sprawdź weryfikację specjalisty i konfigurację zgód środowiska."
                    )
                    is RemoteError.Network -> mutableCreateClient.value = CreateClientState.Failed(
                        "Brak odpowiedzi. Kartoteka mogła zostać zapisana. Ponów bez zmiany formularza — użyjemy tego samego klucza żądania."
                    )
                    is RemoteError.Http -> mutableCreateClient.value = CreateClientState.Failed(
                        if (error.status == 400) "Sprawdź dane formularza." else "Nie udało się dodać kartoteki."
                    )
                    else -> mutableCreateClient.value = CreateClientState.Failed("Nie udało się dodać kartoteki.")
                }
            }
        }
    }

    /** Notes are fetched only on opening the S07 notes tab, in a fresh authorized context. */
    fun loadNotes(participantId: UUID) {
        if (mutableNoteWrite.value is NoteWriteState.Saving ||
            authorizedInterviewParticipant() != participantId) return
        val role = verifiedRole ?: return
        val requestId = ++noteRequest
        read(mutableNotes, skipIfLoading = false,
            isCurrent = {
                requestId == noteRequest && verifiedRole == role &&
                    authorizedInterviewParticipant() == participantId
            }) { repository.notes(participantId, role) }
    }

    private fun resetNoteEditor() {
        noteMutationRequest++
        pendingNotePost = null
        mutableNoteSelection.value = null
        mutableNoteDraft.value = null
        mutableNoteWrite.value = NoteWriteState.Idle
    }

    fun closeNote() {
        if (mutableNoteWrite.value is NoteWriteState.Saving) return
        resetNoteEditor()
    }

    fun openNewNote(participantId: UUID) {
        if (authorizedInterviewParticipant() != participantId ||
            mutableNoteWrite.value is NoteWriteState.Saving ||
            mutableNoteDraft.value?.hasUnsavedContent == true) return
        val selection = mutableSelection.value as? ContextSelection.Appointment
        val boundAppointment = if (selection?.participantId == participantId &&
            (mutableAppointment.value as? ReadState.Ready)?.value?.appointmentId == selection.appointmentId
        ) selection.appointmentId else null
        resetNoteEditor()
        mutableNoteDraft.value = NoteDraft(participantId,
            values = NoteValues(appointmentId = boundAppointment))
    }

    fun openNote(participantId: UUID, noteId: UUID) {
        if (authorizedInterviewParticipant() != participantId ||
            mutableNoteWrite.value is NoteWriteState.Saving ||
            mutableNoteDraft.value?.hasUnsavedContent == true) return
        val note = currentNote(participantId, noteId) ?: return
        resetNoteEditor()
        mutableNoteSelection.value = noteId
        if (note.status == "DRAFT" && "UPDATE" in note.availableActions) {
            mutableNoteDraft.value = noteFromServer(participantId, note)
        }
    }

    private fun currentNote(participantId: UUID, noteId: UUID): NoteView? =
        (mutableNotes.value as? ReadState.Ready)?.value
            ?.firstOrNull { it.id == noteId && it.participantId == participantId }

    internal fun changeNoteDraft(next: NoteValues) {
        val current = mutableNoteDraft.value ?: return
        if (authorizedInterviewParticipant() != current.participantId ||
            mutableNoteWrite.value is NoteWriteState.Saving ||
            mutableNoteWrite.value is NoteWriteState.Conflict ||
            mutableNoteWrite.value is NoteWriteState.Uncertain ||
            next.appointmentId != current.values.appointmentId ||
            next.title.length > 160 || next.content.length > 8000 ||
            next.category !in NOTE_CATEGORIES) return
        mutableNoteDraft.value = current.copy(values = next)
        if (mutableNoteWrite.value is NoteWriteState.Failed ||
            mutableNoteWrite.value is NoteWriteState.Success) {
            mutableNoteWrite.value = NoteWriteState.Idle
        }
    }

    private fun replaceNote(saved: NoteView) {
        val previous = (mutableNotes.value as? ReadState.Ready)?.value.orEmpty()
        noteRequest++
        mutableNotes.value = ReadState.Ready(
            if (previous.any { it.id == saved.id }) previous.map {
                if (it.id == saved.id) saved else it
            } else listOf(saved) + previous
        )
    }

    /** Creating uses a stable key. A failed response never triggers an automatic POST retry. */
    fun createNote() {
        val draft = mutableNoteDraft.value ?: return
        if (draft.noteId != null || authorizedInterviewParticipant() != draft.participantId ||
            mutableNoteWrite.value is NoteWriteState.Saving ||
            mutableNoteWrite.value is NoteWriteState.Conflict) return
        val payload = createNotePayload(draft) ?: run {
            mutableNoteWrite.value = NoteWriteState.Failed("Uzupełnij tytuł, kategorię i treść notatki.")
            return
        }
        val role = verifiedRole ?: return
        val currentGeneration = generation
        val request = ++noteMutationRequest
        mutableNoteWrite.value = NoteWriteState.Saving(NoteOperation.CREATE)
        viewModelScope.launch {
            if (!ensureFreshToken { generation == currentGeneration && request == noteMutationRequest }) return@launch
            val result = repository.createNote(draft.participantId, role, draft.requestKey, payload)
            if (currentGeneration != generation || request != noteMutationRequest ||
                authorizedInterviewParticipant() != draft.participantId) return@launch
            when (result) {
                is RemoteResult.Success -> {
                    val saved = result.value
                    if (saved.id == null || saved.participantId != draft.participantId ||
                        saved.status != "DRAFT") {
                        clear()
                        mutableProfessionalContext.value = ReadState.Forbidden
                    } else {
                        replaceNote(saved)
                        mutableNoteSelection.value = saved.id
                        mutableNoteDraft.value = noteFromServer(draft.participantId, saved)
                        mutableNoteWrite.value = NoteWriteState.Success("Notatka zapisana na serwerze.")
                    }
                }
                is RemoteResult.Failure -> handleNoteFailure(result.error, NoteOperation.CREATE, draft.participantId)
            }
        }
    }

    /** PUT is versioned and never automatically retried, including after a timeout. */
    fun updateNote() {
        val draft = mutableNoteDraft.value ?: return
        val id = draft.noteId ?: return
        if (!draft.dirty || authorizedInterviewParticipant() != draft.participantId ||
            mutableNoteWrite.value is NoteWriteState.Saving ||
            mutableNoteWrite.value is NoteWriteState.Conflict ||
            mutableNoteWrite.value is NoteWriteState.Uncertain) return
        val record = currentNote(draft.participantId, id) ?: return
        if (draft.version != record.version) {
            mutableNoteWrite.value = NoteWriteState.Conflict(NoteOperation.UPDATE)
            loadNotes(draft.participantId)
            return
        }
        val command = updateNotePayload(draft, record) ?: run {
            mutableNoteWrite.value = NoteWriteState.Failed("Uzupełnij tytuł, kategorię i treść notatki.")
            return
        }
        val role = verifiedRole ?: return
        val currentGeneration = generation
        val request = ++noteMutationRequest
        mutableNoteWrite.value = NoteWriteState.Saving(NoteOperation.UPDATE)
        viewModelScope.launch {
            if (!ensureFreshToken { generation == currentGeneration && request == noteMutationRequest }) return@launch
            val result = repository.updateNote(draft.participantId, id, role, command)
            if (currentGeneration != generation || request != noteMutationRequest ||
                authorizedInterviewParticipant() != draft.participantId ||
                mutableNoteSelection.value != id) return@launch
            when (result) {
                is RemoteResult.Success -> {
                    val saved = result.value
                    if (saved.id != id || saved.participantId != draft.participantId || saved.status != "DRAFT") {
                        clear()
                        mutableProfessionalContext.value = ReadState.Forbidden
                    } else {
                        replaceNote(saved)
                        mutableNoteDraft.value = noteFromServer(draft.participantId, saved)
                        mutableNoteWrite.value = NoteWriteState.Success("Zapisano zmiany notatki.")
                    }
                }
                is RemoteResult.Failure -> handleNoteFailure(result.error, NoteOperation.UPDATE, draft.participantId)
            }
        }
    }

    /** Rebase only after an explicit review of the freshly downloaded note. */
    fun reconcileNote() {
        val state = mutableNoteWrite.value
        if (state !is NoteWriteState.Conflict && state !is NoteWriteState.Uncertain) return
        val draft = mutableNoteDraft.value
        val participant = authorizedInterviewParticipant() ?: return
        val id = mutableNoteSelection.value ?: return
        if (draft != null && (draft.participantId != participant || draft.noteId != id)) return
        val latest = currentNote(participant, id) ?: return
        if (latest.status == "FINAL" &&
            (state is NoteWriteState.Conflict && state.operation == NoteOperation.FINALISE ||
                state == NoteWriteState.Uncertain(NoteOperation.FINALISE))) {
            pendingNotePost = null
            mutableNoteDraft.value = null
            mutableNoteWrite.value = NoteWriteState.Success("Notatka została sfinalizowana na serwerze.")
            return
        }
        if (latest.status == "ARCHIVED" &&
            (state is NoteWriteState.Conflict && state.operation == NoteOperation.ARCHIVE ||
                state == NoteWriteState.Uncertain(NoteOperation.ARCHIVE))) {
            pendingNotePost = null
            mutableNoteDraft.value = null
            mutableNoteWrite.value = NoteWriteState.Success("Notatka została zarchiwizowana.")
            return
        }
        val rebased = rebaseNoteDraft(draft ?: return, latest) ?: return
        mutableNoteDraft.value = rebased
        pendingNotePost = null
        mutableNoteWrite.value = NoteWriteState.Idle
    }

    /** Finalization and archiving are idempotent POSTs with confirmation in the UI. */
    fun finaliseNote() = noteLifecycle(NoteOperation.FINALISE)
    fun archiveNote() = noteLifecycle(NoteOperation.ARCHIVE)

    private fun noteLifecycle(kind: NoteOperation) {
        val id = mutableNoteSelection.value ?: return
        val participant = authorizedInterviewParticipant() ?: return
        val current = currentNote(participant, id) ?: return
        if (mutableNoteDraft.value?.dirty == true ||
            mutableNoteWrite.value is NoteWriteState.Saving ||
            mutableNoteWrite.value is NoteWriteState.Conflict ||
            mutableNoteWrite.value == NoteWriteState.Uncertain(NoteOperation.UPDATE)) return
        val action = if (kind == NoteOperation.FINALISE) "FINALISE" else "ARCHIVE"
        if (action !in current.availableActions || current.version == null) return
        val operation = pendingNotePost?.takeIf {
            it.operation == kind && it.participant == participant && it.noteId == id
        } ?: NotePost(kind, participant, id, current.version, UUID.randomUUID().toString())
        pendingNotePost = operation
        val role = verifiedRole ?: return
        val currentGeneration = generation
        val request = ++noteMutationRequest
        mutableNoteWrite.value = NoteWriteState.Saving(kind)
        viewModelScope.launch {
            if (!ensureFreshToken { generation == currentGeneration && request == noteMutationRequest }) return@launch
            val result = if (kind == NoteOperation.FINALISE) {
                repository.finaliseNote(participant, id, role, InterviewVersionCommand(operation.version), operation.key)
            } else {
                repository.archiveNote(participant, id, role, InterviewVersionCommand(operation.version), operation.key)
            }
            if (currentGeneration != generation || request != noteMutationRequest ||
                authorizedInterviewParticipant() != participant ||
                mutableNoteSelection.value != id) return@launch
            when (result) {
                is RemoteResult.Success -> {
                    val saved = result.value
                    val status = if (kind == NoteOperation.FINALISE) "FINAL" else "ARCHIVED"
                    if (saved.id != id || saved.participantId != participant || saved.status != status) {
                        clear()
                        mutableProfessionalContext.value = ReadState.Forbidden
                    } else {
                        pendingNotePost = null
                        replaceNote(saved)
                        mutableNoteDraft.value = null
                        mutableNoteWrite.value = NoteWriteState.Success(
                            if (kind == NoteOperation.FINALISE) "Notatka sfinalizowana." else "Notatka zarchiwizowana."
                        )
                    }
                }
                is RemoteResult.Failure -> handleNoteFailure(result.error, kind, participant)
            }
        }
    }

    private fun handleNoteFailure(error: RemoteError, kind: NoteOperation, participant: UUID) {
        when (error) {
            RemoteError.Unauthorized -> { clear(); invalidateSession() }
            RemoteError.Forbidden -> { clear(); mutableProfessionalContext.value = ReadState.Forbidden }
            RemoteError.Conflict -> {
                pendingNotePost = null
                mutableNoteWrite.value = if (kind == NoteOperation.CREATE) {
                    NoteWriteState.Failed("Serwer odrzucił utworzenie notatki (409). Sprawdź listę przed ponowieniem.")
                } else NoteWriteState.Conflict(kind)
                loadNotes(participant)
            }
            is RemoteError.Network, is RemoteError.Serialization ->
                mutableNoteWrite.value = NoteWriteState.Uncertain(kind)
            is RemoteError.Http -> {
                if (error.status >= 500) mutableNoteWrite.value = NoteWriteState.Uncertain(kind)
                else {
                    pendingNotePost = null
                    mutableNoteWrite.value = NoteWriteState.Failed("Serwer odrzucił operację na notatce.")
                }
            }
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

    fun clear() {
        generation++
        profileRequest++
        verifiedRole = null
        mutableProfessionalContext.value = ReadState.Idle
        todayRequest++
        clientRequest++
        resetParticipantReads()
        resetAppointmentReads()
        resetSchedule()
        mutableClients.value = ReadState.Idle
        mutableCreateClient.value = CreateClientState.Idle
        mutableClientDraft.value = ClientFormDraft()
        mutableClientQuery.value = ""
        resetInterviewEditor()
        resetNoteEditor()
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
            if (!ensureFreshToken { currentGeneration == generation && isCurrent() }) return@launch
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
                        mutableProfessionalContext.value = ReadState.Forbidden
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
