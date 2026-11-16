package com.motionecosystem.moves.mobile.api

import java.util.UUID
import kotlinx.serialization.Serializable

@Serializable
data class NoteView(
    @Serializable(with = UuidSerializer::class) val id: UUID? = null,
    @Serializable(with = UuidSerializer::class) val participantId: UUID? = null,
    val category: String? = null,
    val title: String? = null,
    val content: String? = null,
    @Serializable(with = UuidSerializer::class) val appointmentId: UUID? = null,
    val status: String? = null,
    val availableActions: List<String> = emptyList(),
    val version: Long? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null
)

@Serializable
data class CreateNoteCommand(
    val category: String,
    val title: String,
    val content: String,
    @Serializable(with = UuidSerializer::class) val appointmentId: UUID? = null
)

@Serializable
data class UpdateNoteCommand(
    val expectedVersion: Long,
    val category: String,
    val title: String,
    val content: String,
    @Serializable(with = UuidSerializer::class) val appointmentId: UUID? = null
)
