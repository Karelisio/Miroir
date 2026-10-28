package com.karelisio.miroir.ui.recap

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.karelisio.miroir.AppContainer
import com.karelisio.miroir.data.local.DayEntity
import com.karelisio.miroir.data.model.Category
import com.karelisio.miroir.ui.history.agreementsOf
import com.karelisio.miroir.ui.history.guessScoresOf
import com.karelisio.miroir.ui.history.overall
import com.karelisio.miroir.ui.today.bestStreakOf
import com.karelisio.miroir.ui.today.display
import com.karelisio.miroir.ui.today.myAnswerPayload
import com.karelisio.miroir.ui.today.myReactionPayload
import com.karelisio.miroir.ui.today.partnerAnswerPayload
import com.karelisio.miroir.ui.today.partnerReactionPayload
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.YearMonth
import kotlin.random.Random

/** Un souvenir mis en avant dans le récap (favori de préférence). */
data class Highlight(val day: String, val question: String, val mine: String, val theirs: String, val favorite: Boolean)

data class Recap(
    val month: YearMonth,
    /** Jours du mois avec une question. */
    val days: Int,
    /** Jours où les deux ont répondu. */
    val together: Int,
    val bestStreak: Int,
    /** Emojis les plus utilisés, avec leur nombre. */
    val reactions: List<Pair<String, Int>>,
    val comments: Int,
    val agreement: Int?,
    val guess: Int?,
    val topCategory: Category?,
    val highlight: Highlight? = null,
)

/** Mois (avec au moins une question faite à deux), du plus ancien au plus récent. */
fun recapMonths(days: List<DayEntity>): List<YearMonth> =
    days.filter { it.myAnswer != null && it.partnerAnswered }.mapNotNull { runCatching { YearMonth.parse(it.day.take(7)) }.getOrNull() }.distinct().sorted()

/** Chiffres du mois, sans le souvenir mis en avant (qui demande de résoudre la question). */
fun recapOf(month: YearMonth, all: List<DayEntity>, myName: String, partnerName: String): Recap {
    val days = all.filter { it.day.startsWith(month.toString()) }
    val both = days.filter { it.myAnswerPayload() != null && it.partnerAnswerPayload() != null }
    val reactions = days.flatMap { listOfNotNull(it.myReactionPayload(), it.partnerReactionPayload()) }
    return Recap(
        month = month,
        days = days.size,
        together = days.count { it.myAnswer != null && it.partnerAnswered },
        bestStreak = bestStreakOf(days),
        reactions = reactions.mapNotNull { it.emoji }.groupingBy { it }.eachCount().entries
            .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key }).take(3).map { it.key to it.value },
        comments = reactions.count { !it.comment.isNullOrBlank() },
        agreement = agreementsOf(both).overall(),
        guess = guessScoresOf(both, myName, partnerName).overall(),
        topCategory = both.groupingBy { it.category }.eachCount().maxByOrNull { it.value }?.key?.let { Category.of(it) },
    )
}

class RecapViewModel(private val c: AppContainer, initial: YearMonth?) : ViewModel() {
    private val repo = c.repository
    private val month = MutableStateFlow<YearMonth?>(initial)

    /** (récap du mois affiché, mois disponibles) */
    val state: StateFlow<Pair<Recap, List<YearMonth>>?> = combine(month, repo.dao.observeDays(), c.prefs.data) { selected, days, p ->
        // Sans mois demandé : le plus récent qui a des souvenirs.
        val m = selected ?: recapMonths(days).lastOrNull() ?: YearMonth.from(repo.today())
        val myName = p.myName.ifBlank { "Toi" }
        val recap = recapOf(m, days, myName, p.partnerDisplayName)
        val nameOf: (String) -> String = { if (it == p.member.key) myName else p.partnerDisplayName }
        // Souvenir du mois : un favori, sinon une question révélée, tiré de façon stable pour ce mois.
        val revealed = days.filter { it.day.startsWith(m.toString()) && it.myAnswerPayload() != null && it.partnerAnswerPayload() != null }
        val pool = revealed.filter { it.favorite }.ifEmpty { revealed }
        val pick = pool.takeIf { it.isNotEmpty() }?.let { it[Random(m.hashCode()).nextInt(it.size)] }
        val highlight = pick?.let { d ->
            val q = repo.resolveQuestion(d.questionId)
            Highlight(
                d.day, d.questionText,
                "$myName : ${d.myAnswerPayload()!!.display(q, nameOf)}",
                "${p.partnerDisplayName} : ${d.partnerAnswerPayload()!!.display(q, nameOf)}",
                d.favorite,
            )
        }
        recap.copy(highlight = highlight) to recapMonths(days)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun show(m: YearMonth) { month.value = m }
}
