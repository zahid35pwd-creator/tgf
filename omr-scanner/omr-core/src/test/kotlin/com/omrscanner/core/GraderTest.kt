package com.omrscanner.core

import kotlin.test.Test
import kotlin.test.assertEquals

class GraderTest {
    private fun reading(q: Int, vararg marked: Int) = QuestionReading(q, DoubleArray(4), marked.toList(), false)

    @Test
    fun gradesWithNegativeMarking() {
        val answers = listOf(reading(0, 1), reading(1, 2), reading(2), reading(3, 0, 3), reading(4, 2))
        val key = listOf(1, 0, 3, 3, null)
        val g = Grader.grade(answers, key, Marking(perCorrect = 1.0, perWrong = 0.25))
        assertEquals(listOf(Verdict.CORRECT, Verdict.WRONG, Verdict.BLANK, Verdict.MULTIPLE, Verdict.UNKEYED), g.questions.map { it.verdict })
        assertEquals(1.0 - 0.25 - 0.25, g.score, 1e-9)
        assertEquals(4.0, g.maxScore, 1e-9)
        assertEquals(1, g.correct)
        assertEquals(1, g.wrong)
        assertEquals(1, g.blank)
        assertEquals(1, g.multiple)
    }

    @Test
    fun keyCodecRoundTrips() {
        val key = listOf(0, 3, null, 4, 1)
        assertEquals("AD-EB", AnswerKeyCodec.encode(key))
        assertEquals(key, AnswerKeyCodec.decode("AD-EB"))
        assertEquals(listOf(0, 3, null, null, 1), AnswerKeyCodec.decode("ad-eb", optionCount = 4))
    }
}
