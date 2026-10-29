package com.karelisio.miroir

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.karelisio.miroir.data.crypto.PairCrypto
import com.karelisio.miroir.data.model.Member
import com.karelisio.miroir.games.data.DrawFilter
import com.karelisio.miroir.games.data.GameAnswerEntity
import com.karelisio.miroir.games.data.GameDraw
import com.karelisio.miroir.games.data.GameRepository
import com.karelisio.miroir.games.data.GameSessionEntity
import com.karelisio.miroir.games.model.GameAnswer
import com.karelisio.miroir.games.model.GameCategory
import com.karelisio.miroir.games.model.GameItem
import com.karelisio.miroir.games.model.GameKind
import com.karelisio.miroir.games.model.GameValue
import com.karelisio.miroir.games.model.ItemFormat
import com.karelisio.miroir.games.model.ItemKind
import com.karelisio.miroir.games.model.MatchKind
import com.karelisio.miroir.games.model.SessionPayload
import com.karelisio.miroir.games.model.Wish
import com.karelisio.miroir.games.ui.DONE_ITEM
import com.karelisio.miroir.games.ui.GameStatsCalculator
import com.karelisio.miroir.games.ui.SessionStatus
import com.karelisio.miroir.games.ui.SessionView
import com.karelisio.miroir.games.ui.judgeKey
import com.karelisio.miroir.games.ui.questionKey
import com.karelisio.miroir.games.ui.turnKey
import com.karelisio.miroir.games.ui.theme.warmScheme
import com.karelisio.miroir.ui.theme.MiroirDarkScheme
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.random.Random

@RunWith(AndroidJUnit4::class)
class GamesTest {
    private val json = Json { encodeDefaults = false; explicitNulls = false }

    private fun item(id: String, cat: GameCategory, kind: ItemKind = ItemKind.DEVINE, intensity: Int = 1, format: ItemFormat? = ItemFormat.CHOICE, remote: Boolean = false) =
        GameItem(id, cat, kind, intensity, "Question $id", format, options = listOf("A", "B", "C"), remote = remote)

    private val bank = buildList {
        GameCategory.entries.forEach { c -> repeat(10) { i -> add(item("${c.key}-$i", c, intensity = 1 + i % 3)) } }
        repeat(6) { add(item("act-$it", GameCategory.FUN, ItemKind.ACTION, format = null, remote = it % 2 == 0)) }
    }

    @Test
    fun `la banque embarquée se charge en entier`() = kotlinx.coroutines.runBlocking {
        val items = com.karelisio.miroir.games.data.GameBank(androidx.test.core.app.ApplicationProvider.getApplicationContext()).all()
        assertEquals(16_000, items.size)
        GameCategory.entries.forEach { c -> assertEquals(c.key, 2_000, items.count { it.category == c }) }
        assertTrue(items.all { it.text.isNotBlank() && it.intensity in 1..3 })
        assertTrue(items.filter { it.kind == ItemKind.DEVINE }.all { it.format != null })
        assertTrue(items.filter { it.kind == ItemKind.PREFERES || it.format == ItemFormat.CHOICE }.all { it.options.size >= 2 })
        assertTrue(items.none { it.kind in setOf(ItemKind.DEVINE, ItemKind.PREFERES) && "{autre}" in it.text })
        assertTrue(items.any { it.kind == ItemKind.ACTION && it.remote })
    }

    @Test
    fun `le tirage respecte l'intensité commune et l'union des exclusions`() {
        val f = DrawFilter(setOf(ItemKind.DEVINE), intensity = 2, excluded = setOf("intime", "fun"))
        val drawn = GameDraw.draw(bank, f, emptyMap(), 200, Random(1))
        assertTrue(drawn.isNotEmpty())
        assertTrue(drawn.all { it.intensity <= 2 && it.category != GameCategory.INTIME && it.category != GameCategory.FUN })
        assertEquals(drawn.size, drawn.map { it.id }.toSet().size)
    }

    @Test
    fun `pas de répétition tant que la catégorie n'est pas épuisée`() {
        val f = DrawFilter(setOf(ItemKind.DEVINE), intensity = 3, categories = setOf(GameCategory.SOUVENIRS))
        val seen = mutableMapOf<String, Long>()
        val first = GameDraw.draw(bank, f, seen, 6, Random(2)).onEach { seen[it.id] = 1 }
        val second = GameDraw.draw(bank, f, seen, 4, Random(3))
        assertTrue("les 4 restantes d'abord", second.none { it.id in seen })
        second.forEach { seen[it.id] = 2 }
        // Catégorie épuisée : les plus anciennes reviennent en premier.
        val third = GameDraw.draw(bank, f, seen, 3, Random(4))
        assertTrue(third.all { seen[it.id] == 1L })
        assertEquals(6, first.size)
    }

