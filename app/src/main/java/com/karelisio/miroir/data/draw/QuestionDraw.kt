package com.karelisio.miroir.data.draw

import com.karelisio.miroir.data.model.Category
import com.karelisio.miroir.data.model.Pack
import com.karelisio.miroir.data.model.Question
import java.security.MessageDigest
import java.time.LocalDate
import java.time.MonthDay

/**
 * Tirage déterministe de la question du jour : les deux téléphones, avec les mêmes entrées
 * (pairId, date, nombre de "passer", réglages partagés, historique synchronisé), obtiennent
 * exactement la même question. Le Worker verrouille ensuite la première proposition reçue,
 * ce qui couvre les rares cas où les deux historiques ne seraient pas encore alignés.
 */
object QuestionDraw {

    data class Input(
        val pairId: String,
        val day: LocalDate,
        val skipIndex: Int,
        val bank: List<Question>,
        val custom: List<CustomCandidate> = emptyList(),
        val disabledCategories: Set<String> = emptySet(),
        val packsEnabled: Boolean = true,
        val anniversary: LocalDate? = null,
        val birthdays: List<MonthDay> = emptyList(),
        /** Questions déjà tirées les jours précédents (y compris celles passées). */
        val used: Set<String> = emptySet(),
        /** Questions des 60 derniers jours, jamais reproposées même quand la banque est épuisée. */
        val recent: Set<String> = emptySet(),
        /** Questions exclues pour ce tirage (ex. celle qu'on vient de passer). */
        val exclude: Set<String> = emptySet(),
    )

    data class CustomCandidate(val question: Question, val createdDay: String)

    fun draw(input: Input): Question {
        val rng = SplitMix64(seed(input.pairId, input.day, input.skipIndex))
        val blocked = input.used + input.exclude

        // 1. Packs saisonniers et événements, par ordre de priorité.
        if (input.packsEnabled) {
            for (pack in activePacks(input, rng)) {
                val pool = input.bank.filter { it.pack == pack.key && it.id !in input.exclude }
                val fresh = pool.filter { it.id !in blocked }
                val pick = fresh.ifEmpty { pool.filter { it.id !in input.recent } }
                if (pick.isNotEmpty()) return pick[rng.nextInt(pick.size)]
            }
        }

        // 2. Questions perso (créées avant aujourd'hui), une chance sur deux tant qu'il en reste.
        val dayKey = input.day.toString()
        val custom = input.custom
            .filter { it.createdDay < dayKey && it.question.id !in blocked }
            .map { it.question }
            .sortedBy { it.id }
        val customRoll = rng.nextDouble()
        if (custom.isNotEmpty() && customRoll < 0.5) return custom[rng.nextInt(custom.size)]

        // 3. Banque générale : catégorie d'abord (variété), puis question.
        val enabled = Category.drawable.map { it.key }.filter { it !in input.disabledCategories }
            .ifEmpty { Category.drawable.map { it.key } }
        val regular = input.bank.filter { it.pack == null && it.category in enabled }
        var pool = regular.filter { it.id !in blocked }
        if (pool.isEmpty()) pool = regular.filter { it.id !in input.recent && it.id !in input.exclude } // banque épuisée
        if (pool.isEmpty()) pool = regular.filter { it.id !in input.exclude }
        if (pool.isEmpty()) return custom.firstOrNull() ?: input.bank.first()

        val byCategory = pool.groupBy { it.category }.toSortedMap()
        val categories = byCategory.keys.toList()
        val chosen = byCategory.getValue(categories[rng.nextInt(categories.size)])
        return chosen[rng.nextInt(chosen.size)]
    }

    fun activePacks(input: Input, rng: SplitMix64? = null): List<Pack> {
        val d = input.day
        val md = MonthDay.from(d)
        val packs = mutableListOf<Pack>()
        if (input.birthdays.any { it == md }) packs += Pack.ANNIVERSAIRE
        input.anniversary?.let { if (MonthDay.from(it) == md && it.isBefore(d)) packs += Pack.ANNIVERSAIRE_COUPLE }
        if (d.monthValue == 2 && d.dayOfMonth in 12..14) packs += Pack.SAINT_VALENTIN
        if (d.monthValue == 12 && d.dayOfMonth in 20..26) packs += Pack.NOEL
        if ((d.monthValue == 12 && d.dayOfMonth >= 30) || (d.monthValue == 1 && d.dayOfMonth <= 2)) packs += Pack.NOUVEL_AN
        val summer = md >= MonthDay.of(6, 21) && md <= MonthDay.of(9, 22)
        // Été : une question sur trois environ, pour ne pas monopoliser trois mois.
        if (summer && (rng?.nextInt(3) ?: 0) == 0) packs += Pack.ETE
        return packs
    }

    private fun seed(pairId: String, day: LocalDate, skipIndex: Int): Long {
        val h = MessageDigest.getInstance("SHA-256").digest("miroir|$pairId|$day|$skipIndex".toByteArray())
        var s = 0L
        for (i in 0 until 8) s = (s shl 8) or (h[i].toLong() and 0xFF)
        return s
    }

    /** PRNG portable et reproductible (identique sur tous les appareils). */
    class SplitMix64(private var state: Long) {
        fun nextLong(): Long {
            state += -0x61c8864680b583ebL
            var z = state
            z = (z xor (z ushr 30)) * -0x40a7b892e31b1a47L
            z = (z xor (z ushr 27)) * -0x6b2fb644ecceee15L
            return z xor (z ushr 31)
        }

        fun nextInt(bound: Int): Int {
            require(bound > 0)
            return ((nextLong() ushr 1) % bound).toInt()
        }

        fun nextDouble(): Double = (nextLong() ushr 11) * (1.0 / (1L shl 53))
    }
}
