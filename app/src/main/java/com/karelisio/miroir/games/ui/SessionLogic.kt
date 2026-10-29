package com.karelisio.miroir.games.ui

import com.karelisio.miroir.games.data.GameAnswerEntity
import com.karelisio.miroir.games.data.GameSessionEntity
import com.karelisio.miroir.games.model.GameAnswer
import com.karelisio.miroir.games.model.GameCategory
import com.karelisio.miroir.games.model.GameItem
import com.karelisio.miroir.games.model.GameKind
import com.karelisio.miroir.games.model.ItemFormat
import com.karelisio.miroir.games.model.SessionPayload
import kotlinx.serialization.json.Json
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.roundToInt

private val json = Json { ignoreUnknownKeys = true }

enum class SessionStatus { YOUR_TURN, WAITING, REVEAL, DONE }

const val DONE_ITEM = "\$done"
fun questionKey(i: Int) = "q$i"
fun judgeKey(i: Int) = "~j$i"
fun turnKey(i: Int) = "~t$i"
const val DEFI_DONE = "~done"

/** Une partie et ses réponses, vue depuis mon téléphone. */
class SessionView(
    val entity: GameSessionEntity,
    answers: List<GameAnswerEntity>,
    val me: String,
) {
    val kind: GameKind? = GameKind.of(entity.kind)
    val payload: SessionPayload = runCatching { json.decodeFromString(SessionPayload.serializer(), entity.payload) }.getOrDefault(SessionPayload())
    val partner: String = if (me == "A") "B" else "A"
    private val byKey = answers.associateBy { it.member to it.item }

    fun row(member: String, item: String): GameAnswerEntity? = byKey[member to item]
    fun answered(member: String, item: String) = row(member, item) != null
    fun answer(member: String, item: String): GameAnswer? =
        row(member, item)?.payload?.let { runCatching { json.decodeFromString(GameAnswer.serializer(), it) }.getOrNull() }

    val count: Int get() = payload.items.size
    val done: (String) -> Boolean = { member -> answered(member, DONE_ITEM) }

    /** Devine-moi : la personne qui répond sur elle-même. */
    val answerer: String get() = payload.answerer ?: entity.createdBy
    val guesser: String get() = if (answerer == "A") "B" else "A"

    /** Action ou vérité : tours joués, et à qui le tour (la personne défiée commence). */
    val turnsPlayed: Int get() = generateSequence(0) { it + 1 }.takeWhile { i -> (0..1).any { answered(it.memberKey(), turnKey(i)) } }.count()
    private fun Int.memberKey() = if (this == 0) "A" else "B"
    val challenged: String get() = if (entity.createdBy == "A") "B" else "A"
    fun turnPlayer(i: Int): String = if (i % 2 == 0) challenged else entity.createdBy
    val totalTurns: Int get() = payload.turns ?: 6

    fun pendingJudges(items: Map<String, GameItem>): List<Int> = payload.items.indices.filter { i ->
        items[payload.items[i]]?.format == ItemFormat.TEXT &&
            answered(answerer, questionKey(i)) && answered(guesser, questionKey(i)) && !answered(answerer, judgeKey(i))
    }

    fun status(items: Map<String, GameItem> = emptyMap()): SessionStatus {
        if (entity.status == "cancelled" || entity.status == "done" && kind != GameKind.DEFI) return SessionStatus.DONE
        val keys = payload.items.indices.map(::questionKey)
        return when (kind) {
            GameKind.DEVINE -> {
                val answererAll = keys.all { answered(answerer, it) }
                val guesserAll = keys.all { answered(guesser, it) }
                val judges = pendingJudges(items).isNotEmpty()
                when {
                    me == answerer && (!answererAll || judges) -> SessionStatus.YOUR_TURN
                    me == guesser && !guesserAll -> SessionStatus.YOUR_TURN
                    !answererAll || !guesserAll || judges -> SessionStatus.WAITING
                    else -> SessionStatus.REVEAL
                }
            }
            GameKind.DUEL, GameKind.PREFERES -> when {
                !done(me) -> SessionStatus.YOUR_TURN
                !done(partner) -> SessionStatus.WAITING
                else -> SessionStatus.REVEAL
            }
            GameKind.ACTION_VERITE -> when {
                turnsPlayed >= totalTurns -> SessionStatus.REVEAL
                turnPlayer(turnsPlayed) == me -> SessionStatus.YOUR_TURN
                else -> SessionStatus.WAITING
            }
            GameKind.SURPRISE -> when {
                answered(challenged, questionKey(0)) -> SessionStatus.REVEAL
                me == challenged -> SessionStatus.YOUR_TURN
                else -> SessionStatus.WAITING
            }
            GameKind.DEFI -> if (answered("A", DEFI_DONE) || answered("B", DEFI_DONE)) SessionStatus.DONE else SessionStatus.YOUR_TURN
            GameKind.CANAPE, null -> SessionStatus.DONE
        }
    }
}

