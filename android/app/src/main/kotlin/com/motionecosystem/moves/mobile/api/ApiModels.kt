package com.motionecosystem.moves.mobile.api

import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.UUID
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

object UuidSerializer : KSerializer<UUID> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("UUID", PrimitiveKind.STRING)
    override fun deserialize(decoder: Decoder): UUID = UUID.fromString(decoder.decodeString())
    override fun serialize(encoder: Encoder, value: UUID) = encoder.encodeString(value.toString())
}
object LocalDateSerializer : KSerializer<LocalDate> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("LocalDate", PrimitiveKind.STRING)
    override fun deserialize(decoder: Decoder): LocalDate = LocalDate.parse(decoder.decodeString())
    override fun serialize(encoder: Encoder, value: LocalDate) = encoder.encodeString(value.toString())
}
object OffsetDateTimeSerializer : KSerializer<OffsetDateTime> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("OffsetDateTime", PrimitiveKind.STRING)
    override fun deserialize(decoder: Decoder): OffsetDateTime = OffsetDateTime.parse(decoder.decodeString())
    override fun serialize(encoder: Encoder, value: OffsetDateTime) = encoder.encodeString(value.toString())
}

@Serializable data class IdentityResponse(val audiences: List<String>? = null, val subject: String? = null)
@Serializable data class ActiveParticipantView(@Serializable(with = UuidSerializer::class) val participantId: UUID? = null, val label: String? = null)
@Serializable enum class ActingContext { TRAINER, PHYSIOTHERAPIST }
@Serializable enum class WorklistPurpose { PERFORMANCE_PLANNING, FUNCTIONAL_RECOVERY, CLINICAL_REVIEW }
@Serializable data class ReplyView(@Serializable(with = UuidSerializer::class) val id: UUID? = null, @Serializable(with = OffsetDateTimeSerializer::class) val createdAt: OffsetDateTime? = null, val shortText: String? = null)
@Serializable data class WorklistItemView(@Serializable(with = UuidSerializer::class) val id: UUID? = null, @Serializable(with = UuidSerializer::class) val participantId: UUID? = null, @Serializable(with = UuidSerializer::class) val planRevisionId: UUID? = null, @Serializable(with = OffsetDateTimeSerializer::class) val createdAt: OffsetDateTime? = null, @Serializable(with = OffsetDateTimeSerializer::class) val snoozedUntil: OffsetDateTime? = null, val category: String? = null, val issueText: String? = null, val minimalData: String? = null, val policyVersion: String? = null, val priority: String? = null, val reasonCode: String? = null, val replies: List<ReplyView>? = null, val status: String? = null)
@Serializable data class AppointmentView(@Serializable(with = UuidSerializer::class) val appointmentId: UUID? = null, @Serializable(with = UuidSerializer::class) val participantId: UUID? = null, @Serializable(with = UuidSerializer::class) val plannedSessionId: UUID? = null, @Serializable(with = OffsetDateTimeSerializer::class) val startsAt: OffsetDateTime? = null, @Serializable(with = OffsetDateTimeSerializer::class) val endsAt: OffsetDateTime? = null, val availableActions: List<String>? = null, val isCurrent: Boolean? = null, val isNext: Boolean? = null, val location: String? = null, val locationMode: AppointmentLocationMode? = null, val shortPurpose: String? = null, val status: AppointmentStatus? = null, val type: AppointmentType? = null, val version: Long? = null)
@Serializable enum class AppointmentLocationMode { IN_PERSON, REMOTE, PHONE }
@Serializable enum class AppointmentStatus { SCHEDULED, CONFIRMED, IN_PROGRESS, COMPLETED, CANCELLED, NO_SHOW }
@Serializable enum class AppointmentType { TRAINING, PHYSIOTHERAPY, ASSESSMENT, CONSULTATION }
@Serializable data class AttentionItemView(@Serializable(with = UuidSerializer::class) val id: UUID? = null, @Serializable(with = OffsetDateTimeSerializer::class) val createdAt: OffsetDateTime? = null, @Serializable(with = OffsetDateTimeSerializer::class) val dueAt: OffsetDateTime? = null, val availableActions: List<String>? = null, val navigationReference: String? = null, val neutralReason: String? = null, val participantLabel: String? = null, val priority: String? = null, val status: String? = null, val title: String? = null, val type: String? = null)
@Serializable data class AvailabilityWindowView(@Serializable(with = OffsetDateTimeSerializer::class) val startsAt: OffsetDateTime? = null, @Serializable(with = OffsetDateTimeSerializer::class) val endsAt: OffsetDateTime? = null, val type: String? = null)
@Serializable data class BookableSlotView(@Serializable(with = OffsetDateTimeSerializer::class) val startsAt: OffsetDateTime? = null, @Serializable(with = OffsetDateTimeSerializer::class) val endsAt: OffsetDateTime? = null)
@Serializable data class Counts(val appointments: Int? = null, val attentionItems: Int? = null, val currentAppointments: Int? = null, val operationalTasks: Int? = null)
@Serializable data class OperationalTaskView(val navigationReference: String? = null, val title: String? = null, val type: String? = null)
@Serializable data class VisibleRange(@Serializable(with = OffsetDateTimeSerializer::class) val startsAt: OffsetDateTime? = null, @Serializable(with = OffsetDateTimeSerializer::class) val endsAt: OffsetDateTime? = null, val recommendedStepMinutes: Int? = null)
@Serializable data class TodayView(val appointments: List<AppointmentView>? = null, val attentionItems: List<AttentionItemView>? = null, val availabilityWindows: List<AvailabilityWindowView>? = null, val bookableSlots: List<BookableSlotView>? = null, val counts: Counts? = null, val currentAppointment: AppointmentView? = null, @Serializable(with = OffsetDateTimeSerializer::class) val generatedAt: OffsetDateTime? = null, @Serializable(with = LocalDateSerializer::class) val localDate: LocalDate? = null, val nextAppointment: AppointmentView? = null, val operationalTasks: List<OperationalTaskView>? = null, val timeZoneId: String? = null, val visibleRange: VisibleRange? = null)
@Serializable data class ProblemDetails(@Serializable(with = UuidSerializer::class) val problemId: UUID? = null, val status: String? = null, val summary: String? = null, val type: String? = null)
