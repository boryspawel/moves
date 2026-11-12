package com.motionecosystem.moves.mobile.api

import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.UUID
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class MovesApiTest {
    private lateinit var server: MockWebServer

    @Before fun start() { server = MockWebServer(); server.start() }
    @After fun stop() { server.close() }

    @Test fun `serializers preserve uuid date enums and optional fields`() {
        val id = UUID.randomUUID()
        val decoded = Json.decodeFromString(ActiveParticipantView.serializer(), "{\"participantId\":\"$id\",\"label\":\"A\"}")
        assertEquals(id, decoded.participantId)
        assertEquals(LocalDate.parse("2026-11-12"), Json.decodeFromString(LocalDateSerializer, "\"2026-11-12\""))
        val timestamp = "2026-11-12T08:15:30+02:00"
        val decodedTimestamp = Json.decodeFromString(OffsetDateTimeSerializer, "\"$timestamp\"")
        val roundTripTimestamp = Json.decodeFromString(OffsetDateTimeSerializer, Json.encodeToString(OffsetDateTimeSerializer, decodedTimestamp))
        assertEquals(OffsetDateTime.parse(timestamp), roundTripTimestamp)
        assertEquals("+02:00", roundTripTimestamp.offset.toString())
        assertEquals(ActingContext.TRAINER, Json.decodeFromString(ActingContext.serializer(), "\"TRAINER\""))
        assertEquals(null, Json.decodeFromString(ActiveParticipantView.serializer(), "{}" ).label)
    }

    @Test fun `read request has bearer token and no idempotency header`() = runBlocking {
        server.enqueue(MockResponse.Builder().body("{\"subject\":\"specialist\"}").addHeader("Content-Type", "application/json").build())
        val api = createMovesApi(server.url("/").toString(), TokenProvider { "test-token" })
        assertEquals("specialist", api.currentIdentity().body()?.subject)
        val request = server.takeRequest()
        assertEquals("Bearer test-token", request.headers["Authorization"])
        assertFalse(request.headers.names().contains("Idempotency-Key"))
    }

    @Test fun `error mapper distinguishes HTTP status and request scoped idempotency`() = runBlocking {
        server.enqueue(MockResponse.Builder().code(409).body("{\"summary\":\"Conflict\"}").addHeader("Content-Type", "application/json").build())
        val api = createMovesApi(server.url("/").toString())
        val result = RemoteCall(Json { ignoreUnknownKeys = true }).execute { api.currentIdentity() }
        assertTrue(result is RemoteResult.Failure && result.error is RemoteError.Conflict)
        server.takeRequest()
        server.enqueue(MockResponse.Builder().code(204).build())
        val request = Request.Builder().url(server.url("mutation")).get().build().withIdempotencyKey("one-request-only")
        OkHttpClient().newCall(request).execute().close()
        assertEquals("one-request-only", server.takeRequest().headers["Idempotency-Key"])
    }

    @Test fun `error mapper preserves authorization conflict and non JSON HTTP outcomes`() = runBlocking {
        listOf(401, 403, 409, 502).forEach { code ->
            server.enqueue(MockResponse.Builder().code(code).body(if (code == 502) "not-json" else "{}").build())
        }
        val api = createMovesApi(server.url("/").toString())
        val calls = RemoteCall(Json { ignoreUnknownKeys = true })
        val errors = List(4) { (calls.execute { api.currentIdentity() } as RemoteResult.Failure).error }
        assertTrue(errors[0] is RemoteError.Unauthorized)
        assertTrue(errors[1] is RemoteError.Forbidden)
        assertTrue(errors[2] is RemoteError.Conflict)
        val genericHttp = errors[3] as RemoteError.Http
        assertEquals(502, genericHttp.status)
        assertEquals(null, genericHttp.problem)
    }
}
