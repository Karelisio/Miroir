package com.karelisio.miroir

import com.karelisio.miroir.data.local.DayEntity
import com.karelisio.miroir.data.model.AnswerPayload
import com.karelisio.miroir.data.model.Guess
import com.karelisio.miroir.data.model.QuestionType
import com.karelisio.miroir.data.model.ReactionPayload
import com.karelisio.miroir.ui.history.guessScoresOf
import com.karelisio.miroir.ui.history.overall
import com.karelisio.miroir.ui.recap.recapMonths
import com.karelisio.miroir.ui.recap.recapOf
import com.karelisio.miroir.ui.today.daysTogether
import com.karelisio.miroir.ui.today.milestoneOf
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class FeaturesTest {
    private val json = Json { encodeDefaults = false; explicitNulls = false }

    private fun day(date: String, mine: AnswerPayload?, theirs: AnswerPayload?, category: String = "fun", myEmoji: String? = null, comment: String? = null) = DayEntity(
        day = date, questionId = "q-$date", tag = "t", questionText = "?", category = category,
        myAnswer = mine?.let { json.encodeToString(AnswerPayload.serializer(), it) },
        partnerAnswered = theirs != null,
        partnerAnswer = theirs?.let { json.encodeToString(AnswerPayload.serializer(), it) },
        myReaction = if (myEmoji != null || comment != null) json.encodeToString(ReactionPayload.serializer(), ReactionPayload(myEmoji, comment, 0)) else null,
    )

    private fun choice(v: Int, guess: Int? = null) = AnswerPayload("q", QuestionType.CHOICE, choice = v, at = 0, guess = guess?.let { Guess(choice = it) })
    private fun scale(v: Int, guess: Int? = null) = AnswerPayload("q", QuestionType.SCALE, scale = v, at = 0, guess = guess?.let { Guess(scale = it) })

    @Test
    fun `un pronostic se compare à la réponse de l'autre (échelle à un point près)`() {
        assertEquals(true, Guess(choice = 1).matches(choice(1)))
        assertEquals(false, Guess(choice = 0).matches(choice(1)))
        assertEquals(true, Guess(scale = 6).matches(scale(7)))
        assertEquals(false, Guess(scale = 4).matches(scale(7)))
        assertEquals(true, Guess(who = "B").matches(AnswerPayload("q", QuestionType.WHO, who = "B", at = 0)))
        assertNull(Guess().matches(choice(1)))
    }

    @Test
    fun `les anciennes réponses sans pronostic restent lisibles`() {
        val old = """{"questionId":"q","type":"choice","choice":1,"at":0}"""
        val parsed = Json { ignoreUnknownKeys = true }.decodeFromString(AnswerPayload.serializer(), old)
        assertNull(parsed.guess)
        // Et une nouvelle réponse se lit avec un décodeur qui ignore les champs inconnus (anciennes versions).
        val withGuess = json.encodeToString(AnswerPayload.serializer(), choice(1, guess = 0))
        assertEquals(1, Json { ignoreUnknownKeys = true }.decodeFromString(AnswerPayload.serializer(), withGuess).choice)
    }

    @Test
    fun `score des pronostics dans chaque sens`() {
        val days = listOf(
            day("2026-10-01", choice(0, guess = 1), choice(1, guess = 0)), // les deux devinent juste
            day("2026-10-02", choice(0, guess = 0), choice(1)),            // je me trompe, l'autre ne devine pas
            day("2026-10-03", scale(5, guess = 9), scale(8, guess = 4)),   // à un point près : les deux pronostics sont bons
        )
        val scores = guessScoresOf(days, "Léa", "Sam")
        assertEquals("Léa devine Sam", scores[0].label)
        assertEquals(67, scores[0].percent)
        assertEquals(3, scores[0].total)
        assertEquals(100, scores[1].percent)
        assertEquals(2, scores[1].total)
        assertEquals(80, scores.overall())
    }

    @Test
    fun `compteur ensemble et paliers`() {
        val since = LocalDate.of(2023, 2, 14)
        assertEquals(0L, daysTogether(since, since))
        assertNull(daysTogether(since, since.minusDays(1)))
        assertNull(milestoneOf(since, since))
        assertTrue(milestoneOf(since, since.plusDays(1000))!!.title.contains("1"))
        assertEquals("💍", milestoneOf(since, LocalDate.of(2026, 2, 14))!!.emoji)
        assertTrue(milestoneOf(since, LocalDate.of(2026, 2, 14))!!.title.startsWith("3 ans"))
        assertNull(milestoneOf(since, since.plusDays(101)))
        assertNull(milestoneOf(null, since))
    }

    @Test
    fun `récap du mois`() {
        val days = listOf(
            day("2026-09-30", choice(0), choice(0)),
            day("2026-10-01", choice(0), choice(0), category = "souvenirs", myEmoji = "😂"),
            day("2026-10-02", choice(0), choice(1), category = "souvenirs", myEmoji = "😂", comment = "Trop drôle"),
            day("2026-10-03", choice(0), null, myEmoji = "💜"),
            day("2026-10-05", scale(5), scale(6), category = "fun"),
        )
        val r = recapOf(YearMonth.of(2026, 10), days, "Léa", "Sam")
        assertEquals(4, r.days)
        assertEquals(3, r.together)
        assertEquals(2, r.bestStreak)
        assertEquals(listOf("😂" to 2, "💜" to 1), r.reactions)
        assertEquals(1, r.comments)
        assertEquals(67, r.agreement)
        assertEquals("souvenirs", r.topCategory?.key)
        assertEquals(listOf(YearMonth.of(2026, 9), YearMonth.of(2026, 10)), recapMonths(days))
    }
}
