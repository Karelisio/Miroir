package com.karelisio.miroir

import com.karelisio.miroir.data.local.DayEntity
import com.karelisio.miroir.data.model.AnswerPayload
import com.karelisio.miroir.data.model.QuestionType
import com.karelisio.miroir.ui.history.agreementsOf
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class AgreementsTest {
    private var n = 0

    private fun day(mine: AnswerPayload?, theirs: AnswerPayload?) = DayEntity(
        day = "2026-01-%02d".format(++n), questionId = "q$n", tag = "t", questionText = "?", category = "c", pack = null,
        myAnswer = mine?.let { Json.encodeToString(AnswerPayload.serializer(), it) },
        partnerAnswered = theirs != null,
        partnerAnswer = theirs?.let { Json.encodeToString(AnswerPayload.serializer(), it) },
    )

    private fun who(v: String) = AnswerPayload("q", QuestionType.WHO, who = v, at = 0)
    private fun scale(v: Int) = AnswerPayload("q", QuestionType.SCALE, scale = v, at = 0)
    private fun text() = AnswerPayload("q", QuestionType.TEXT, text = "x", at = 0)

    @Test
    fun `accord compté seulement sur les questions révélées et comparables`() {
        val days = listOf(
            day(who("A"), who("A")), day(who("A"), who("B")), day(who("B"), who("B")), day(who("A"), null),
            day(scale(7), scale(8)), day(scale(3), scale(9)),
            day(text(), text()),
        )
        val stats = agreementsOf(days).associateBy { it.emoji }
        assertEquals(2, stats.size)
        assertEquals(67, stats.getValue("👀").percent)
        assertEquals(3, stats.getValue("👀").total)
        assertEquals(50, stats.getValue("📏").percent)
    }
}
