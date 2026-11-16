package com.motionecosystem.moves.mobile.app

import com.motionecosystem.moves.mobile.api.*
import com.motionecosystem.moves.mobile.repository.SpecialistRepository
import com.motionecosystem.moves.mobile.auth.TokenRefreshTemporarilyUnavailable
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
    @Test fun keycloakOutageDuringRefreshGatesDataButPreservesSessionAndDraftForRetry() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            var temporaryOutage = false
            var invalidated = false
            val repository = StubRepository()
            val vm = OperationalViewModel(repository, {
                if (temporaryOutage) throw TokenRefreshTemporarilyUnavailable()
                "token"
            }, { invalidated = true })
            vm.revalidateAccess()
            advanceUntilIdle()
            val epoch = vm.accessEpoch.value
            val participantId = UUID.randomUUID()
            vm.selectParticipant(participantId)
            vm.loadWorkspace(participantId)
            advanceUntilIdle()
            vm.openClientForm()
            vm.updateClientForm(vm.clientDraft.value.copy(displayName = "Robocza kartoteka"))
            temporaryOutage = true
            vm.revalidateAccess()
            advanceUntilIdle()
            assertEquals(ReadState.Offline, vm.professionalContext.value)
            assertEquals(ReadState.Idle, vm.workspace.value)
            assertEquals(ContextSelection.Participant(participantId), vm.selection.value)
            assertEquals("Robocza kartoteka", vm.clientDraft.value.displayName)
            assertFalse(invalidated)
            assertEquals(epoch, vm.accessEpoch.value)

            temporaryOutage = false
            vm.revalidateAccess()
            advanceUntilIdle()
            assertEquals(ActingContext.TRAINER, (vm.professionalContext.value as ReadState.Ready).value)
            assertEquals(epoch + 1, vm.accessEpoch.value)
            assertEquals(ContextSelection.Participant(participantId), vm.selection.value)
            assertEquals(ReadState.Idle, vm.workspace.value)
        } finally { Dispatchers.resetMain() }
    }

    @Test fun failureToRefreshBeforePostDoesNotSendOrAutoRetryMutation() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            var temporaryOutage = false
            var invalidated = false
            val repository = StubRepository()
            val vm = OperationalViewModel(repository, {
                if (temporaryOutage) throw TokenRefreshTemporarilyUnavailable()
                "token"
            }, { invalidated = true })
            vm.revalidateAccess()
            advanceUntilIdle()
            vm.openClientForm()
            vm.updateClientForm(vm.clientDraft.value.copy(displayName = "Nowy"))
            temporaryOutage = true
            vm.createClient(ClientCommand(displayName = "Nowy"), vm.clientDraft.value.requestKey)
            advanceUntilIdle()
            assertEquals(ReadState.Offline, vm.professionalContext.value)
            assertEquals(0, repository.creationKeys.size)
            assertEquals("Nowy", vm.clientDraft.value.displayName)
            assertFalse(invalidated)
            temporaryOutage = false
            vm.revalidateAccess()
            advanceUntilIdle()
            assertEquals(0, repository.creationKeys.size)
        } finally { Dispatchers.resetMain() }
    }

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

    @Test fun inMemoryClientFormSurvivesRevalidationButIsClearedAfterLogout() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val vm = OperationalViewModel(StubRepository(), { "token" }, {})
            vm.revalidateAccess()
            advanceUntilIdle()
            vm.openClientForm()
            val initialKey = vm.clientDraft.value.requestKey
            val draft = vm.clientDraft.value.copy(
                displayName = "Uczestnik testowy",
                email = "test@example.invalid",
                requestKey = UUID.randomUUID().toString()
            )
            vm.updateClientForm(draft)
            vm.revalidateAccess()
            advanceUntilIdle()
            assertEquals(draft, vm.clientDraft.value)
            assertTrue(vm.clientDraft.value.open)
            assertEquals(initialKey == draft.requestKey, false)
            vm.clear()
            assertFalse(vm.clientDraft.value.open)
            assertEquals("", vm.clientDraft.value.displayName)
            assertEquals("", vm.clientDraft.value.email)
        } finally { Dispatchers.resetMain() }
    }

    @Test fun creatingClientUsesOnePostPerAttemptAndPreservesKeyForExplicitRetry() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val repository = StubRepository()
            val vm = OperationalViewModel(repository, { "access-token" }, {})
            val requestKey = UUID.randomUUID().toString()
            val command = ClientCommand(displayName = "Nowy uczestnik")
            vm.createClient(command, requestKey)
            advanceUntilIdle()
            assertTrue(vm.createClientState.value is CreateClientState.Failed)
            assertEquals(listOf(requestKey), repository.creationKeys)
            val id = UUID.randomUUID()
            repository.createResult = RemoteResult.Success(
                ClientView(participantId = id, displayName = command.displayName, accessStatus = "NO_ACCOUNT")
            )
            vm.createClient(command, requestKey)
            advanceUntilIdle()
            assertEquals(listOf(requestKey, requestKey), repository.creationKeys)
            assertEquals(CreateClientState.Saved(id), vm.createClientState.value)
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
            val profileResult = RemoteResult.Success(
                MobileOnboardingState("READY", "SPECIALIST", MobileSpecialistProfile(ActingContext.PHYSIOTHERAPIST))
            )
            val delayedProfile = CompletableDeferred<RemoteResult<MobileOnboardingState>>()
            repository.pendingOnboarding = delayedProfile
            vm.revalidateAccess()
            runCurrent()
            // The navigation identity remains while checked, but server data is hidden.
            assertEquals(ContextSelection.Participant(id), vm.selection.value)
            assertEquals(ReadState.Idle, vm.workspace.value)
            delayed.complete(RemoteResult.Success(WorkspaceView(participant = WorkspaceParticipant(id, "old"))))
            delayedProfile.complete(profileResult)
            advanceUntilIdle()
            assertNull(vm.selection.value)
            assertEquals(ReadState.Idle, vm.workspace.value)
            assertEquals(ActingContext.PHYSIOTHERAPIST, (vm.professionalContext.value as ReadState.Ready).value)
        } finally { Dispatchers.resetMain() }
    }

    @Test fun resumeKeepsSelectedAppointmentAndDraftUntilAccessIsReconfirmed() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val repository = StubRepository()
            val vm = OperationalViewModel(repository, { "token" }, {})
            val participant = UUID.randomUUID()
            val appointmentId = UUID.randomUUID()
            repository.appointmentValue = AppointmentView(
                appointmentId, participantId = participant,
                version = 4, status = AppointmentStatus.CONFIRMED, availableActions = listOf("UPDATE")
            )
            vm.revalidateAccess()
            advanceUntilIdle()
            vm.selectAppointment(participant, appointmentId)
            vm.loadAppointment(appointmentId)
            advanceUntilIdle()
            assertTrue(vm.openEditSchedule())
            advanceUntilIdle()
            val target = vm.scheduleTarget.value
            val date = vm.scheduleDate.value
            val previousEpoch = vm.accessEpoch.value

            val pending = CompletableDeferred<RemoteResult<MobileOnboardingState>>()
            repository.pendingOnboarding = pending
            vm.revalidateAccess()
            runCurrent()
            assertEquals(ReadState.Loading, vm.professionalContext.value)
            assertEquals(ReadState.Idle, vm.appointment.value)
            assertEquals(ReadState.Idle, vm.scheduleDay.value)
            assertEquals(ContextSelection.Appointment(participant, appointmentId), vm.selection.value)
            assertEquals(target, vm.scheduleTarget.value)
            assertEquals(date, vm.scheduleDate.value)

            pending.complete(repository.onboardingResult)
            advanceUntilIdle()
            assertEquals(ActingContext.TRAINER, (vm.professionalContext.value as ReadState.Ready).value)
            assertEquals(previousEpoch + 1, vm.accessEpoch.value)
            assertEquals(target, vm.scheduleTarget.value)
            assertEquals(ContextSelection.Appointment(participant, appointmentId), vm.selection.value)
            // Detail becomes visible only after the route explicitly refetches it.
            assertEquals(ReadState.Idle, vm.appointment.value)
            vm.loadAppointment(appointmentId)
            advanceUntilIdle()
            assertEquals(appointmentId, (vm.appointment.value as ReadState.Ready).value.appointmentId)
        } finally { Dispatchers.resetMain() }
    }

    @Test fun offlineResumeHidesProjectionWithoutLosingDraftAndDeniedRetryDestroysIt() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val repository = StubRepository()
            val vm = OperationalViewModel(repository, { "token" }, {})
            vm.revalidateAccess()
            advanceUntilIdle()
            val participant = UUID.randomUUID()
            vm.selectParticipant(participant)
            vm.loadWorkspace(participant)
            advanceUntilIdle()
            repository.onboardingResult = RemoteResult.Failure(RemoteError.Network(java.io.IOException("offline")))
            vm.revalidateAccess()
            advanceUntilIdle()
            assertEquals(ReadState.Offline, vm.professionalContext.value)
            assertEquals(ReadState.Idle, vm.workspace.value)
            assertEquals(ContextSelection.Participant(participant), vm.selection.value)
            repository.onboardingResult = RemoteResult.Failure(RemoteError.Forbidden)
            vm.revalidateAccess()
            advanceUntilIdle()
            assertEquals(ReadState.Forbidden, vm.professionalContext.value)
            assertNull(vm.selection.value)
            assertNull(vm.scheduleTarget.value)
        } finally { Dispatchers.resetMain() }
    }

    @Test fun differentProfessionalRoleDiscardsPreviouslySelectedContext() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val repository = StubRepository()
            val vm = OperationalViewModel(repository, { "token" }, {})
            vm.revalidateAccess()
            advanceUntilIdle()
            vm.selectParticipant(UUID.randomUUID())
            vm.loadWorkspace((vm.selection.value as ContextSelection.Participant).participantId)
            advanceUntilIdle()
            repository.onboardingResult = RemoteResult.Success(
                MobileOnboardingState("READY", "SPECIALIST", MobileSpecialistProfile(ActingContext.PHYSIOTHERAPIST))
            )
            vm.revalidateAccess()
            advanceUntilIdle()
            assertEquals(ReadState.Idle, vm.workspace.value)
            assertNull(vm.selection.value)
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

    @Test fun openingNewAppointmentDropsPreviousParticipantsAuthorizedDetail() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val repository = StubRepository()
            val previousParticipant = UUID.randomUUID()
            val nextParticipant = UUID.randomUUID()
            val previousAppointmentId = UUID.randomUUID()
            repository.appointmentValue = AppointmentView(
                appointmentId = previousAppointmentId, participantId = previousParticipant,
                shortPurpose = "Poufna treść", location = "Adres poprzedniego uczestnika",
                version = 1, status = AppointmentStatus.CONFIRMED
            )
            val vm = OperationalViewModel(repository, { "token" }, {})
            vm.selectAppointment(previousParticipant, previousAppointmentId)
            vm.loadAppointment(previousAppointmentId)
            advanceUntilIdle()
            assertTrue(vm.appointment.value is ReadState.Ready)

            vm.openNewSchedule(nextParticipant)
            assertEquals(ScheduleTarget.New(nextParticipant), vm.scheduleTarget.value)
            assertEquals(ReadState.Idle, vm.appointment.value)
            assertEquals(AppointmentStartState.IDLE, vm.appointmentStart.value)
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

    @Test fun editOpenedWithoutCachedTodayResolvesLocalDayBeforeFetchingSlots() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val repo = StubRepository()
            val participantId = UUID.randomUUID()
            val appointmentId = UUID.randomUUID()
            val start = java.time.OffsetDateTime.parse("2026-11-20T00:30:00Z")
            val specialistDay = java.time.LocalDate.of(2026, 11, 19)
            repo.appointmentValue = AppointmentView(
                appointmentId, participantId = participantId,
                startsAt = start, endsAt = start.plusMinutes(45),
                version = 3, status = AppointmentStatus.CONFIRMED,
                availableActions = listOf("UPDATE")
            )
            repo.scheduleToday = TodayView(
                localDate = specialistDay, timeZoneId = "America/Los_Angeles",
                bookableSlots = emptyList()
            )
            val vm = OperationalViewModel(repo, { "token" }, {})
            vm.selectAppointment(participantId, appointmentId)
            vm.loadAppointment(appointmentId)
            advanceUntilIdle()
            assertTrue(vm.openEditSchedule())
            assertTrue(vm.scheduleDateResolving.value)
            assertNull(vm.scheduleDate.value)
            advanceUntilIdle()
            val serverToday = (vm.scheduleDay.value as ReadState.Ready).value
            vm.resolveEditScheduleDate(serverToday)
            assertFalse(vm.scheduleDateResolving.value)
            assertEquals(specialistDay, vm.scheduleDate.value)
            advanceUntilIdle()
            assertEquals(specialistDay, vm.scheduleDate.value)
            assertEquals(specialistDay, (vm.scheduleDay.value as ReadState.Ready).value.localDate)
        } finally { Dispatchers.resetMain() }
    }

    @Test fun missingCalendarZoneNeverFallsBackToUtcDay() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val repo = StubRepository()
            val participant = UUID.randomUUID()
            val visitId = UUID.randomUUID()
            val start = java.time.OffsetDateTime.parse("2026-11-20T00:30:00Z")
            repo.appointmentValue = AppointmentView(
                visitId, participantId = participant,
                startsAt = start, endsAt = start.plusMinutes(45),
                version = 3, status = AppointmentStatus.SCHEDULED,
                availableActions = listOf("UPDATE")
            )
            repo.scheduleToday = TodayView(localDate = java.time.LocalDate.of(2026,11,20))
            val vm = OperationalViewModel(repo, { "token" }, {})
            vm.selectAppointment(participant, visitId)
            vm.loadAppointment(visitId)
            advanceUntilIdle()
            assertTrue(vm.openEditSchedule())
            advanceUntilIdle()
            assertNull(vm.scheduleDate.value)
            vm.resolveEditScheduleDate((vm.scheduleDay.value as ReadState.Ready).value)
            assertTrue(vm.scheduleDateResolving.value)
            assertEquals(ReadState.Error, vm.scheduleDay.value)
            vm.saveSchedule(AppointmentSaveCommand(
                participant, start, start.plusMinutes(45),
                AppointmentType.CONSULTATION, AppointmentLocationMode.REMOTE
            ))
            assertEquals(0, repo.updateAppointmentCalls.size)
            assertTrue(vm.scheduleSave.value is ScheduleSaveState.Failed)
        } finally { Dispatchers.resetMain() }
    }

    @Test fun editMetadataSavesWithoutSelectingAFreeSlotForItsOwnAppointment() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val repository = StubRepository()
            val participantId = UUID.randomUUID()
            val appointmentId = UUID.randomUUID()
            val start = java.time.OffsetDateTime.parse("2026-11-20T10:00:00Z")
            repository.scheduleToday = TodayView(
                localDate = java.time.LocalDate.parse("2026-11-20"),
                timeZoneId = "Europe/Warsaw", bookableSlots = emptyList()
            )
            repository.appointmentValue = AppointmentView(
                appointmentId, participantId = participantId,
                startsAt = start, endsAt = start.plusMinutes(45),
                version = 3, status = AppointmentStatus.SCHEDULED, availableActions = listOf("UPDATE")
            )
            val vm = OperationalViewModel(repository, { "token" }, {})
            vm.selectAppointment(participantId, appointmentId)
            vm.loadAppointment(appointmentId)
            advanceUntilIdle()
            assertTrue(vm.openEditSchedule())
            advanceUntilIdle()
            val command = AppointmentSaveCommand(
                participantId, start, start.plusMinutes(45),
                AppointmentType.CONSULTATION, AppointmentLocationMode.REMOTE,
                shortPurpose = "Zaktualizowany cel"
            )
            vm.saveSchedule(command.copy(startsAt = start.plusHours(1)))
            advanceUntilIdle()
            assertEquals(0, repository.updateAppointmentCalls.size)
            assertTrue(vm.scheduleSave.value is ScheduleSaveState.Failed)

            repository.updateAppointmentResults.add(RemoteResult.Success(
                repository.appointmentValue!!.copy(
                    version = 4, shortPurpose = "Zaktualizowany cel"
                )
            ))
            vm.saveSchedule(command)
            advanceUntilIdle()
            assertEquals(1, repository.updateAppointmentCalls.size)
            assertEquals(3L, repository.updateAppointmentCalls.single().second.version)
            assertEquals(start, repository.updateAppointmentCalls.single().second.startsAt)
            assertEquals(start.plusMinutes(45), repository.updateAppointmentCalls.single().second.endsAt)
            assertTrue(vm.scheduleSave.value is ScheduleSaveState.Saved)
        } finally { Dispatchers.resetMain() }
    }

    @Test fun schedulingDraftRetainsOriginalTimeAndTextAfterRefreshThenClearsOnLogout() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val repository = StubRepository()
            val participantId = UUID.randomUUID()
            val appointmentId = UUID.randomUUID()
            val start = java.time.OffsetDateTime.parse("2026-11-20T10:00:00Z")
            repository.appointmentValue = AppointmentView(
                appointmentId, participantId = participantId,
                startsAt = start, endsAt = start.plusMinutes(45),
                shortPurpose = "Pierwotny cel", status = AppointmentStatus.CONFIRMED,
                version = 3, availableActions = listOf("UPDATE")
            )
            val vm = OperationalViewModel(repository, { "token" }, {})
            vm.selectAppointment(participantId, appointmentId)
            vm.loadAppointment(appointmentId)
            advanceUntilIdle()
            assertTrue(vm.openEditSchedule())
            advanceUntilIdle()
            val selected = BookableSlotView(start.plusDays(1), start.plusDays(1).plusMinutes(45))
            val draft = vm.scheduleDraft.value!!.copy(
                shortPurpose = "Niezapisana zmiana",
                location = "Miejsce robocze",
                selectedSlot = selected
            )
            vm.changeScheduleDraft(draft)
            repository.appointmentValue = repository.appointmentValue!!.copy(
                version = 4, shortPurpose = "Zmiana serwera"
            )
            vm.loadAppointment(appointmentId)
            advanceUntilIdle()
            assertEquals(draft, vm.scheduleDraft.value)
            assertEquals(start, vm.scheduleDraft.value!!.baseline!!.originalSlot!!.startsAt)
            vm.clear()
            assertNull(vm.scheduleDraft.value)
            assertNull(vm.scheduleTarget.value)
            assertEquals(ReadState.Idle, vm.appointment.value)
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
                timeZoneId = "Europe/Warsaw",
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
            assertEquals(ScheduleTarget.Edit(appointmentId, participantId), vm.scheduleTarget.value)
            assertEquals(1, repository.updateAppointmentCalls.size) // No automatic retry after re-fetch.
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

    @Test fun enteringVisitStageDropsPreviousReadWithoutChangingIdentifiers() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val repository = StubRepository()
            val participantId = UUID.randomUUID()
            val appointmentId = UUID.randomUUID()
            repository.appointmentValue = AppointmentView(appointmentId, participantId = participantId,
                status = AppointmentStatus.IN_PROGRESS, plannedSessionId = UUID.randomUUID())
            val vm = OperationalViewModel(repository, { "token" }, {})
            vm.selectAppointment(participantId, appointmentId)
            vm.loadAppointment(appointmentId)
            advanceUntilIdle()
            assertNotNull(verifiedVisit(vm.selection.value, vm.appointment.value))
            vm.prepareVisitStage()
            assertEquals(ReadState.Idle, vm.appointment.value)
            assertEquals(ContextSelection.Appointment(participantId, appointmentId), vm.selection.value)
            vm.loadAppointment(appointmentId)
            advanceUntilIdle()
            assertEquals(participantId, verifiedVisit(vm.selection.value, vm.appointment.value)?.participantId)

            vm.selectParticipant(participantId)
            vm.loadWorkspace(participantId)
            advanceUntilIdle()
            vm.prepareParticipantDocumentationStage()
            assertEquals(ReadState.Idle, vm.workspace.value)
            assertEquals(ContextSelection.Participant(participantId), vm.selection.value)
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
        var pendingOnboarding: CompletableDeferred<RemoteResult<MobileOnboardingState>>? = null
        override suspend fun onboarding(): RemoteResult<MobileOnboardingState> =
            pendingOnboarding?.await() ?: onboardingResult
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
        override suspend fun interviews(participantId: UUID, actingContext: ActingContext):
            RemoteResult<List<InterviewView>> = RemoteResult.Success(emptyList())
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