    @Test
    fun `à distance, seules les actions faisables à distance sont tirées`() {
        val f = DrawFilter(setOf(ItemKind.ACTION), intensity = 3, remoteOnly = true)
        assertTrue(GameDraw.eligible(bank, f).all { it.remote })
        assertEquals(3, GameDraw.eligible(bank, f).size)
    }

    @Test
    fun `envies aveugles — seuls oui+oui et oui+peut-être coïncident`() {
        val crypto = PairCrypto(ByteArray(32) { it.toByte() }, "pairPairPairPair")
        val item = "rom-b-0700"
        fun tags(m: Member, a: Int) = GameRepository.wishTags(crypto, m, item, a).toList().toSet()
        fun common(a: Int, b: Int) = tags(Member.A, a) intersect tags(Member.B, b)
        fun seenBy(m: Member, mine: Int, other: Int) = GameRepository.matchOf(crypto, m, item, mine, common(if (m == Member.A) mine else other, if (m == Member.A) other else mine))

        assertEquals(1, common(Wish.YES, Wish.YES).size)
        assertEquals(MatchKind.MATCH, seenBy(Member.A, Wish.YES, Wish.YES))
        assertEquals(MatchKind.MATCH, seenBy(Member.B, Wish.YES, Wish.YES))
        assertEquals(MatchKind.DISCUSS, seenBy(Member.A, Wish.YES, Wish.MAYBE))
        assertEquals(MatchKind.DISCUSS, seenBy(Member.B, Wish.MAYBE, Wish.YES))
        assertTrue("peut-être + peut-être ne révèle rien", common(Wish.MAYBE, Wish.MAYBE).isEmpty())
        assertTrue("un non ne révèle jamais rien", common(Wish.YES, Wish.NO).isEmpty() && common(Wish.NO, Wish.YES).isEmpty())
        assertTrue(common(Wish.MAYBE, Wish.NO).isEmpty())
        // Les étiquettes d'un non sont aléatoires : jamais deux fois les mêmes.
        assertNotEquals(tags(Member.A, Wish.NO), tags(Member.A, Wish.NO))
        assertEquals(32, GameRepository.wishSlot(crypto, Member.A, item).length)
    }

    private fun session(kind: GameKind, payload: SessionPayload, createdBy: String = "A", status: String = "open") = GameSessionEntity(
        id = "s-${kind.key}", kind = kind.key, reveal = kind.reveal, createdBy = createdBy,
        payload = json.encodeToString(SessionPayload.serializer(), payload), status = status, createdAt = 1, updatedAt = 1,
    )

    private fun answer(s: GameSessionEntity, member: String, item: String, a: GameAnswer, at: Long = 1) =
        GameAnswerEntity(s.id, member, item, json.encodeToString(GameAnswer.serializer(), a), at)

    @Test
    fun `devine-moi, à qui le tour, puis révélation`() {
        val s = session(GameKind.DEVINE, SessionPayload(items = listOf("souvenirs-0", "souvenirs-1"), answerer = "A"))
        val items = bank.associateBy { it.id }
        assertEquals(SessionStatus.YOUR_TURN, SessionView(s, emptyList(), "A").status(items))
        assertEquals(SessionStatus.YOUR_TURN, SessionView(s, emptyList(), "B").status(items))
        val a = listOf(0, 1).map { answer(s, "A", questionKey(it), GameAnswer(self = GameValue(choice = it))) }
        assertEquals(SessionStatus.WAITING, SessionView(s, a, "A").status(items))
        val b = listOf(0, 1).map { answer(s, "B", questionKey(it), GameAnswer(guess = GameValue(choice = 0))) }
        assertEquals(SessionStatus.REVEAL, SessionView(s, a + b, "B").status(items))
        val results = GameStatsCalculator.guesses(listOf(SessionView(s, a + b, "A")), items)
        assertEquals(listOf(true, false), results.map { it.correct })
    }