fun statusLabel(status: SessionStatus, partnerName: String): String = when (status) {
    SessionStatus.YOUR_TURN -> "À toi !"
    SessionStatus.WAITING -> "En attente de $partnerName"
    SessionStatus.REVEAL -> "Révélation prête ✨"
    SessionStatus.DONE -> "Terminée"
}

/** Un pronostic jugé : catégorie de la question et justesse. */
data class GuessResult(val category: GameCategory, val correct: Boolean)

data class CategoryScore(val category: GameCategory, val percent: Int, val total: Int)

data class Badge(val id: String, val emoji: String, val title: String, val description: String, val unlocked: Boolean)

data class GameStats(
    val connivence: Int?,
    val points: Int,
    val byCategory: List<CategoryScore>,
    val agreementByCategory: List<CategoryScore>,
    val played: Int,
    val streak: Int,
    val bestStreak: Int,
    val matches: Int,
    val badges: List<Badge>,
)

/** Statistiques de connivence : pronostics justes (Devine-moi, Duel, canapé), accords (Tu préfères), envies communes. */
object GameStatsCalculator {
    fun guesses(views: List<SessionView>, items: Map<String, GameItem>): List<GuessResult> = views.flatMap { v ->
        v.payload.items.mapIndexedNotNull { i, id ->
            val item = items[id] ?: return@mapIndexedNotNull null
            val key = questionKey(i)
            when (v.kind) {
                GameKind.DEVINE -> {
                    val self = v.answer(v.answerer, key)?.self
                    val guess = v.answer(v.guesser, key)?.guess
                    val correct = if (item.format == ItemFormat.TEXT) v.answer(v.answerer, judgeKey(i))?.ok else guess?.matches(self)
                    listOfNotNull(correct?.let { GuessResult(item.category, it) })
                }
                GameKind.DUEL -> listOf("A" to "B", "B" to "A").mapNotNull { (g, s) ->
                    v.answer(g, key)?.guess?.matches(v.answer(s, key)?.self)?.let { GuessResult(item.category, it) }
                }
                else -> emptyList()
            }
        }.flatten()
    }

    fun agreements(views: List<SessionView>, items: Map<String, GameItem>): List<GuessResult> = views.filter { it.kind == GameKind.PREFERES }.flatMap { v ->
        v.payload.items.mapIndexedNotNull { i, id ->
            val item = items[id] ?: return@mapIndexedNotNull null
            val a = v.answer("A", questionKey(i))?.self?.choice ?: return@mapIndexedNotNull null
            val b = v.answer("B", questionKey(i))?.self?.choice ?: return@mapIndexedNotNull null
            GuessResult(item.category, a == b)
        }
    }

    private fun byCategory(results: List<GuessResult>) = results.groupBy { it.category }.map { (c, list) ->
        CategoryScore(c, (100.0 * list.count { it.correct } / list.size).roundToInt(), list.size)
    }.sortedBy { it.category.ordinal }

    /** Jours où j'ai joué (réponse donnée ou partie canapé lancée). */
    fun playedDays(views: List<SessionView>, zone: ZoneId): Set<LocalDate> = views.flatMap { v ->
        val mine = listOfNotNull(
            v.entity.createdAt.takeIf { v.kind == GameKind.CANAPE && v.entity.createdBy == v.me },
        ) + v.payload.items.indices.mapNotNull { v.row(v.me, questionKey(it))?.at } +
            (0 until 20).mapNotNull { v.row(v.me, turnKey(it))?.at }
        mine.filter { it > 0 }.map { Instant.ofEpochMilli(it).atZone(zone).toLocalDate() }
    }.toSet()

