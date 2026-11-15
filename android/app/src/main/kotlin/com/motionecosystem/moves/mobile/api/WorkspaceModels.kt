package com.motionecosystem.moves.mobile.api

import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.UUID
import kotlinx.serialization.Serializable

/**
 * Allowlist of S03 fields. Deliberately excludes recentMeasurements, goal measurement values,
 * adherence metrics, free-text clinical detail and all executable actions.
 * Unknown server fields are ignored by the shared JSON decoder, not persisted or displayed.
 */
@Serializable
data class WorkspaceView(
    val participant: WorkspaceParticipant? = null,
    val relationship: WorkspaceRelationship? = null,
    val focus: WorkspaceFocus? = null,
    val nextAppointment: WorkspaceAppointment? = null,
    val activePlan: WorkspacePlan? = null,
    val goals: List<WorkspaceGoal>? = null
)

@Serializable
data class WorkspaceParticipant(
    @Serializable(with = UuidSerializer::class) val participantId: UUID? = null,
    val displayName: String? = null,
    val contextLabel: String? = null,
    val timeZoneId: String? = null
)

@Serializable data class WorkspaceRelationship(val status: String? = null)
@Serializable data class WorkspaceFocus(val kind: String? = null)

@Serializable
data class WorkspaceAppointment(
    @Serializable(with = UuidSerializer::class) val appointmentId: UUID? = null,
    @Serializable(with = OffsetDateTimeSerializer::class) val startsAt: OffsetDateTime? = null,
    val status: String? = null,
    val type: String? = null
)

@Serializable
data class WorkspacePlan(
    val name: String? = null,
    val status: String? = null,
    @Serializable(with = LocalDateSerializer::class) val validFrom: LocalDate? = null,
    @Serializable(with = LocalDateSerializer::class) val validTo: LocalDate? = null,
    val activeSessionCount: Int? = null
)

@Serializable
data class WorkspaceGoal(
    val title: String? = null,
    val type: String? = null,
    val status: String? = null,
    @Serializable(with = LocalDateSerializer::class) val targetDate: LocalDate? = null
)

/** Non-measurement metadata only; title, summary, problem and detail are intentionally omitted. */
@Serializable
data class TimelineEvent(
    val eventId: String? = null,
    val eventType: String? = null,
    val category: String? = null,
    val status: String? = null,
    @Serializable(with = OffsetDateTimeSerializer::class) val effectiveFrom: OffsetDateTime? = null
)

@Serializable
data class TimelinePage(
    val items: List<TimelineEvent> = emptyList(),
    val nextCursor: String? = null,
    @Serializable(with = OffsetDateTimeSerializer::class) val from: OffsetDateTime? = null,
    @Serializable(with = OffsetDateTimeSerializer::class) val to: OffsetDateTime? = null
)

/** Timeline intentionally excludes MEASUREMENT even though that type is supported by the server. */
internal const val MOBILE_TIMELINE_TYPES = "APPOINTMENT,SESSION,EXECUTION,GOAL,INTERVIEW,NOTE"
