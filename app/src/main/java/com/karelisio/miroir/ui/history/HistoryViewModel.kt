package com.karelisio.miroir.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.karelisio.miroir.AppContainer
import com.karelisio.miroir.data.local.DayEntity
import com.karelisio.miroir.data.model.AnswerPayload
import com.karelisio.miroir.data.model.Category
import com.karelisio.miroir.data.model.Question
import com.karelisio.miroir.data.model.QuestionType
import com.karelisio.miroir.data.prefs.AppPrefs
import com.karelisio.miroir.ui.today.bestStreakOf
import com.karelisio.miroir.ui.today.display
import com.karelisio.miroir.ui.today.myAnswerPayload
import com.karelisio.miroir.ui.today.myReactionPayload
import com.karelisio.miroir.ui.today.partnerAnswerPayload
import com.karelisio.miroir.ui.today.partnerReactionPayload
import com.karelisio.miroir.ui.today.streakOf
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.Normalizer
import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.roundToInt

data class HistoryItem(
    val day: String,
    val date: LocalDate,
    val question: String,
    val category: Category,
    val revealed: Boolean,
    val status: String,
    val reactions: List<String>,
    val searchText: String,
    val favorite: Boolean = false,
)

/** Taux d'accord sur un format de question où l'on peut comparer les deux réponses. */
data class Agreement(val label: String, val emoji: String, val percent: Int, val total: Int)

data class HistoryUi(
    val items: List<HistoryItem> = emptyList(),
    val query: String = "",
    val category: String? = null,
    val favoritesOnly: Boolean = false,
    val together: Int = 0,
    val streak: Int = 0,
    val best: Int = 0,
    val agreements: List<Agreement> = emptyList(),
)

data class DayDetailUi(
    val entity: DayEntity,
    val question: Question?,
    val prefs: AppPrefs,
    /** Question d'hier : encore ouverte jusqu'à ce soir minuit. */
    val isYesterday: Boolean = false,
) {
    /** Question d'hier sans ma réponse : on peut encore y répondre aujourd'hui. */
    val canCatchUp: Boolean get() = isYesterday && entity.myAnswer == null
}

/** Accord sur les « qui de nous deux », les choix et les échelles (à un point près). */
fun agreementsOf(days: List<DayEntity>): List<Agreement> {
    val pairs = days.mapNotNull { d ->
        val a = d.myAnswerPayload()
        val b = d.partnerAnswerPayload()
        if (a != null && b != null && a.type == b.type) a to b else null
    }
    fun stat(type: QuestionType, label: String, emoji: String, agree: (AnswerPayload, AnswerPayload) -> Boolean): Agreement? {
        val sample = pairs.filter { it.first.type == type }
        if (sample.isEmpty()) return null
        val ok = sample.count { (a, b) -> agree(a, b) }
        return Agreement(label, emoji, (100.0 * ok / sample.size).roundToInt(), sample.size)
    }
    return listOfNotNull(
        stat(QuestionType.WHO, "Qui de nous deux", "👀") { a, b -> a.who != null && a.who == b.who },
        stat(QuestionType.CHOICE, "Choix", "⚖️") { a, b -> a.choice != null && a.choice == b.choice },
        stat(QuestionType.SCALE, "Échelles (à 1 près)", "📏") { a, b -> abs((a.scale ?: 0) - (b.scale ?: 0)) <= 1 },
    )
}

private data class Filters(val query: String, val category: String?, val favoritesOnly: Boolean)

class HistoryViewModel(private val c: AppContainer) : ViewModel() {
    private val repo = c.repository
    private val query = MutableStateFlow("")
    private val category = MutableStateFlow<String?>(null)
    private val favoritesOnly = MutableStateFlow(false)
    private val messages = Channel<String>(Channel.BUFFERED)
    val events = messages.receiveAsFlow()

    private val allItems = combine(repo.dao.observeDays(), c.prefs.data) { days, prefs ->
        days to days.map { it.toItem(prefs) }
    }

    private val filters = combine(query, category, favoritesOnly) { q, cat, fav -> Filters(q, cat, fav) }

    val state: StateFlow<HistoryUi> = combine(allItems, filters, repo.todayFlow) { (days, items), f, today ->
        val needle = f.query.normalized()
        HistoryUi(
            items = items.filter {
                (f.category == null || it.category.key == f.category) &&
                    (!f.favoritesOnly || it.favorite) &&
                    (needle.isEmpty() || it.searchText.contains(needle))
            },
            query = f.query,
            category = f.category,
            favoritesOnly = f.favoritesOnly,
            together = days.count { it.myAnswer != null && it.partnerAnswered },
            streak = streakOf(days, today),
            best = bestStreakOf(days),
            agreements = agreementsOf(days),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HistoryUi())

    fun setQuery(q: String) { query.value = q }
    fun setCategory(key: String?) { category.value = key; if (key != null) favoritesOnly.value = false }
    fun setFavoritesOnly(on: Boolean) { favoritesOnly.value = on; if (on) category.value = null }

    fun day(day: String): Flow<DayDetailUi?> = combine(repo.dao.observeDay(day), c.prefs.data, repo.todayFlow) { e, p, today ->
        e?.let {
            DayDetailUi(
                entity = it,
                question = repo.resolveQuestion(it.questionId),
                prefs = p,
                isYesterday = it.day == today.minusDays(1).toString(),
            )
        }
    }

    fun react(day: String, emoji: String?, comment: String?) {
        viewModelScope.launch { repo.react(day, emoji, comment) }
    }

    fun toggleFavorite(day: String, favorite: Boolean) {
        viewModelScope.launch { repo.setFavorite(day, favorite) }
    }

    fun answer(day: String, answer: AnswerPayload) {
        viewModelScope.launch {
            if (!repo.submitAnswer(day, answer)) messages.send("La question a changé entre-temps.")
        }
    }

    fun markRevealSeen(day: String) {
        viewModelScope.launch { repo.markRevealSeen(day) }
    }

    private fun DayEntity.toItem(p: AppPrefs): HistoryItem {
        val mine = myAnswerPayload()
        val theirs = partnerAnswerPayload()
        val nameOf: (String) -> String = { if (it == p.member.key) p.myName.ifBlank { "Toi" } else p.partnerDisplayName }
        val status = when {
            mine != null && theirs != null -> "Révélé 💜"
            mine != null -> "En attente de ${p.partnerDisplayName}"
            partnerAnswered -> "${p.partnerDisplayName} a répondu"
            else -> "Pas de réponse"
        }
        val answers = listOfNotNull(mine?.display(null, nameOf), theirs?.display(null, nameOf), myReactionPayload()?.comment, partnerReactionPayload()?.comment)
        return HistoryItem(
            day = day,
            date = LocalDate.parse(day),
            question = questionText,
            category = Category.of(category),
            revealed = mine != null && theirs != null,
            status = status,
            reactions = listOfNotNull(myReactionPayload()?.emoji, partnerReactionPayload()?.emoji),
            searchText = (listOf(questionText) + answers).joinToString(" ").normalized(),
            favorite = favorite,
        )
    }
}

private fun String.normalized(): String =
    Normalizer.normalize(lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "").trim()
