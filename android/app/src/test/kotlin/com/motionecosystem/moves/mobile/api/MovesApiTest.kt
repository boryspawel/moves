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

    @Test fun workspaceAndTimelineUseAuthorizedReadRoutesAndExcludeMeasurementTimelineType() = runBlocking {
        val participantId = UUID.randomUUID()
        server.enqueue(MockResponse.Builder()
            .body("""{"participant":{"participantId":"$participantId","displayName":"Anna","timeZoneId":"Europe/Warsaw"},"goals":[{"title":"Cel A","status":"ACTIVE","latestValue":999}],"recentMeasurements":[{"label":"private","value":55}],"activePlan":{"name":"Plan podstawowy","status":"ACTIVE"}}""")
            .addHeader("Content-Type", "application/json").build())
        server.enqueue(MockResponse.Builder()
            .body("""{"items":[{"eventId":"appointment-event:1","category":"APPOINTMENT","effectiveFrom":"2026-11-15T12:00:00Z","summary":"sensitive text"}],"nextCursor":"cursor-2","from":"2026-11-01T00:00:00Z","to":"2026-11-15T20:00:00Z"}""")
            .addHeader("Content-Type", "application/json").build())
        val api = createMovesApi(server.url("/").toString(), TokenProvider { "access-token" })
        val workspace = api.workspace(participantId).body()
        assertEquals("Anna", workspace?.participant?.displayName)
        assertEquals("Cel A", workspace?.goals?.single()?.title)
        assertEquals("Plan podstawowy", workspace?.activePlan?.name)
        val workspaceRequest = server.takeRequest()
        assertEquals("/api/v1/specialist/participants/$participantId/workspace", workspaceRequest.url.encodedPath)
        assertEquals("Bearer access-token", workspaceRequest.headers["Authorization"])

        val history = api.timeline(
            participantId,
            cursor = "cursor-1",
            from = "2026-11-01T00:00:00Z",
            to = "2026-11-15T20:00:00Z"
        ).body()
        assertEquals("cursor-2", history?.nextCursor)
        assertEquals("APPOINTMENT", history?.items?.single()?.category)
        val historyRequest = server.takeRequest()
        assertEquals("/api/v1/specialist/participants/$participantId/timeline", historyRequest.url.encodedPath)
        assertEquals(MOBILE_TIMELINE_TYPES, historyRequest.url.queryParameter("types"))
        assertFalse(MOBILE_TIMELINE_TYPES.contains("MEASUREMENT"))
        assertEquals("DETAIL", historyRequest.url.queryParameter("granularity"))
        assertEquals("20", historyRequest.url.queryParameter("limit"))
        assertEquals("cursor-1", historyRequest.url.queryParameter("cursor"))
        assertEquals("2026-11-01T00:00:00Z", historyRequest.url.queryParameter("from"))
        assertEquals("2026-11-15T20:00:00Z", historyRequest.url.queryParameter("to"))
    }

    @Test fun specialistClientCreationSendsOneIdempotentPostAndReadsAccountFreeRecord() = runBlocking {
        val id = UUID.randomUUID()
        server.enqueue(MockResponse.Builder()
            .body("""{"participantId":"$id","displayName":"Anna","accessStatus":"NO_ACCOUNT","availableActions":["OPEN_WORKSPACE"]}""")
            .addHeader("Content-Type", "application/json").build())
        val api = createMovesApi(server.url("/").toString(), TokenProvider { "access" })
        val key = UUID.randomUUID().toString()
        val command = ClientCommand(displayName = "Anna", relationshipContext = ClientRelationship.PATIENT)
        val result = api.createClient(key, command)
        assertEquals(id, result.body()?.participantId)
        assertEquals("NO_ACCOUNT", result.body()?.accessStatus)
        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/api/v1/specialist/clients", request.url.encodedPath)
        assertEquals(key, request.headers["Idempotency-Key"])
        assertTrue(request.body?.utf8()?.contains("displayName") == true)
        assertTrue(request.body?.utf8()?.contains("Anna") == true)
    }

    @Test fun todayDateIsSentAsLocalDateWithoutClientTimeZoneConversion() = runBlocking {
        server.enqueue(MockResponse.Builder()
            .body("{\"localDate\":\"2026-11-16\",\"timeZoneId\":\"Europe/Warsaw\"}")
            .addHeader("Content-Type", "application/json").build())
        val api = createMovesApi(server.url("/").toString())
        val requested = api.today("2026-11-16")
        assertEquals(java.time.LocalDate.of(2026, 11, 16), requested.body()?.localDate)
        val request = server.takeRequest()
        assertEquals("/api/v1/specialist/today", request.url.encodedPath)
        assertEquals("2026-11-16", request.url.queryParameter("date"))
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
