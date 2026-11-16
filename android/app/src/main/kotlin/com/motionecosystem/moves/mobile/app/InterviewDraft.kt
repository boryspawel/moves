package com.motionecosystem.moves.mobile.app

import com.motionecosystem.moves.mobile.api.InterviewAnswer
import com.motionecosystem.moves.mobile.api.InterviewQuestion
import com.motionecosystem.moves.mobile.api.InterviewView
import com.motionecosystem.moves.mobile.api.SaveInterviewDraftCommand
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID
import kotlinx.serialization.json.JsonPrimitive

/** PHI and form baselines exist only in retained ViewModel RAM, never SavedState or disk. */
internal data class InterviewDraft(
    val participantId: UUID,
    val interviewId: UUID,
    val values: Map<String, String>,
    val originalValues: Map<String, String> = values,
    val selections: Map<String, List<String>> = emptyMap(),
    val originalSelections: Map<String, List<String>> = selections,
    val baseVersion: Long? = null,
    val dirty: Boolean = false
)

internal enum class InterviewOperation { START, SAVE, COMPLETE }

internal sealed interface InterviewWriteState {
    data object Idle : InterviewWriteState
    data class Saving(val operation: InterviewOperation) : InterviewWriteState
    data class Uncertain(val operation: InterviewOperation) : InterviewWriteState
    data class Conflict(val operation: InterviewOperation) : InterviewWriteState
    data class Failed(val message: String) : InterviewWriteState
    data class Success(val message: String) : InterviewWriteState
}

internal fun editableInterviewAnswer(question: InterviewQuestion): Boolean =
    question.type in setOf("TEXT", "NUMBER", "DATE")

internal data class InterviewOption(val value: String, val label: String)

/** Reuse the existing web interview vocabulary, never invent consent categories.
 * The API currently returns question codes/types without option catalogs.
 */
internal fun interviewOptions(code: String): List<InterviewOption> = when (code) {
    "activity_level" -> listOf(
        InterviewOption("LOW", "Niski"),
        InterviewOption("MODERATE", "Umiarkowany"),
        InterviewOption("HIGH", "Wysoki")
    )
    "sleep" -> listOf(
        InterviewOption("POOR", "Niewystarczający"),
        InterviewOption("ADEQUATE", "Wystarczający"),
        InterviewOption("GOOD", "Dobry")
    )
    "consents" -> listOf(
        InterviewOption("DATA_PROCESSING", "Przetwarzanie danych"),
        InterviewOption("CONTACT", "Kontakt"),
        InterviewOption("PLAN_SHARING", "Udostępnianie planu")
    )
    else -> emptyList()
}

internal fun isInterviewSelection(question: InterviewQuestion): Boolean =
    question.type == "SINGLE_SELECT" || question.type == "MULTI_SELECT"

internal fun interviewAnswerText(answer: InterviewAnswer?, question: InterviewQuestion): String =
    when (question.type) {
        "TEXT" -> answer?.textValue.orEmpty()
        "NUMBER" -> answer?.numberValue?.content.orEmpty()
        "DATE" -> answer?.dateValue.orEmpty()
        else -> answer?.selections.orEmpty().joinToString(", ")
    }

/** A draft is initialized only from a matching authorized interview. */
internal fun initialInterviewDraft(participantId: UUID, view: InterviewView): InterviewDraft? {
    val id = view.id ?: return null
    if (view.participantId != participantId || view.status != "DRAFT") return null
    val answers = view.answers.associateBy { it.code }
    val values = view.questions.filter(::editableInterviewAnswer)
        .associate { it.code to interviewAnswerText(answers[it.code], it) }
    val selections = view.questions.filter(::isInterviewSelection)
        .associate { it.code to answers[it.code]?.selections.orEmpty() }
    return InterviewDraft(participantId, id, values,
        originalValues = values, selections = selections, originalSelections = selections,
        baseVersion = view.version)
}

/** Preserve local edits while adopting server changes to UNTOUCHED fields after user review.
 * This never performs a write: the user must explicitly save again.
 */
internal fun reconcileInterviewDraft(draft: InterviewDraft, latest: InterviewView): InterviewDraft? {
    val fresh = initialInterviewDraft(draft.participantId, latest) ?: return null
    if (fresh.interviewId != draft.interviewId || fresh.baseVersion == null) return null
    val changed = draft.values.filter { (key, value) -> draft.originalValues[key] != value }
    val changedSelections = draft.selections.filter { (key, value) ->
        draft.originalSelections[key] != value
    }
    val merged = fresh.values + changed
    val mergedSelections = fresh.selections + changedSelections
    return fresh.copy(
        values = merged, selections = mergedSelections,
        dirty = merged != fresh.originalValues || mergedSelections != fresh.originalSelections
    )
}

/** The backend replaces ALL answers on PUT: keep server-owned SELECT answers intact.
 * Only non-empty, validated TEXT/NUMBER/DATE edits override their original values.
 */
internal fun interviewSaveCommand(view: InterviewView, draft: InterviewDraft): SaveInterviewDraftCommand? {
    if (view.id != draft.interviewId || view.participantId != draft.participantId ||
        view.status != "DRAFT" || "SAVE_DRAFT" !in view.availableActions ||
        view.version == null || view.version != draft.baseVersion) return null
    val original = view.answers.associateBy { it.code }
    val answers = view.questions.mapNotNull { question ->
        if (isInterviewSelection(question)) {
            if (draft.originalSelections[question.code] == draft.selections[question.code])
                return@mapNotNull original[question.code]
            val values = draft.selections[question.code].orEmpty().distinct()
            if (values.isEmpty()) return@mapNotNull null
            val allowed = interviewOptions(question.code).map { it.value }.toSet()
            if (allowed.isEmpty() || values.any { it !in allowed } ||
                (question.type == "SINGLE_SELECT" && values.size != 1)) return null
            return@mapNotNull InterviewAnswer(question.code, selections = values)
        }
        if (!editableInterviewAnswer(question) ||
            draft.originalValues[question.code] == draft.values[question.code]) {
            return@mapNotNull original[question.code]
        }
        val input = draft.values[question.code]?.trim().orEmpty()
        if (input.isEmpty()) return@mapNotNull null
        when (question.type) {
            "TEXT" -> {
                if (input.length > 4000) return null
                InterviewAnswer(question.code, textValue = input)
            }
            "NUMBER" -> {
                val number = input.toBigDecimalOrNull() ?: return null
                InterviewAnswer(question.code, numberValue = JsonPrimitive(number))
            }
            "DATE" -> {
                val date = runCatching { LocalDate.parse(input) }.getOrNull() ?: return null
                InterviewAnswer(question.code, dateValue = date.toString())
            }
            else -> null
        }
    }
    return SaveInterviewDraftCommand(view.version, answers)
}

/** A completed interview is immutable. Required SELECT choices cannot be invented on mobile. */
internal fun canCompleteInterview(view: InterviewView): Boolean {
    if (view.status != "DRAFT" || "COMPLETE" !in view.availableActions || view.version == null) return false
    val answers = view.answers.associateBy { it.code }
    return view.questions.filter { it.required }.all { q ->
        val answer = answers[q.code] ?: return@all false
        when (q.type) {
            "TEXT" -> !answer.textValue.isNullOrBlank()
            "NUMBER" -> answer.numberValue != null
            "DATE" -> !answer.dateValue.isNullOrBlank()
            "SINGLE_SELECT", "MULTI_SELECT" -> !answer.selections.isNullOrEmpty()
            else -> false
        }
    }
}
