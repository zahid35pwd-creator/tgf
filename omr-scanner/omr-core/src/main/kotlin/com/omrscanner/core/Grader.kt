package com.omrscanner.core

enum class Verdict { CORRECT, WRONG, MULTIPLE, BLANK, UNKEYED }

class QuestionGrade(val question: Int, val marked: List<Int>, val key: Int?, val verdict: Verdict, val needsReview: Boolean)

class GradeResult(
    val questions: List<QuestionGrade>,
    val score: Double,
    val maxScore: Double,
) {
    val correct get() = questions.count { it.verdict == Verdict.CORRECT }
    val wrong get() = questions.count { it.verdict == Verdict.WRONG }
    val multiple get() = questions.count { it.verdict == Verdict.MULTIPLE }
    val blank get() = questions.count { it.verdict == Verdict.BLANK }
    val reviewCount get() = questions.count { it.needsReview }
    val percent get() = if (maxScore > 0) 100.0 * score / maxScore else 0.0
}

data class Marking(val perCorrect: Double = 1.0, val perWrong: Double = 0.0) {
    init {
        require(perCorrect > 0) { "marks per correct answer must be positive" }
        require(perWrong >= 0) { "negative marking is given as a positive number" }
    }
}

object Grader {
    /**
     * Grades [answers] against [key] (index = question, value = correct option or null to skip).
     * Several filled bubbles count as a wrong answer, as on most exam OMR sheets.
     */
    fun grade(answers: List<QuestionReading>, key: List<Int?>, marking: Marking = Marking()): GradeResult {
        var score = 0.0
        var max = 0.0
        val grades = answers.map { a ->
            val k = key.getOrNull(a.question)
            val verdict = when {
                k == null -> Verdict.UNKEYED
                a.status == MarkStatus.BLANK -> Verdict.BLANK
                a.status == MarkStatus.MULTIPLE -> Verdict.MULTIPLE
                a.answer == k -> Verdict.CORRECT
                else -> Verdict.WRONG
            }
            if (k != null) max += marking.perCorrect
            when (verdict) {
                Verdict.CORRECT -> score += marking.perCorrect
                Verdict.WRONG, Verdict.MULTIPLE -> score -= marking.perWrong
                else -> {}
            }
            QuestionGrade(a.question, a.marked, k, verdict, a.needsReview)
        }
        return GradeResult(grades, score, max)
    }
}

/** Compact text form of an answer key: one letter per question, '-' for "no key". */
object AnswerKeyCodec {
    fun encode(key: List<Int?>) = key.joinToString("") { it?.let { o -> ('A' + o).toString() } ?: "-" }

    fun decode(text: String, optionCount: Int = SheetLayout.MAX_OPTIONS): List<Int?> = text.trim().map { ch ->
        val o = ch.uppercaseChar() - 'A'
        if (o in 0 until optionCount) o else null
    }
}
