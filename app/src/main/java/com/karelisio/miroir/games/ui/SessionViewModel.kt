package com.karelisio.miroir.games.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.karelisio.miroir.AppContainer
import com.karelisio.miroir.games.data.GamePinEntity
import com.karelisio.miroir.games.model.GameAnswer
import com.karelisio.miroir.games.model.GameItem
import com.karelisio.miroir.games.model.GameValue
import com.karelisio.miroir.games.model.ItemKind
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SessionUi(
    val view: SessionView,
    val items: List<GameItem?>,
    val itemsById: Map<String, GameItem>,
    val myName: String,
    val partnerName: String,
    val pins: Set<String>,
    val spicy: Boolean,
    val lockSpicy: Boolean,
) {
    val status: SessionStatus get() = view.status(itemsById)
    fun name(member: String) = if (member == view.me) myName else partnerName
}

/** Une partie à distance (Devine-moi, Duel, Tu préfères, Action ou vérité, Question surprise, défi). */
class SessionViewModel(private val c: AppContainer, private val id: String) : ViewModel() {
    private val games = c.games
    private val messages = Channel<String>(Channel.BUFFERED)
    val events = messages.receiveAsFlow()

    val state: StateFlow<SessionUi?> = combine(
        games.dao.observeSession(id), games.dao.observeAnswers(id), games.dao.observePins(), c.prefs.data,
    ) { session, answers, pins, prefs ->
        session ?: return@combine null
        val view = SessionView(session, answers, prefs.member.key)
        val turnItems = answers.mapNotNull { games.answerOf(it)?.item }
        val all = games.items((view.payload.items + turnItems).distinct()).associateBy { it.id }
        SessionUi(
            view = view,
            items = view.payload.items.map { all[it] },
            itemsById = all,
            myName = prefs.myName.ifBlank { "Toi" },
            partnerName = prefs.partnerDisplayName,
            pins = pins.filter { it.sessionId == id }.map { it.item }.toSet(),
            spicy = all.values.any { it.category.spicy },
            lockSpicy = prefs.gamesLockSpicy,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Réponse sur moi-même et/ou pronostic sur l'autre, pour la question [index]. */
    fun answer(index: Int, self: GameValue?, guess: GameValue?) = viewModelScope.launch {
        games.answer(id, questionKey(index), GameAnswer(self = self, guess = guess))
    }

    /** J'ai fini ma part : l'autre reçoit « C'est ton tour » (ou « Révélation prête »). */
    fun finish() = viewModelScope.launch { games.answer(id, DONE_ITEM, GameAnswer(ok = true), notify = true) }

    /** Devine-moi, réponse libre : la personne concernée juge le pronostic. */
    fun judge(index: Int, ok: Boolean) = viewModelScope.launch { games.answer(id, judgeKey(index), GameAnswer(ok = ok), notify = true) }

    /** Question surprise : la réponse de la personne qui la reçoit. */
    fun answerSurprise(text: String) = viewModelScope.launch {
        games.answer(id, questionKey(0), GameAnswer(self = GameValue(text = text.trim())), notify = true)
    }

    /** Action ou vérité : tire une entrée du type choisi (actions faisables à distance seulement). */
    suspend fun drawTurn(pick: String): GameItem? {
        val kind = if (pick == "action") ItemKind.ACTION else ItemKind.VERITE
        val item = games.draw(games.filter(setOf(kind), remoteOnly = true), 1).firstOrNull()
        if (item == null) messages.send("Plus rien dans cette catégorie avec vos réglages actuels.")
        item?.let { games.markSeen(listOf(it.id)) }
        return item
    }

    fun playTurn(turn: Int, pick: String, item: GameItem, text: String?, ok: Boolean?) = viewModelScope.launch {
        games.answer(id, turnKey(turn), GameAnswer(pick = pick, item = item.id, self = text?.let { GameValue(text = it.trim()) }, ok = ok), notify = true)
    }

    fun stop() = viewModelScope.launch { games.setStatus(id, "done") }

    fun togglePin(item: String, question: String, answer: String, author: String) = viewModelScope.launch {
        if (item in (state.value?.pins ?: emptySet())) games.unpin(id, item)
        else {
            games.pin(GamePinEntity(id, item, question, answer, author, System.currentTimeMillis()))
            messages.send("Épinglée dans « Nos révélations » 📌")
        }
    }

    fun refresh() = viewModelScope.launch { runCatching { games.flushPending(); games.sync() } }
}
