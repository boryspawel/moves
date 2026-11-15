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

    private class StubRepository : SpecialistRepository {
        var participantResult: RemoteResult<List<ActiveParticipantView>> = RemoteResult.Success(emptyList())
        var pendingToday: CompletableDeferred<RemoteResult<TodayView>>? = null
        val pendingDates = mutableMapOf<java.time.LocalDate, CompletableDeferred<RemoteResult<TodayView>>>()
        override suspend fun clients(): RemoteResult<List<ClientView>> = RemoteResult.Success(emptyList())
        var createResult: RemoteResult<ClientView> = RemoteResult.Failure(RemoteError.Conflict)
        val creationKeys = mutableListOf<String>()
        override suspend fun createClient(command: ClientCommand, idempotencyKey: String): RemoteResult<ClientView> {
            creationKeys += idempotencyKey
            return createResult
        }
        override suspend fun identity(): RemoteResult<IdentityResponse> = RemoteResult.Success(IdentityResponse())
        override suspend fun today(date: java.time.LocalDate?): RemoteResult<TodayView> =
            date?.let { pendingDates[it]?.await() } ?: pendingToday?.await() ?: RemoteResult.Success(TodayView())
        override suspend fun participants() = participantResult
        override suspend fun worklist(context: ActingContext, purpose: WorklistPurpose):
            RemoteResult<List<WorklistItemView>> = RemoteResult.Success(emptyList())
    }
}
