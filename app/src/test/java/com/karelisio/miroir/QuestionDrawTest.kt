package com.karelisio.miroir

import com.karelisio.miroir.data.draw.QuestionDraw
import com.karelisio.miroir.data.model.Category
import com.karelisio.miroir.data.model.Pack
import com.karelisio.miroir.data.model.Question
import com.karelisio.miroir.data.model.QuestionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.MonthDay

class QuestionDrawTest {
    private val bank = TestBank.questions
    private val pair = "Zm9vYmFyYmF6cXV4MTIzNA"

    private fun input(day: LocalDate, used: Set<String> = emptySet(), skip: Int = 0, block: QuestionDraw.Input.() -> QuestionDraw.Input = { this }) =
        QuestionDraw.Input(pairId = pair, day = day, skipIndex = skip, bank = bank, used = used).block()

    @Test
    fun `la banque contient au moins 2000 questions valides`() {
        assertTrue("taille ${bank.size}", bank.size >= 2000)
        assertEquals(bank.size, bank.map { it.id }.toSet().size)
        val categories = Category.drawable.map { it.key }.toSet()
        bank.forEach { q ->
            assertTrue(q.id, q.category in categories)
            assertTrue(q.id, q.tags.isNotEmpty())
            if (q.type == QuestionType.CHOICE) assertTrue(q.id, q.options.size in 2..4)
            if (q.type == QuestionType.SCALE) assertTrue(q.id, q.minLabel != null && q.maxLabel != null)
            q.pack?.let { assertTrue(q.id, Pack.of(it) != null) }
        }
        // Les 13 catégories et les 6 packs sont fournis.
        assertEquals(categories, bank.filter { it.pack == null }.map { it.category }.toSet())
        assertEquals(Pack.entries.map { it.key }.toSet(), bank.mapNotNull { it.pack }.toSet())
        // Variété des formats.
        assertEquals(QuestionType.entries.toSet(), bank.map { it.type }.toSet())
    }

    @Test
    fun `même paire et même jour donnent la même question sur les deux téléphones`() {
        val day = LocalDate.of(2026, 10, 28)
        val a = QuestionDraw.draw(input(day))
        val b = QuestionDraw.draw(input(day).copy(bank = bank.shuffled()))
        assertEquals(a.id, b.id)
        val other = (1..10).map { QuestionDraw.draw(input(day).copy(pairId = "autre$it-xxxxxxxxxxxxxx")).id }.toSet()
        assertTrue("le tirage dépend du pairId", other.size > 1)
    }

    @Test
    fun `aucune répétition tant que la banque n'est pas épuisée`() {
        val used = linkedSetOf<String>()
        var day = LocalDate.of(2027, 1, 10)
        repeat(1500) {
            val q = QuestionDraw.draw(input(day, used))
            assertTrue("répétition de ${q.id} le $day", used.add(q.id))
            day = day.plusDays(1)
        }
    }

    @Test
    fun `la banque épuisée est recyclée sans les questions récentes`() {
        val regular = bank.filter { it.pack == null }.map { it.id }.toSet()
        val recent = regular.take(50).toSet()
        val q = QuestionDraw.draw(input(LocalDate.of(2027, 3, 3), used = regular) { copy(recent = recent) })
        assertTrue(q.id !in recent)
    }

    @Test
    fun `les packs saisonniers sont proposés à la bonne date`() {
        assertEquals("noel", QuestionDraw.draw(input(LocalDate.of(2026, 12, 25))).pack)
        assertEquals("nouvel_an", QuestionDraw.draw(input(LocalDate.of(2026, 12, 31))).pack)
        assertEquals("saint_valentin", QuestionDraw.draw(input(LocalDate.of(2027, 2, 14))).pack)
        val birthday = QuestionDraw.draw(input(LocalDate.of(2027, 3, 14)) { copy(birthdays = listOf(MonthDay.of(3, 14))) })
        assertEquals("anniversaire", birthday.pack)
        val anniversary = QuestionDraw.draw(input(LocalDate.of(2027, 6, 12)) { copy(anniversary = LocalDate.of(2021, 6, 12)) })
        assertEquals("anniversaire_couple", anniversary.pack)
        // L'été mélange pack et questions habituelles.
        val summer = (0 until 60).map { QuestionDraw.draw(input(LocalDate.of(2027, 7, 1).plusDays(it.toLong()))).pack }
        assertTrue(summer.count { it == "ete" } in 5..45)
        // Packs désactivés : jamais de question saisonnière.
        assertEquals(null, QuestionDraw.draw(input(LocalDate.of(2026, 12, 25)) { copy(packsEnabled = false) }).pack)
    }

    @Test
    fun `les catégories désactivées ne sont jamais tirées`() {
        val disabled = Category.drawable.map { it.key }.filter { it != "fun" && it != "voyages" }.toSet()
        // Printemps : aucun pack actif, seules les catégories comptent.
        repeat(60) {
            val q = QuestionDraw.draw(input(LocalDate.of(2027, 4, 1).plusDays(it.toLong())) { copy(disabledCategories = disabled) })
            assertTrue(q.category in setOf("fun", "voyages"))
        }
    }

    @Test
    fun `passer donne une autre question, toujours déterministe`() {
        val day = LocalDate.of(2027, 5, 5)
        val first = QuestionDraw.draw(input(day))
        val next = QuestionDraw.draw(input(day, skip = 1) { copy(exclude = setOf(first.id)) })
        assertNotEquals(first.id, next.id)
        assertEquals(next.id, QuestionDraw.draw(input(day, skip = 1) { copy(exclude = setOf(first.id)) }).id)
    }

    @Test
    fun `les questions perso entrent dans le tirage le lendemain de leur création`() {
        val custom = (1..3).map {
            QuestionDraw.CustomCandidate(Question("perso_test$it", "perso", QuestionType.TEXT, "Question perso $it ?"), "2027-05-01")
        }
        val sameDay = (0 until 20).map { QuestionDraw.draw(input(LocalDate.of(2027, 5, 1)) { copy(custom = custom, skipIndex = it) }).category }
        assertTrue("pas le jour même", sameDay.none { it == "perso" })
        val later = (0 until 20).map { QuestionDraw.draw(input(LocalDate.of(2027, 5, 2)) { copy(custom = custom, skipIndex = it) }).category }
        assertTrue("tirées ensuite", later.any { it == "perso" })
    }
}
