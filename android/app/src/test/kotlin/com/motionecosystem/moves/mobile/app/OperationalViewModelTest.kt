package com.motionecosystem.moves.mobile.app

import com.motionecosystem.moves.mobile.api.*
import com.motionecosystem.moves.mobile.repository.SpecialistRepository
import java.util.UUID
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class OperationalViewModelTest {
    @Test fun deniedAccessClearsPreviousProjectionWithoutLoggingOut() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val repository = StubRepository()
            val vm = OperationalViewModel(repository, { "access-token" }, {})
            vm.loadToday()
            advanceUntilIdle()
            assertTrue(vm.today.value is ReadState.Ready)
            repository.participantResult = RemoteResult.Failure(RemoteError.Forbidden)
            vm.loadParticipants()
            advanceUntilIdle()
            assertEquals(ReadState.Idle, vm.today.value)
            assertEquals(ReadState.Forbidden, vm.participants.value)
        } finally { Dispatchers.resetMain() }
    }

    @Test fun logoutDiscardsInFlightDataAndParticipantContext() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val repository = StubRepository()
            repository.pendingToday = CompletableDeferred()
            val vm = OperationalViewModel(repository, { "access-token" }, {})
            vm.selectParticipant(UUID.randomUUID())
            vm.loadToday()
            runCurrent()
            vm.clear()
            repository.pendingToday!!.complete(RemoteResult.Success(TodayView()))
            advanceUntilIdle()
            assertEquals(ReadState.Idle, vm.today.value)
            assertNull(vm.selection.value)
        } finally { Dispatchers.resetMain() }
    }

    @Test fun latestChosenDateWinsOverSlowerPreviousResponse() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val firstDay = java.time.LocalDate.of(2026, 11, 15)
            val secondDay = firstDay.plusDays(1)
            val first = CompletableDeferred<RemoteResult<TodayView>>()
            val second = CompletableDeferred<RemoteResult<TodayView>>()
            val repository = StubRepository()
            repository.pendingDates[firstDay] = first
            repository.pendingDates[secondDay] = second
            val vm = OperationalViewModel(repository, { "access-token" }, {})
            vm.chooseDate(firstDay)
            runCurrent()
            vm.chooseDate(secondDay)
            runCurrent()
            second.complete(RemoteResult.Success(TodayView(localDate = secondDay)))
            advanceUntilIdle()
            first.complete(RemoteResult.Success(TodayView(localDate = firstDay)))
            advanceUntilIdle()
            assertEquals(secondDay, (vm.today.value as ReadState.Ready).value.localDate)
            assertEquals(secondDay, vm.requestedDate.value)
        } finally { Dispatchers.resetMain() }
    }

    @Test fun creatingClientUsesOnePostPerAttemptAndPreservesKeyForExplicitRetry() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val repository = StubRepository()
            val vm = OperationalViewModel(repository, { "access-token" }, {})
            val requestKey = UUID.randomUUID().toString()
            val command = ClientCommand(displayName = "Nowy uczestnik")
            vm.createClient(command, requestKey) {}
            advanceUntilIdle()
            assertTrue(vm.createClientState.value is CreateClientState.Failed)
            assertEquals(listOf(requestKey), repository.creationKeys)
            val id = UUID.randomUUID()
            repository.createResult = RemoteResult.Success(
                ClientView(participantId = id, displayName = command.displayName, accessStatus = "NO_ACCOUNT")
            )
            var opened: UUID? = null
            vm.createClient(command, requestKey) { opened = it }
            advanceUntilIdle()
            assertEquals(listOf(requestKey, requestKey), repository.creationKeys)
            assertEquals(id, opened)
            assertEquals("NO_ACCOUNT", (vm.clients.value as ReadState.Ready).value.single().accessStatus)
        } finally { Dispatchers.resetMain() }
    }

    @Test fun participantSwitchDiscardsLateWorkspaceResponse() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val original = UUID.randomUUID()
            val replacement = UUID.randomUUID()
            val repository = StubRepository()
            val slow = CompletableDeferred<RemoteResult<WorkspaceView>>()
            repository.pendingWorkspaces[original] = slow
            val vm = OperationalViewModel(repository, { "access-token" }, {})
            vm.selectParticipant(original)
            vm.loadWorkspace(original)
            runCurrent()
            vm.selectParticipant(replacement)
            vm.loadWorkspace(replacement)
            advanceUntilIdle()
            slow.complete(RemoteResult.Success(WorkspaceView(
                participant = WorkspaceParticipant(participantId = original, displayName = "stale")
            )))
            advanceUntilIdle()
            assertEquals(replacement, (vm.workspace.value as ReadState.Ready).value.participant?.participantId)
        } finally { Dispatchers.resetMain() }
    }

    @Test fun timelinePaginationPreservesBoundsAndOmitsMeasurements() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val id = UUID.randomUUID()
            val from = java.time.OffsetDateTime.parse("2026-11-01T00:00:00Z")
            val to = java.time.OffsetDateTime.parse("2026-11-15T00:00:00Z")
            val repository = StubRepository()
            repository.timelineResults[null] = RemoteResult.Success(
                TimelinePage(
                    items = listOf(
                        TimelineEvent(eventId = "a", category = "APPOINTMENT"),
                        TimelineEvent(eventId = "hidden", category = "MEASUREMENT")
                    ),
                    nextCursor = "cursor-1", from = from, to = to
                )
            )
            repository.timelineResults["cursor-1"] = RemoteResult.Success(
                TimelinePage(items = listOf(
                    TimelineEvent(eventId = "a", category = "APPOINTMENT"),
                    TimelineEvent(eventId = "b", category = "GOAL")
                ))
            )
            val vm = OperationalViewModel(repository, { "access-token" }, {})
            vm.selectParticipant(id)
            vm.loadWorkspace(id)
            advanceUntilIdle()
            vm.loadTimeline(id)
            advanceUntilIdle()
            vm.loadMoreTimeline(id)
            advanceUntilIdle()
            val events = (vm.timeline.value as ReadState.Ready).value.items
            assertEquals(listOf("a", "b"), events.map { it.eventId })
            assertEquals(listOf(
                Triple(null, null, null),
                Triple("cursor-1", from.toString(), to.toString())
            ), repository.timelineRequests)
        } finally { Dispatchers.resetMain() }
    }

    @Test fun foregroundRecheckDropsPreviousParticipantAndDiscardsLateResponses() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val id = UUID.randomUUID()
            val repository = StubRepository()
            val vm = OperationalViewModel(repository, { "token" }, {})
            vm.revalidateAccess()
            advanceUntilIdle()
            assertEquals(ActingContext.TRAINER, (vm.professionalContext.value as ReadState.Ready).value)
            val delayed = CompletableDeferred<RemoteResult<WorkspaceView>>()
            repository.pendingWorkspaces[id] = delayed
            vm.selectParticipant(id)
            vm.loadWorkspace(id)
            runCurrent()
            repository.onboardingResult = RemoteResult.Success(
                MobileOnboardingState("READY", "SPECIALIST", MobileSpecialistProfile(ActingContext.PHYSIOTHERAPIST))
            )
            vm.revalidateAccess()
            runCurrent()
            assertNull(vm.selection.value)
            delayed.complete(RemoteResult.Success(WorkspaceView(participant = WorkspaceParticipant(id, "old"))))
            advanceUntilIdle()
            assertEquals(ReadState.Idle, vm.workspace.value)
            assertEquals(ActingContext.PHYSIOTHERAPIST, (vm.professionalContext.value as ReadState.Ready).value)
        } finally { Dispatchers.resetMain() }
    }

    @Test fun participantRevocationBlocksOldWorkspaceButKeepsOidcSession() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val repository = StubRepository()
            var invalidated = false
            val vm = OperationalViewModel(repository, { "token" }, { invalidated = true })
            vm.revalidateAccess()
            advanceUntilIdle()
            val participantId = UUID.randomUUID()
            vm.selectParticipant(participantId)
            vm.loadWorkspace(participantId)
            advanceUntilIdle()
            assertTrue(vm.workspace.value is ReadState.Ready)
            repository.pendingWorkspaces[participantId] = CompletableDeferred(RemoteResult.Failure(RemoteError.Forbidden))
            vm.loadWorkspace(participantId)
            advanceUntilIdle()
            assertEquals(ReadState.Forbidden, vm.professionalContext.value)
            assertEquals(ReadState.Forbidden, vm.workspace.value)
            assertNull(vm.selection.value)
            assertEquals(ReadState.Idle, vm.timeline.value)
            assertFalse(invalidated)
        } finally { Dispatchers.resetMain() }
    }

    @Test fun revokedOrUnavailableProfileNeverRevealsOldDataAndDoesNotLogOutOn403() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val repository = StubRepository()
            var invalidated = false
            val vm = OperationalViewModel(repository, { "token" }, { invalidated = true })
            vm.revalidateAccess()
            advanceUntilIdle()
            vm.loadToday()
            advanceUntilIdle()
            assertTrue(vm.today.value is ReadState.Ready)
            repository.onboardingResult = RemoteResult.Failure(RemoteError.Forbidden)
            vm.revalidateAccess()
            advanceUntilIdle()
            assertEquals(ReadState.Forbidden, vm.professionalContext.value)
            assertEquals(ReadState.Idle, vm.today.value)
            assertFalse(invalidated)
        } finally { Dispatchers.resetMain() }
    }

    @Test fun startRetryAfterUncertainResponseUsesSameKeyAndNeverAutoRepeats() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val repository = StubRepository()
            val id = UUID.randomUUID()
            val participant = UUID.randomUUID()
            repository.appointmentValue = AppointmentView(
                appointmentId = id, participantId = participant, status = AppointmentStatus.SCHEDULED,
                version = 7, availableActions = listOf("START")
            )
            repository.startResults.add(RemoteResult.Failure(RemoteError.Network(java.io.IOException("lost response"))))
            repository.startResults.add(RemoteResult.Success(repository.appointmentValue!!.copy(
                status = AppointmentStatus.IN_PROGRESS, version = 8, availableActions = listOf("COMPLETE")
            )))
            val vm = OperationalViewModel(repository, { "token" }, {})
            vm.selectAppointment(participant, id)
            vm.loadAppointment(id)
            advanceUntilIdle()
            vm.startAppointment(id)
            vm.startAppointment(id) // second click while saving is ignored
            advanceUntilIdle()
            assertEquals(AppointmentStartState.UNCERTAIN, vm.appointmentStart.value)
            assertEquals(1, repository.startRequests.size)
            vm.startAppointment(id)
            advanceUntilIdle()
            assertEquals(2, repository.startRequests.size)
            assertEquals(repository.startRequests[0], repository.startRequests[1])
            assertEquals(7L, repository.startRequests[0].second)
            assertEquals(AppointmentStatus.IN_PROGRESS, (vm.appointment.value as ReadState.Ready).value.status)
            assertEquals(AppointmentStartState.IDLE, vm.appointmentStart.value)
        } finally { Dispatchers.resetMain() }
    }

    @Test fun appointmentConflictReloadsServerStateAndNewVersion() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val repository = StubRepository()
            val id = UUID.randomUUID()
            val participant = UUID.randomUUID()
            repository.appointmentValue = AppointmentView(id, participantId = participant,
                status = AppointmentStatus.SCHEDULED, version = 2, availableActions = listOf("START"))
            repository.startResults.add(RemoteResult.Failure(RemoteError.Conflict))
            val vm = OperationalViewModel(repository, { "token" }, {})
            vm.selectAppointment(participant, id)
            vm.loadAppointment(id)
            advanceUntilIdle()
            repository.appointmentValue = repository.appointmentValue!!.copy(version = 3)
            vm.startAppointment(id)
            advanceUntilIdle()
            assertEquals(listOf(2L), repository.startRequests.map { it.second })
            assertEquals(3L, (vm.appointment.value as ReadState.Ready).value.version)
            assertEquals(AppointmentStartState.CONFLICT, vm.appointmentStart.value)
        } finally { Dispatchers.resetMain() }
    }

    @Test fun changingAppointmentOrRevokingAccessDropsLateDetailsAndMutation() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val repository = StubRepository()
            val participant = UUID.randomUUID()
            val first = UUID.randomUUID()
            val second = UUID.randomUUID()
            val pending = CompletableDeferred<RemoteResult<AppointmentView>>()
            repository.pendingAppointment = pending
            val vm = OperationalViewModel(repository, { "token" }, {})
            vm.selectAppointment(participant, first)
            vm.loadAppointment(first)
            runCurrent()
            repository.pendingAppointment = null
            repository.appointmentValue = AppointmentView(second, participantId = participant,
                status = AppointmentStatus.CONFIRMED, version = 1)
            vm.selectAppointment(participant, second)
            vm.loadAppointment(second)
            runCurrent()
            pending.complete(RemoteResult.Success(AppointmentView(first, participantId = participant,
                status = AppointmentStatus.IN_PROGRESS)))
            advanceUntilIdle()
            assertEquals(second, (vm.appointment.value as ReadState.Ready).value.appointmentId)
            repository.appointmentValue = repository.appointmentValue!!.copy(participantId = UUID.randomUUID())
            vm.loadAppointment(second)
            advanceUntilIdle()
            assertEquals(ReadState.Forbidden, vm.professionalContext.value)
            assertEquals(ReadState.Forbidden, vm.appointment.value)
            assertNull(vm.selection.value)
        } finally { Dispatchers.resetMain() }
    }

    @Test fun scheduleCreateUncertainResultRequiresManualRetryWithIdenticalKey() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val repository = StubRepository()
            val participantId = UUID.randomUUID()
            val start = java.time.OffsetDateTime.parse("2026-11-19T09:00:00Z")
            val slot = BookableSlotView(start, start.plusMinutes(50))
            repository.scheduleToday = TodayView(localDate = java.time.LocalDate.parse("2026-11-19"),
                timeZoneId = "Europe/Warsaw", bookableSlots = listOf(slot))
            repository.clientResult = RemoteResult.Success(listOf(
                ClientView(participantId = participantId, displayName = "P.",
                    availableActions = listOf("SCHEDULE_APPOINTMENT"))))
            val createdId = UUID.randomUUID()
            repository.createAppointmentResults.add(RemoteResult.Failure(RemoteError.Network(java.io.IOException("lost"))))
            repository.createAppointmentResults.add(RemoteResult.Success(
                AppointmentView(appointmentId = createdId, participantId = participantId,
                    status = AppointmentStatus.SCHEDULED, version = 0)))
            val vm = OperationalViewModel(repository, { "token" }, {})
            vm.openNewSchedule(participantId)
            advanceUntilIdle()
            val draft = AppointmentSaveCommand(participantId, slot.startsAt!!, slot.endsAt!!,
                AppointmentType.CONSULTATION, AppointmentLocationMode.REMOTE)
            vm.saveSchedule(draft)
            vm.saveSchedule(draft) // second press during in-flight save is ignored
            advanceUntilIdle()
            assertEquals(ScheduleSaveState.Uncertain, vm.scheduleSave.value)
            assertEquals(1, repository.createAppointmentCalls.size)
            vm.saveSchedule(draft)
            advanceUntilIdle()
            assertEquals(2, repository.createAppointmentCalls.size)
            assertEquals(repository.createAppointmentCalls[0], repository.createAppointmentCalls[1])
            assertEquals(createdId, (vm.scheduleSave.value as ScheduleSaveState.Saved).appointment.appointmentId)
        } finally { Dispatchers.resetMain() }
    }

    @Test fun scheduleEditConflictRefreshesAuthorizedAppointmentAndDoesNotAutoRetry() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val repository = StubRepository()
            val participantId = UUID.randomUUID()
            val appointmentId = UUID.randomUUID()
            val start = java.time.OffsetDateTime.parse("2026-11-20T10:00:00Z")
            repository.scheduleToday = TodayView(localDate = java.time.LocalDate.parse("2026-11-20"),
                bookableSlots = listOf(BookableSlotView(start, start.plusMinutes(50))))
            repository.appointmentValue = AppointmentView(appointmentId, participantId = participantId,
                startsAt = start, endsAt = start.plusMinutes(50), version = 3,
                status = AppointmentStatus.SCHEDULED, availableActions = listOf("UPDATE"))
            repository.updateAppointmentResults.add(RemoteResult.Failure(RemoteError.Conflict))
            val vm = OperationalViewModel(repository, { "token" }, {})
            vm.selectAppointment(participantId, appointmentId)
            vm.loadAppointment(appointmentId)
            advanceUntilIdle()
            assertTrue(vm.openEditSchedule())
            advanceUntilIdle()
            repository.appointmentValue = repository.appointmentValue!!.copy(version = 4)
            val draft = AppointmentSaveCommand(participantId, start, start.plusMinutes(50),
                AppointmentType.CONSULTATION, AppointmentLocationMode.REMOTE)
            vm.saveSchedule(draft)
            advanceUntilIdle()
            assertEquals(ScheduleSaveState.Conflict, vm.scheduleSave.value)
            assertEquals(1, repository.updateAppointmentCalls.size)
            assertEquals(3L, repository.updateAppointmentCalls.single().second.version)
            assertEquals(4L, (vm.appointment.value as ReadState.Ready).value.version)
        } finally { Dispatchers.resetMain() }
    }

    @Test fun obsoleteScheduleResponseCannotReplaceSelectedDate() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val repo = StubRepository()
            val first = java.time.LocalDate.parse("2026-11-20")
            val second = first.plusDays(1)
            repo.scheduleToday = TodayView(localDate = first)
            val delayed = CompletableDeferred<RemoteResult<TodayView>>()
            repo.pendingDates[first] = delayed
            val vm = OperationalViewModel(repo, { "token" }, {})
            vm.openNewSchedule()
            advanceUntilIdle()
            vm.chooseScheduleDate(first)
            runCurrent()
            vm.chooseScheduleDate(second)
            advanceUntilIdle()
            delayed.complete(RemoteResult.Success(TodayView(localDate = first)))
            advanceUntilIdle()
            assertEquals(second, (vm.scheduleDay.value as ReadState.Ready).value.localDate)
        } finally { Dispatchers.resetMain() }
    }

    @Test fun visitStageRecheckDeniesAccessAndClearsSelectionAfterRevocation() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val repository = StubRepository()
            val participant = UUID.randomUUID()
            val appointment = UUID.randomUUID()
            repository.appointmentValue = AppointmentView(appointment, participantId = participant,
                status = AppointmentStatus.IN_PROGRESS, plannedSessionId = UUID.randomUUID())
            val vm = OperationalViewModel(repository, { "token" }, {})
            vm.selectAppointment(participant, appointment)
            vm.loadAppointment(appointment)
            advanceUntilIdle()
            assertNotNull(verifiedVisit(vm.selection.value, vm.appointment.value))
            // Navigating into a visit stage re-fetches the appointment instead of trusting S05's snapshot.
            repository.pendingAppointment = CompletableDeferred(RemoteResult.Failure(RemoteError.Forbidden))
            vm.loadAppointment(appointment)
            advanceUntilIdle()
            assertNull(vm.selection.value)
            assertNull(verifiedVisit(vm.selection.value, vm.appointment.value))
            assertEquals(ReadState.Forbidden, vm.professionalContext.value)
        } finally { Dispatchers.resetMain() }
    }

    private class StubRepository : SpecialistRepository {
        var onboardingResult: RemoteResult<MobileOnboardingState> = RemoteResult.Success(
            MobileOnboardingState(stage = "READY", profileType = "SPECIALIST",
                profile = MobileSpecialistProfile(specialistKind = ActingContext.TRAINER))
        )
        override suspend fun onboarding(): RemoteResult<MobileOnboardingState> = onboardingResult
        val pendingWorkspaces = mutableMapOf<UUID, CompletableDeferred<RemoteResult<WorkspaceView>>>()
        val timelineResults = mutableMapOf<String?, RemoteResult<TimelinePage>>()
        val timelineRequests = mutableListOf<Triple<String?, String?, String?>>()
        var participantResult: RemoteResult<List<ActiveParticipantView>> = RemoteResult.Success(emptyList())
        var pendingToday: CompletableDeferred<RemoteResult<TodayView>>? = null
        val pendingDates = mutableMapOf<java.time.LocalDate, CompletableDeferred<RemoteResult<TodayView>>>()
        var appointmentValue: AppointmentView? = null
        var pendingAppointment: CompletableDeferred<RemoteResult<AppointmentView>>? = null
        val startResults = ArrayDeque<RemoteResult<AppointmentView>>()
        val startRequests = mutableListOf<Triple<UUID, Long, String>>()
        override suspend fun appointment(id: UUID): RemoteResult<AppointmentView> =
            pendingAppointment?.await() ?: RemoteResult.Success(appointmentValue ?: AppointmentView(appointmentId = id))
        override suspend fun startAppointment(id: UUID, version: Long, key: String): RemoteResult<AppointmentView> {
            startRequests += Triple(id, version, key)
            return startResults.removeFirstOrNull() ?: RemoteResult.Failure(RemoteError.Conflict)
        }
        var scheduleToday: TodayView = TodayView()
        val createAppointmentResults = ArrayDeque<RemoteResult<AppointmentView>>()
        val updateAppointmentResults = ArrayDeque<RemoteResult<AppointmentView>>()
        val createAppointmentCalls = mutableListOf<Pair<AppointmentSaveCommand, String>>()
        val updateAppointmentCalls = mutableListOf<Triple<UUID, AppointmentSaveCommand, String>>()
        override suspend fun createAppointment(command: AppointmentSaveCommand, key: String): RemoteResult<AppointmentView> {
            createAppointmentCalls += command to key
            return createAppointmentResults.removeFirstOrNull() ?: RemoteResult.Failure(RemoteError.Conflict)
        }
        override suspend fun updateAppointment(id: UUID, command: AppointmentSaveCommand, key: String): RemoteResult<AppointmentView> {
            updateAppointmentCalls += Triple(id, command, key)
            return updateAppointmentResults.removeFirstOrNull() ?: RemoteResult.Failure(RemoteError.Conflict)
        }
        override suspend fun workspace(participantId: java.util.UUID): RemoteResult<WorkspaceView> =
            pendingWorkspaces[participantId]?.await() ?: RemoteResult.Success(
                WorkspaceView(participant = WorkspaceParticipant(participantId = participantId))
            )
        override suspend fun timeline(participantId: java.util.UUID, cursor: String?, from: String?, to: String?): RemoteResult<TimelinePage> {
            timelineRequests += Triple(cursor, from, to)
            return timelineResults[cursor] ?: RemoteResult.Success(TimelinePage())
        }
        var clientResult: RemoteResult<List<ClientView>> = RemoteResult.Success(emptyList())
        override suspend fun clients(): RemoteResult<List<ClientView>> = clientResult
        var createResult: RemoteResult<ClientView> = RemoteResult.Failure(RemoteError.Conflict)
        val creationKeys = mutableListOf<String>()
        override suspend fun createClient(command: ClientCommand, idempotencyKey: String): RemoteResult<ClientView> {
            creationKeys += idempotencyKey
            return createResult
        }
        override suspend fun identity(): RemoteResult<IdentityResponse> = RemoteResult.Success(IdentityResponse())
        override suspend fun today(date: java.time.LocalDate?): RemoteResult<TodayView> =
            date?.let { pendingDates[it]?.await() } ?: pendingToday?.await() ?: RemoteResult.Success(
                if (scheduleToday.localDate != null && date != null) scheduleToday.copy(localDate = date)
                else scheduleToday
            )
        override suspend fun participants() = participantResult
        override suspend fun worklist(context: ActingContext, purpose: WorklistPurpose):
            RemoteResult<List<WorklistItemView>> = RemoteResult.Success(emptyList())
    }
}
