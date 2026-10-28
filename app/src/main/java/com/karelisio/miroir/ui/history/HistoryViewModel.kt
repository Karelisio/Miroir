package com.karelisio.miroir.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.karelisio.miroir.AppContainer
import com.karelisio.miroir.data.local.DayEntity
import com.karelisio.miroir.data.model.Category
import com.karelisio.miroir.data.model.Question
import com.karelisio.miroir.data.prefs.AppPrefs
import com.karelisio.miroir.ui.today.bestStreakOf
import com.karelisio.miroir.ui.today.display
import com.karelisio.miroir.ui.today.myAnswerPayload
import com.karelisio.miroir.ui.today.myReactionPayload
import com.karelisio.miroir.ui.today.partnerAnswerPayload
import com.karelisio.miroir.ui.today.partnerReactionPayload
import com.karelisio.miroir.ui.today.streakOf
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.Normalizer
import java.time.LocalDate

data class HistoryItem(
    val day: String,
    val date: LocalDate,
    val question: String,
    val category: Category,
    val revealed: Boolean,
    val status: String,
    val reactions: List<String>,
    val searchText: String,
)

data class HistoryUi(
    val items: List<HistoryItem> = emptyList(),
    val query: String = "",
    val category: String? = null,
    val together: Int = 0,
    val streak: Int = 0,
    val best: Int = 0,
)

data class DayDetailUi(
    val entity: DayEntity,
    val question: Question?,
    val prefs: AppPrefs,
)

class HistoryViewModel(private val c: AppContainer) : ViewModel() {
    private val repo = c.repository
    private val query = MutableStateFlow("")
    private val category = MutableStateFlow<String?>(null)

    private val allItems = combine(repo.dao.observeDays(), c.prefs.data) { days, prefs ->
        days to days.map { it.toItem(prefs) }
    }

    val state: StateFlow<HistoryUi> = combine(allItems, query, category, repo.todayFlow) { (days, items), q, cat, today ->
        val needle = q.normalized()
        HistoryUi(
            items = items.filter { (cat == null || it.category.key == cat) && (needle.isEmpty() || it.searchText.contains(needle)) },
            query = q,
            category = cat,
            together = days.count { it.myAnswer != null && it.partnerAnswered },
            streak = streakOf(days, today),
            best = bestStreakOf(days),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HistoryUi())

    fun setQuery(q: String) { query.value = q }
    fun setCategory(key: String?) { category.value = key }

    fun day(day: String): Flow<DayDetailUi?> = combine(repo.dao.observeDay(day), c.prefs.data) { e, p ->
        e?.let { DayDetailUi(it, repo.resolveQuestion(it.questionId), p) }
    }

    fun react(day: String, emoji: String?, comment: String?) {
        viewModelScope.launch { repo.react(day, emoji, comment) }
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
        )
    }
}

private fun String.normalized(): String =
    Normalizer.normalize(lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "").trim()
