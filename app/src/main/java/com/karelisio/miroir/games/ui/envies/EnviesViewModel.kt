package com.karelisio.miroir.games.ui.envies

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.karelisio.miroir.AppContainer
import com.karelisio.miroir.games.data.GameDraw
import com.karelisio.miroir.games.model.GameCategory
import com.karelisio.miroir.games.model.GameItem
import com.karelisio.miroir.games.model.ItemKind
import com.karelisio.miroir.games.model.MatchKind
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class EnvieMatch(val item: GameItem, val kind: MatchKind, val fresh: Boolean)

data class EnviesUi(
    val loading: Boolean = true,
    val category: GameCategory? = null,
    val categories: List<GameCategory> = emptyList(),
    val queue: List<GameItem> = emptyList(),
    val answered: Map<String, Int> = emptyMap(),
    val total: Int = 0,
    val matches: List<EnvieMatch> = emptyList(),
    val partnerName: String = "",
    val lockSpicy: Boolean = false,
    val spicy: Boolean = false,
    val all: List<GameItem> = emptyList(),
)

/** Envies communes : chacun répond en secret ; seuls oui+oui et oui+peut-être reviennent du serveur. */
class EnviesViewModel(private val c: AppContainer) : ViewModel() {
    private val games = c.games
    private val category = MutableStateFlow<GameCategory?>(null)

    val state: StateFlow<EnviesUi> = combine(
        games.dao.observeWishes(), games.dao.observeMatches(), games.dao.observeCustom(), c.prefs.data, category,
    ) { wishes, matches, _, prefs, cat ->
        val filter = games.filter(setOf(ItemKind.ENVIE))
        val items = GameDraw.eligible(games.allItems(), filter).sortedWith(compareBy({ it.category.ordinal }, { it.intensity }, { it.id }))
        val answered = wishes.associate { it.itemId to it.answer }
        val inCat = items.filter { cat == null || it.category == cat }
        val byId = items.associateBy { it.id }
        EnviesUi(
            loading = false,
            category = cat,
            categories = items.map { it.category }.distinct(),
            queue = inCat.filter { it.id !in answered },
            answered = answered,
            total = inCat.size,
            matches = matches.mapNotNull { m ->
                val item = byId[m.itemId] ?: games.item(m.itemId) ?: return@mapNotNull null
                EnvieMatch(item, runCatching { MatchKind.valueOf(m.kind) }.getOrDefault(MatchKind.DISCUSS), !m.seen)
            },
            partnerName = prefs.partnerDisplayName,
            lockSpicy = prefs.gamesLockSpicy,
            spicy = items.any { it.category.spicy },
            all = items,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), EnviesUi())

    init {
        refresh()
    }

    fun setCategory(c: GameCategory?) { category.value = c }

    fun answer(item: GameItem, value: Int) = viewModelScope.launch { games.setWish(item.id, value) }

    fun refresh() = viewModelScope.launch { runCatching { games.flushPending(); games.sync() } }

    fun markSeen() = viewModelScope.launch { games.markMatchesSeen() }
}
