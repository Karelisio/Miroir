package com.karelisio.miroir.games.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.karelisio.miroir.AppContainer
import com.karelisio.miroir.data.prefs.AppPrefs
import com.karelisio.miroir.games.data.GamePinEntity
import com.karelisio.miroir.games.model.CustomGameItem
import com.karelisio.miroir.games.model.GameCategory
import com.karelisio.miroir.games.model.GameItem
import com.karelisio.miroir.games.model.GameKind
import com.karelisio.miroir.games.model.ItemFormat
import com.karelisio.miroir.games.model.ItemKind
import com.karelisio.miroir.games.model.SessionPayload
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.IOException

data class SessionCard(
    val id: String,
    val kind: GameKind,
    val status: SessionStatus,
    val label: String,
    val subtitle: String,
    val updatedAt: Long,
)

data class DefiCard(val sessionId: String, val week: String, val text: String, val intensity: Int, val done: Boolean, val doneBy: String?)

data class GamesUi(
    val loading: Boolean = true,
    val onboardingDone: Boolean = true,
    val partnerJoined: Boolean = true,
    val me: String = "A",
    val myName: String = "",
    val partnerName: String = "",
    val ongoing: List<SessionCard> = emptyList(),
    val history: List<SessionCard> = emptyList(),
    val defi: DefiCard? = null,
    val matches: Int = 0,
    val newMatches: Int = 0,
    val stats: GameStats? = null,
    val intensity: Int = 1,
    val myIntensity: Int = 1,
    val partnerIntensity: Int? = null,
    val excluded: Set<String> = emptySet(),
    val partnerExcluded: Set<String> = emptySet(),
    val lockSpicy: Boolean = false,
    val timer: Int = 0,
    val bankSize: Int = 0,
    val pins: List<GamePinEntity> = emptyList(),
    val custom: List<GameItem> = emptyList(),
) {
    /** Le contenu peut-il contenir de l'Osé ou de l'Intime ? (sert au verrou biométrique) */
    val spicyPossible: Boolean
        get() = GameCategory.entries.any { it.spicy && it.key !in excluded && it.key !in partnerExcluded } && intensity >= 1
}

/** Accueil de « De l'autre côté » : parties en cours, défi de la semaine, stats, réglages. */
class GamesViewModel(private val c: AppContainer) : ViewModel() {
    private val games = c.games
    private val messages = Channel<String>(Channel.BUFFERED)
    val events = messages.receiveAsFlow()
    val refreshing = MutableStateFlow(false)

    private val base = combine(games.dao.observeSessions(), games.dao.observeAnswers(), c.prefs.data) { sessions, answers, prefs ->
        Triple(sessions, answers, prefs)
    }
    private val extras = combine(games.dao.observeMatches(), games.dao.observePins(), games.dao.observeCustom()) { m, p, custom -> Triple(m, p, custom) }

