package com.motionecosystem.moves.mobile.app

import com.motionecosystem.moves.mobile.api.*
import java.math.BigDecimal
import java.util.UUID
import kotlinx.serialization.json.JsonPrimitive

internal enum class ExecutionChoice { AS_PLANNED, CHANGED, SKIPPED }

internal data class ExerciseActualDraft(
    val choice: ExecutionChoice? = null,
    val sets: String = "",
    val repetitions: String = "",
    val durationSeconds: String = "",
    val distanceMeters: String = "",
    val contacts: String = "",
    val externalLoadValue: String = ""
)

/** All clinical input stays in the lifecycle-retained ViewModel RAM; never in SavedState. */
internal data class ExecutionDraft(
    val appointmentId: UUID,
    val participantId: UUID,
    val plannedSessionId: UUID,
    val revisionId: UUID?,
    val prescriptionIds: Set<UUID>,
    val exercises: Map<UUID, ExerciseActualDraft> = emptyMap(),
    val pain: String = "",
    val difficulty: String = "",
    val sessionRpe: String = "",
    val techniqueConfidence: String = "",
    val note: String = ""
) {
    val dirty: Boolean get() = exercises.isNotEmpty() || pain.isNotEmpty() ||
        difficulty.isNotEmpty() || sessionRpe.isNotEmpty() ||
        techniqueConfidence.isNotEmpty() || note.isNotEmpty()
}

internal sealed interface ExecutionSaveState {
    data object Idle : ExecutionSaveState
    data object Saving : ExecutionSaveState
    data object Uncertain : ExecutionSaveState
    data object Conflict : ExecutionSaveState
    data class Failed(val message: String) : ExecutionSaveState
    data object Saved : ExecutionSaveState
}

internal fun initialExecutionDraft(context: SpecialistExecutionContext): ExecutionDraft? {
    val appointment = context.appointmentId ?: return null
    val participant = context.participantId ?: return null
    val session = context.plannedSessionId ?: return null
    if (context.appointmentStatus != "IN_PROGRESS" || !context.recordingAllowed ||
        context.recordedExecution != null || context.prescriptions.isEmpty()) return null
    val ids = context.prescriptions.mapNotNull { it.id }
    if (ids.size != context.prescriptions.size || ids.toSet().size != ids.size) return null
    return ExecutionDraft(appointment, participant, session, context.revisionId, ids.toSet())
}

internal fun matchesExecutionDraft(draft: ExecutionDraft, context: SpecialistExecutionContext): Boolean =
    draft.appointmentId == context.appointmentId &&
        draft.participantId == context.participantId &&
        draft.plannedSessionId == context.plannedSessionId &&
        draft.revisionId == context.revisionId &&
        context.prescriptions.mapNotNull { it.id }.toSet() == draft.prescriptionIds &&
        context.prescriptions.size == draft.prescriptionIds.size

internal fun showsExecutionSets(p: SpecialistExecutionPrescription): Boolean =
    p.sets != null && (p.canonicalDoseType in setOf("STRENGTH", "IMPACT") ||
        p.doseType in setOf("DYNAMIC_RESISTANCE", "IMPACT"))

internal fun showsExecutionRepetitions(p: SpecialistExecutionPrescription): Boolean =
    p.repetitions != null && (p.canonicalDoseType == "STRENGTH" || p.doseType == "DYNAMIC_RESISTANCE")

internal fun showsExecutionLoad(p: SpecialistExecutionPrescription): Boolean =
    p.externalLoadValue != null && (p.canonicalDoseType == "STRENGTH" ||
        p.doseType == "DYNAMIC_RESISTANCE")

internal fun showsExecutionDuration(p: SpecialistExecutionPrescription): Boolean =
    p.durationSeconds != null && !showsExecutionRepetitions(p)

internal fun showsExecutionDistance(p: SpecialistExecutionPrescription): Boolean =
    p.distanceMeters != null

internal fun showsExecutionContacts(p: SpecialistExecutionPrescription): Boolean =
    p.contacts != null && (p.canonicalDoseType == "IMPACT" || p.doseType == "IMPACT")

internal fun plannedExecutionDose(p: SpecialistExecutionPrescription): String =
    listOfNotNull(
        if (p.sets != null && p.repetitions != null) "${p.sets} × ${p.repetitions}" else null,
        p.durationSeconds?.let { "$it s" },
        p.distanceMeters?.let { "${it.content} m" },
        p.contacts?.let { "$it kontaktów" },
        p.externalLoadValue?.let { "${it.content} ${p.externalLoadUnit.orEmpty()}".trim() }
    ).joinToString(", ").ifBlank { "Dawka zgodna z planem" }

