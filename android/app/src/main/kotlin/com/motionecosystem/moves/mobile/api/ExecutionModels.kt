package com.motionecosystem.moves.mobile.api

import java.util.UUID
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonPrimitive

/** The appointment, not the device, supplies the authorized planned session and its snapshot. */
@Serializable
data class SpecialistExecutionContext(
    @Serializable(with = UuidSerializer::class) val appointmentId: UUID? = null,
    val appointmentStatus: String? = null,
    @Serializable(with = UuidSerializer::class) val participantId: UUID? = null,
    val participantName: String? = null,
    @Serializable(with = UuidSerializer::class) val plannedSessionId: UUID? = null,
    val sessionTitle: String? = null,
    @Serializable(with = UuidSerializer::class) val planId: UUID? = null,
    @Serializable(with = UuidSerializer::class) val revisionId: UUID? = null,
    val prescriptions: List<SpecialistExecutionPrescription> = emptyList(),
    val recordedExecution: RecordedExecutionSummary? = null,
    val recordingAllowed: Boolean = false
)

@Serializable
data class SpecialistExecutionPrescription(
    @Serializable(with = UuidSerializer::class) val id: UUID? = null,
    val position: Int? = null,
    val exerciseName: String? = null,
    val canonicalDoseType: String? = null,
    val doseType: String? = null,
    val sets: Int? = null,
    val repetitions: Int? = null,
    val durationSeconds: Int? = null,
    val distanceMeters: JsonPrimitive? = null,
    val contacts: Int? = null,
    val externalLoadValue: JsonPrimitive? = null,
    val externalLoadUnit: String? = null,
    val intensityType: String? = null,
    val intensityValue: JsonPrimitive? = null,
    val intensityZone: String? = null,
    val side: String? = null,
    val tempo: String? = null,
    val restSeconds: Int? = null,
    val notes: String? = null
)

@Serializable
data class RecordedExecutionSummary(
    @Serializable(with = UuidSerializer::class) val executionId: UUID? = null,
    val outcome: String? = null,
    val recordedAt: String? = null
)

@Serializable
data class ExecutionResultCommand(
    @Serializable(with = UuidSerializer::class) val exercisePrescriptionId: UUID,
    val actualSets: Int? = null,
    val actualRepetitions: Int? = null,
    val actualDurationSeconds: Int? = null,
    val actualContacts: Int? = null,
    val actualDistanceMeters: JsonPrimitive? = null,
    val actualExternalLoadValue: JsonPrimitive? = null,
    val actualExternalLoadUnit: String? = null,
    val actualIntensityType: String? = null,
    val actualIntensityValue: JsonPrimitive? = null,
    val actualIntensityZone: String? = null,
    val side: String? = null,
    val modified: Boolean,
    val skipped: Boolean,
    val observationMode: String = "DECLARED"
)

@Serializable
data class RecordSessionExecutionCommand(
    val declaredCompletion: Boolean = true,
    val results: List<ExecutionResultCommand>,
    val painLevel: Int,
    val difficultyLevel: Int,
    val techniqueConfidenceLevel: Int? = null,
    val note: String? = null,
    val sessionRpe: Int? = null,
    val observationMode: String = "DECLARED"
)

@Serializable
data class RecordedSessionExecution(
    @Serializable(with = UuidSerializer::class) val id: UUID? = null,
    @Serializable(with = UuidSerializer::class) val plannedSessionId: UUID? = null,
    @Serializable(with = UuidSerializer::class) val participantId: UUID? = null,
    val outcome: String? = null
)