    val state: StateFlow<GamesUi> = combine(base, extras) { (sessions, answers, prefs), (matches, pins, custom) ->
        val me = prefs.member.key
        val byId = answers.groupBy { it.sessionId }
        val views = sessions.map { SessionView(it, byId[it.id].orEmpty(), me) }
        val items = games.items(views.flatMap { it.payload.items }.distinct()).associateBy { it.id }
        val partner = prefs.partnerDisplayName
        val cards = views.filter { it.kind != null && it.kind != GameKind.DEFI && it.kind != GameKind.CANAPE }.map { v ->
            val status = v.status(items)
            SessionCard(v.entity.id, v.kind!!, status, statusLabel(status, partner), subtitle(v, prefs), v.entity.updatedAt)
        }
        val today = c.repository.today()
        val week = com.karelisio.miroir.games.data.GameRepository.isoWeek(today)
        val defi = views.firstOrNull { it.kind == GameKind.DEFI && it.payload.week == week }?.let { v ->
            val item = v.payload.items.firstOrNull()?.let { items[it] }
            val doneBy = listOf("A", "B").firstOrNull { v.answer(it, DEFI_DONE)?.ok == true }
            DefiCard(v.entity.id, week, item?.render(partner) ?: "", item?.intensity ?: 1, doneBy != null, doneBy)
        }
        GamesUi(
            loading = false,
            onboardingDone = prefs.gamesOnboardingDone,
            partnerJoined = prefs.partnerJoined,
            me = me,
            myName = prefs.myName.ifBlank { "Toi" },
            partnerName = partner,
            ongoing = cards.filter { it.status != SessionStatus.DONE }.sortedWith(compareBy<SessionCard> { it.status.ordinal }.thenByDescending { it.updatedAt }),
            history = cards.filter { it.status == SessionStatus.DONE }.sortedByDescending { it.updatedAt },
            defi = defi,
            matches = matches.size,
            newMatches = matches.count { !it.seen },
            stats = GameStatsCalculator.compute(views, items, matches.size, today, c.repository.zone(prefs)),
            intensity = prefs.gamesIntensity,
            myIntensity = prefs.gamesSettings.intensity,
            partnerIntensity = prefs.partnerGamesSettings?.intensity,
            excluded = prefs.gamesSettings.excluded,
            partnerExcluded = prefs.partnerGamesSettings?.excluded ?: emptySet(),
            lockSpicy = prefs.gamesLockSpicy,
            timer = prefs.gamesTimer,
            bankSize = games.bank.all().size,
            pins = pins,
            custom = custom.filter { !it.deleted }.mapNotNull(games::customItem),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GamesUi())

    init {
        viewModelScope.launch {
            games.weeklyDefi()
            refresh(silent = true)
        }
    }

    private fun subtitle(v: SessionView, p: AppPrefs): String = when (v.kind) {
        GameKind.DEVINE -> if (v.answerer == p.member.key) "Tu réponds, ${p.partnerDisplayName} devine · ${v.count} questions"
        else "${p.partnerDisplayName} répond, tu devines · ${v.count} questions"
        GameKind.DUEL -> "${v.count} questions · qui connaît le mieux l'autre ?"
        GameKind.PREFERES -> "${v.count} dilemmes"
        GameKind.ACTION_VERITE -> "Tour ${minOf(v.turnsPlayed + 1, v.totalTurns)} sur ${v.totalTurns}"
        GameKind.SURPRISE -> if (v.entity.createdBy == p.member.key) "Ta question pour ${p.partnerDisplayName}" else "Une question de ${p.partnerDisplayName}"
        else -> ""
    }

    fun refresh(silent: Boolean = false) {
        if (refreshing.value) return
        viewModelScope.launch {
            refreshing.value = true
            try {
                games.flushPending()
                games.sync()
            } catch (e: IOException) {
                if (!silent) messages.send("Hors ligne : tout partira dès le retour du réseau.")
            } finally {
                refreshing.value = false
            }
        }
    }

    /** Lance une partie à distance et prévient l'autre (notification neutre). Renvoie l'id, ou null si rien à jouer. */
    suspend fun challenge(kind: GameKind, count: Int = 5, iAnswer: Boolean = true, question: String? = null): String? {
        val me = games.me().key
        val payload = when (kind) {
            GameKind.DEVINE -> SessionPayload(
                items = games.draw(games.filter(setOf(ItemKind.DEVINE)), count).map { it.id },
                answerer = if (iAnswer) me else if (me == "A") "B" else "A",
            )
            GameKind.DUEL -> SessionPayload(items = games.draw(games.filter(setOf(ItemKind.DEVINE), formats = setOf(ItemFormat.CHOICE, ItemFormat.SCALE)), count).map { it.id })
            GameKind.PREFERES -> SessionPayload(items = games.draw(games.filter(setOf(ItemKind.PREFERES)), count).map { it.id })
            GameKind.ACTION_VERITE -> SessionPayload(turns = count)
            GameKind.SURPRISE -> SessionPayload(question = question?.trim()?.takeIf { it.isNotEmpty() } ?: return null)
            else -> return null
        }
        if (kind != GameKind.ACTION_VERITE && kind != GameKind.SURPRISE && payload.items.isEmpty()) {
            messages.send("Aucune question ne correspond à vos réglages d'intensité et de catégories.")
            return null
        }
        return games.createSession(kind, payload, notify = true)
    }

    fun markDefi(done: Boolean) {
        val defi = state.value.defi ?: return
        viewModelScope.launch { games.answer(defi.sessionId, DEFI_DONE, com.karelisio.miroir.games.model.GameAnswer(ok = done), notify = done) }
    }

    fun setIntensity(level: Int) = viewModelScope.launch { games.updateSettings { it.copy(intensity = level) } }

    fun toggleCategory(key: String) = viewModelScope.launch {
        games.updateSettings { s -> s.copy(excluded = if (key in s.excluded) s.excluded - key else s.excluded + key) }
    }

    fun setLockSpicy(on: Boolean) = viewModelScope.launch { c.prefs.update { gamesLockSpicy = on } }

    fun setTimer(seconds: Int) = viewModelScope.launch { c.prefs.update { gamesTimer(seconds) } }

    fun finishOnboarding(intensity: Int, lock: Boolean) = viewModelScope.launch {
        games.updateSettings { it.copy(intensity = intensity) }
        c.prefs.update {
            gamesLockSpicy = lock
            gamesOnboardingDone = true
        }
    }

    fun addCustom(item: CustomGameItem) = viewModelScope.launch {
        games.addCustom(item)
        messages.send("Ajoutée à vos questions perso ✨")
    }

    fun deleteCustom(id: String) = viewModelScope.launch { games.deleteCustom(id.removePrefix(GameItem.CUSTOM_PREFIX)) }

    fun unpin(p: GamePinEntity) = viewModelScope.launch { games.unpin(p.sessionId, p.item) }

    /** Badges nouvellement débloqués depuis la dernière visite (puis mémorisés). */
    suspend fun takeNewBadges(stats: GameStats): List<Badge> {
        val unlocked = stats.badges.filter { it.unlocked }.map { it.id }.toSet()
        val known = c.prefs.current().gamesBadges
        val fresh = stats.badges.filter { it.unlocked && it.id !in known }
        if (fresh.isNotEmpty()) c.prefs.update { gamesBadges(known + unlocked) }
        return fresh
    }
}
