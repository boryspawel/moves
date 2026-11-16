package com.motionecosystem.moves.mobile.app

import com.motionecosystem.moves.mobile.api.InterviewAnswer
import com.motionecosystem.moves.mobile.api.InterviewQuestion
import com.motionecosystem.moves.mobile.api.InterviewView
import java.util.UUID

/** Unsaved text exists in the retained ViewModel RAM only; never Android SavedState or disk. */
internal data class InterviewDraft(
    val participantId: UUID,
    val interviewId: UUID,
    val values: Map<String, String>,
    val dirty: Boolean = false
)

internal fun editableInterviewAnswer(question: InterviewQuestion): Boolean =
    question.type in setOf("TEXT", "NUMBER", "DATE")

internal fun interviewAnswerText(answer: InterviewAnswer?, question: InterviewQuestion): String =
    when (question.type) {
        "TEXT" -> answer?.textValue.orEmpty()
        "NUMBER" -> answer?.numberValue?.content.orEmpty()
        "DATE" -> answer?.dateValue.orEmpty()
        else -> answer?.selections.orEmpty().joinToString(", ")
    }

/** A draft is initialized only from a matching authorized interview, not another participant. */
internal fun initialInterviewDraft(participantId: UUID, view: InterviewView): InterviewDraft? {
    val id = view.id ?: return null
    if (view.participantId != participantId || view.status != "DRAFT") return null
    val answers = view.answers.associateBy { it.code }
    return InterviewDraft(participantId, id, view.questions
        .filter(::editableInterviewAnswer)
        .associate { it.code to interviewAnswerText(answers[it.code], it) })
}