    @Test
    fun `devine-moi en texte libre — la personne concernée juge`() {
        val text = item("txt", GameCategory.NOUS_DEUX, format = ItemFormat.TEXT)
        val items = mapOf(text.id to text)
        val s = session(GameKind.DEVINE, SessionPayload(items = listOf(text.id), answerer = "A"))
        val both = listOf(
            answer(s, "A", questionKey(0), GameAnswer(self = GameValue(text = "Le lac"))),
            answer(s, "B", questionKey(0), GameAnswer(guess = GameValue(text = "Un lac"))),
        )
        assertEquals(SessionStatus.YOUR_TURN, SessionView(s, both, "A").status(items))
        assertEquals(SessionStatus.WAITING, SessionView(s, both, "B").status(items))
        val judged = both + answer(s, "A", judgeKey(0), GameAnswer(ok = true))
        assertEquals(SessionStatus.REVEAL, SessionView(s, judged, "B").status(items))
        assertEquals(listOf(true), GameStatsCalculator.guesses(listOf(SessionView(s, judged, "B")), items).map { it.correct })
    }

    @Test
    fun `duel et action ou vérité — tours et fin de partie`() {
        val duel = session(GameKind.DUEL, SessionPayload(items = listOf("fun-0")))
        val done = listOf(answer(duel, "A", DONE_ITEM, GameAnswer(ok = true)))
        assertEquals(SessionStatus.WAITING, SessionView(duel, done, "A").status())
        assertEquals(SessionStatus.YOUR_TURN, SessionView(duel, done, "B").status())

        val av = session(GameKind.ACTION_VERITE, SessionPayload(turns = 2), createdBy = "A")
        assertEquals("la personne défiée commence", SessionStatus.YOUR_TURN, SessionView(av, emptyList(), "B").status())
        val t0 = listOf(answer(av, "B", turnKey(0), GameAnswer(pick = "verite", item = "x")))
        assertEquals(SessionStatus.YOUR_TURN, SessionView(av, t0, "A").status())
        val t1 = t0 + answer(av, "A", turnKey(1), GameAnswer(pick = "action", item = "y", ok = true))
        assertEquals(SessionStatus.REVEAL, SessionView(av, t1, "B").status())
    }

    @Test
    fun `stats — connivence, accords, série de jours et badges`() {
        val items = bank.associateBy { it.id }
        val day = LocalDate.of(2026, 10, 29)
        val at = { d: Int -> day.minusDays(d.toLong()).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli() + 3_600_000 }
        val duel = session(GameKind.DUEL, SessionPayload(items = listOf("souvenirs-0", "futur-0")))
        val answers = listOf(
            answer(duel, "A", questionKey(0), GameAnswer(self = GameValue(choice = 1), guess = GameValue(choice = 2)), at(0)),
            answer(duel, "B", questionKey(0), GameAnswer(self = GameValue(choice = 2), guess = GameValue(choice = 1)), at(0)),
            answer(duel, "A", questionKey(1), GameAnswer(self = GameValue(choice = 0), guess = GameValue(choice = 0)), at(1)),
            answer(duel, "B", questionKey(1), GameAnswer(self = GameValue(choice = 1), guess = GameValue(choice = 1)), at(1)),
            answer(duel, "A", DONE_ITEM, GameAnswer(ok = true)), answer(duel, "B", DONE_ITEM, GameAnswer(ok = true)),
        )
        val stats = GameStatsCalculator.compute(listOf(SessionView(duel, answers, "A")), items, matches = 1, day, ZoneOffset.UTC)
        assertEquals(50, stats.connivence) // 4 pronostics, 2 justes
        assertEquals(2, stats.streak)
        assertEquals(1, stats.played)
        assertTrue(stats.badges.first { it.id == "first" }.unlocked)
        assertTrue(stats.badges.first { it.id == "match" }.unlocked)
        assertFalse(stats.badges.first { it.id == "streak7" }.unlocked)
        assertEquals(setOf(GameCategory.SOUVENIRS, GameCategory.FUTUR), stats.byCategory.map { it.category }.toSet())
    }

    @Test
    fun `semaine ISO du défi et palette chaude`() {
        assertEquals("2026-W44", GameRepository.isoWeek(LocalDate.of(2026, 10, 29)))
        assertEquals("2027-W01", GameRepository.isoWeek(LocalDate.of(2027, 1, 4)))
        val warm = warmScheme(MiroirDarkScheme, dark = true)
        assertNotEquals(MiroirDarkScheme.surface, warm.surface)
        assertEquals(MiroirDarkScheme.surface, warmScheme(MiroirDarkScheme, dark = true, t = 0f).surface)
        assertNull(GameValue().matches(null))
    }
}
