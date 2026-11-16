package com.motionecosystem.moves.mobile.api

import java.util.UUID
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonPrimitive

/** Server-owned questionnaire. Options for SELECT fields are not exposed by the current API. */
@Serializable
data class InterviewQuestion(
    val code: String,
    val section: String,
    val title: String,
    val type: String,
    val required: Boolean = false
)

@Serializable
data class InterviewAnswer(
    val code: String,
    val textValue: String? = null,
    val numberValue: JsonPrimitive? = null,
    val dateValue: String? = null,
    val selections: List<String>? = null
)

@Serializable
data class InterviewView(
    @Serializable(with = UuidSerializer::class) val id: UUID? = null,
    @Serializable(with = UuidSerializer::class) val participantId: UUID? = null,
    val status: String? = null,
    val templateCode: String? = null,
    val templateVersion: Int? = null,
    val questions: List<InterviewQuestion> = emptyList(),
    val answers: List<InterviewAnswer> = emptyList(),
    val availableActions: List<String> = emptyList(),
    val version: Long? = null
)