    fun streak(days: Set<LocalDate>, today: LocalDate): Int {
        var d = if (today in days) today else today.minusDays(1)
        var n = 0
        while (d in days) { n++; d = d.minusDays(1) }
        return n
    }

    fun best(days: Set<LocalDate>): Int {
        var best = 0
        var run = 0
        var prev: LocalDate? = null
        for (d in days.sorted()) {
            run = if (prev != null && prev.plusDays(1) == d) run + 1 else 1
            best = maxOf(best, run)
            prev = d
        }
        return best
    }

    fun compute(views: List<SessionView>, items: Map<String, GameItem>, matches: Int, today: LocalDate, zone: ZoneId): GameStats {
        val guesses = guesses(views, items)
        val agreements = agreements(views, items)
        val canapePoints = views.filter { it.kind == GameKind.CANAPE }.sumOf { it.payload.score ?: 0 }
        val days = playedDays(views, zone)
        val finished = views.count { it.kind != GameKind.DEFI && it.status(items) in setOf(SessionStatus.REVEAL, SessionStatus.DONE) }
        val defisDone = views.count { it.kind == GameKind.DEFI && it.status(items) == SessionStatus.DONE }
        val categories = (guesses + agreements).map { it.category }.toSet()
        val spicyRounds = views.any { v -> v.payload.items.any { items[it]?.intensity == 3 } && v.status(items) != SessionStatus.YOUR_TURN }
        val streak = streak(days, today)
        val best = best(days)
        val correctRun = guesses.fold(0 to 0) { (run, max), g -> if (g.correct) (run + 1).let { it to maxOf(max, it) } else 0 to max }.second
        val badges = listOf(
            Badge("first", "🎲", "Premier pas", "Terminer une première partie", finished >= 1),
            Badge("ten", "🔟", "On y prend goût", "10 parties terminées", finished >= 10),
            Badge("fifty", "🏆", "Inséparables", "50 parties terminées", finished >= 50),
            Badge("twins", "🪞", "Âmes jumelles", "5 bons pronostics d'affilée", correctRun >= 5),
            Badge("mind", "🔮", "Télépathes", "80 % de connivence sur 20 pronostics", guesses.size >= 20 && guesses.count { it.correct } * 100 >= guesses.size * 80),
            Badge("streak7", "🔥", "Une semaine de jeux", "Jouer 7 jours d'affilée", best >= 7),
            Badge("streak30", "🌋", "Le feu sacré", "Jouer 30 jours d'affilée", best >= 30),
            Badge("explore", "🧭", "Explorateurs", "Jouer dans 6 catégories", categories.size >= 6),
            Badge("match", "💞", "Premier point commun", "Trouver une envie commune", matches >= 1),
            Badge("match10", "✨", "Sur la même longueur d'onde", "10 envies communes", matches >= 10),
            Badge("defi", "🏅", "Relevé !", "Réussir un défi de la semaine", defisDone >= 1),
            Badge("defi4", "🎖️", "Un mois de défis", "Réussir 4 défis de la semaine", defisDone >= 4),
            Badge("spicy", "🌶", "Sans filtre", "Jouer une partie en intensité brûlante", spicyRounds),
            Badge("canape", "🛋️", "Soirée canapé", "Jouer 3 parties en mode canapé", views.count { it.kind == GameKind.CANAPE } >= 3),
        )
        return GameStats(
            connivence = if (guesses.isEmpty()) null else (100.0 * guesses.count { it.correct } / guesses.size).roundToInt(),
            points = guesses.count { it.correct } + agreements.count { it.correct } + canapePoints,
            byCategory = byCategory(guesses),
            agreementByCategory = byCategory(agreements),
            played = finished,
            streak = streak,
            bestStreak = best,
            matches = matches,
            badges = badges,
        )
    }
}