private fun wholeNumber(raw: String): Int? {
    if (raw.isBlank()) return null
    return raw.trim().toIntOrNull()?.takeIf { it >= 0 }
}
private fun decimal(raw: String): JsonPrimitive? {
    if (raw.isBlank()) return null
    val decimal = raw.trim().replace(',', '.').toBigDecimalOrNull()
        ?.takeIf { it >= BigDecimal.ZERO } ?: return null
    return JsonPrimitive(decimal)
}

private fun resultFor(p: SpecialistExecutionPrescription, actual: ExerciseActualDraft): ExecutionResultCommand? {
    val id = p.id ?: return null
    if (actual.choice == ExecutionChoice.SKIPPED) {
        return ExecutionResultCommand(id, modified = false, skipped = true)
    }
    if (actual.choice == ExecutionChoice.AS_PLANNED) {
        return ExecutionResultCommand(
            id, actualSets = p.sets, actualRepetitions = p.repetitions,
            actualDurationSeconds = p.durationSeconds, actualContacts = p.contacts,
            actualDistanceMeters = p.distanceMeters, actualExternalLoadValue = p.externalLoadValue,
            actualExternalLoadUnit = if (p.externalLoadValue != null) p.externalLoadUnit else null,
            actualIntensityType = p.intensityType, actualIntensityValue = p.intensityValue,
            actualIntensityZone = p.intensityZone, side = p.side,
            modified = false, skipped = false
        )
    }
    if (actual.choice != ExecutionChoice.CHANGED) return null
    val required = listOf(
        showsExecutionSets(p) to actual.sets,
        showsExecutionRepetitions(p) to actual.repetitions,
        showsExecutionDuration(p) to actual.durationSeconds,
        showsExecutionDistance(p) to actual.distanceMeters,
        showsExecutionContacts(p) to actual.contacts,
        showsExecutionLoad(p) to actual.externalLoadValue
    )
    if (required.none { it.first } ||
        required.any { (visible, value) -> visible && value.isBlank() }) return null
    val sets = if (showsExecutionSets(p)) wholeNumber(actual.sets) else null
    val repetitions = if (showsExecutionRepetitions(p)) wholeNumber(actual.repetitions) else null
    val duration = if (showsExecutionDuration(p)) wholeNumber(actual.durationSeconds) else null
    val contacts = if (showsExecutionContacts(p)) wholeNumber(actual.contacts) else null
    val distance = if (showsExecutionDistance(p)) decimal(actual.distanceMeters) else null
    val load = if (showsExecutionLoad(p)) decimal(actual.externalLoadValue) else null
    if (showsExecutionSets(p) && sets == null ||
        showsExecutionRepetitions(p) && repetitions == null ||
        showsExecutionDuration(p) && duration == null ||
        showsExecutionContacts(p) && contacts == null ||
        showsExecutionDistance(p) && distance == null ||
        showsExecutionLoad(p) && (load == null || p.externalLoadUnit.isNullOrBlank())) return null
    return ExecutionResultCommand(
        id, actualSets = sets, actualRepetitions = repetitions,
        actualDurationSeconds = duration, actualContacts = contacts,
        actualDistanceMeters = distance, actualExternalLoadValue = load,
        actualExternalLoadUnit = if (load != null) p.externalLoadUnit else null,
        modified = true, skipped = false
    )
}

internal fun buildExecutionCommand(context: SpecialistExecutionContext, draft: ExecutionDraft): RecordSessionExecutionCommand? {
    if (!matchesExecutionDraft(draft, context) || !context.recordingAllowed ||
        context.appointmentStatus != "IN_PROGRESS" || context.recordedExecution != null ||
        context.prescriptions.isEmpty() || draft.note.length > 500) return null
    val pain = draft.pain.trim().toIntOrNull()?.takeIf { it in 0..10 } ?: return null
    val difficulty = draft.difficulty.trim().toIntOrNull()?.takeIf { it in 1..10 } ?: return null
    val rpe = if (draft.sessionRpe.isBlank()) null
        else draft.sessionRpe.trim().toIntOrNull()?.takeIf { it in 1..10 } ?: return null
    val confidence = if (draft.techniqueConfidence.isBlank()) null
        else draft.techniqueConfidence.trim().toIntOrNull()?.takeIf { it in 1..10 } ?: return null
    val results = context.prescriptions.map { p ->
        resultFor(p, draft.exercises[p.id] ?: ExerciseActualDraft()) ?: return null
    }
    return RecordSessionExecutionCommand(
        declaredCompletion = true,
        results = results, painLevel = pain, difficultyLevel = difficulty,
        techniqueConfidenceLevel = confidence, sessionRpe = rpe,
        note = draft.note.trim().takeIf { it.isNotEmpty() }
    )
}
