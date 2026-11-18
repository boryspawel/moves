package com.motionecosystem.moves.mobile.api

import kotlinx.serialization.Serializable
import java.util.UUID

/** The server response also contains measurements. Deliberately do not deserialize them. */
@Serializable
data class CloseoutContextView(
    @Serializable(with = UuidSerializer::class) val appointmentId: UUID? = null,
    @Serializable(with = UuidSerializer::class) val participantId: UUID? = null,
    val status: AppointmentStatus? = null,
    val version: Long? = null,
    val executionRequired: Boolean = false,
    val executionRecorded: Boolean = false,
    val canComplete: Boolean = false,
    val execution: CloseoutExecutionSummary? = null,
    val summaryNote: CloseoutSummaryNote? = null
)

@Serializable data class CloseoutExecutionSummary(val outcome: String? = null)
@Serializable data class CloseoutSummaryNote(val title: String? = null, val content: String? = null)
