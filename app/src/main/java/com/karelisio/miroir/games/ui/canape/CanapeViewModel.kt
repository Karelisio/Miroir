package com.karelisio.miroir.games.ui.canape

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.karelisio.miroir.AppContainer
import com.karelisio.miroir.games.model.GameItem
import com.karelisio.miroir.games.model.GameKind
import com.karelisio.miroir.games.model.GameValue
import com.karelisio.miroir.games.model.ItemKind
import com.karelisio.miroir.games.model.SessionPayload
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class CanapeGame(val label: String, val emoji: String, val description: String) {
    DEVINE("Devine-moi", "🔮", "L'un répond sur soi, l'autre devine"),
    PREFERES("Tu préfères", "⚖️", "Chacun choisit en secret, puis on compare"),
    AV("Action ou vérité", "🎲", "À tour de rôle, sans tricher"),
}

/** Étapes d'une partie à un seul téléphone. [player] : 0 = moi, 1 = l'autre. */
sealed interface CanapeStep {
    data object Setup : CanapeStep
    data class Pass(val to: Int, val next: CanapeStep) : CanapeStep
    data class Answer(val round: Int, val player: Int) : CanapeStep
    data class Guess(val round: Int, val player: Int) : CanapeStep
    data class Reveal(val round: Int) : CanapeStep
    data class AvPick(val round: Int, val player: Int) : CanapeStep
    data class AvCard(val round: Int, val player: Int, val pick: String, val item: GameItem) : CanapeStep
    data object End : CanapeStep
}

data class CanapeUi(
    val game: CanapeGame = CanapeGame.DEVINE,
    val rounds: Int = 6,
    val timer: Int = 0,
    val players: List<String> = listOf("Toi", "l'autre"),
    val items: List<GameItem> = emptyList(),
    val step: CanapeStep = CanapeStep.Setup,
    val answers: Map<Int, GameValue> = emptyMap(),
    val guesses: Map<Int, GameValue> = emptyMap(),
    /** Résultat de chaque manche : pronostic juste (Devine-moi) ou accord (Tu préfères). */
    val results: Map<Int, Boolean> = emptyMap(),
    val avDone: List<Int> = listOf(0, 0),
    val playedIds: List<String> = emptyList(),
    val message: String? = null,
    /** Verrou biométrique à demander (contenu Osé ou Intime possible). */
    val lockRequired: Boolean = false,
) {
    fun other(player: Int) = players[1 - player]
    val scores: List<Int>
        get() = when (game) {
            CanapeGame.DEVINE -> listOf(0, 1).map { p -> results.count { (r, ok) -> ok && guesserOf(r) == p } }
            CanapeGame.PREFERES -> listOf(results.count { it.value }, results.size)
            CanapeGame.AV -> avDone
        }
    fun answererOf(round: Int) = round % 2
    fun guesserOf(round: Int) = 1 - answererOf(round)
}

/** Mode canapé : un seul téléphone qu'on se passe, sans réseau. La partie est enregistrée à la fin. */
class CanapeViewModel(private val c: AppContainer) : ViewModel() {
    private val games = c.games
    private val _state = MutableStateFlow(CanapeUi())
    val state = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val p = c.prefs.current()
            val spicy = com.karelisio.miroir.games.model.GameCategory.entries.any { it.spicy && it.key !in p.gamesExcluded }
            _state.update {
                it.copy(players = listOf(p.myName.ifBlank { "Toi" }, p.partnerDisplayName), timer = p.gamesTimer, lockRequired = p.gamesLockSpicy && spicy)
            }
        }
    }

    fun setGame(g: CanapeGame) = _state.update { it.copy(game = g) }
    fun setRounds(n: Int) = _state.update { it.copy(rounds = n) }
    fun setTimer(s: Int) {
        _state.update { it.copy(timer = s) }
        viewModelScope.launch { c.prefs.update { gamesTimer(s) } }
    }

    fun start() = viewModelScope.launch {
        val s = _state.value
        val items = when (s.game) {
            CanapeGame.DEVINE -> games.draw(games.filter(setOf(ItemKind.DEVINE)), s.rounds)
            CanapeGame.PREFERES -> games.draw(games.filter(setOf(ItemKind.PREFERES)), s.rounds)
            CanapeGame.AV -> emptyList()
        }
        if (s.game != CanapeGame.AV && items.isEmpty()) {
            _state.update { it.copy(message = "Rien à jouer avec vos réglages d'intensité et de catégories.") }
            return@launch
        }
        _state.update {
            it.copy(
                items = items, answers = emptyMap(), guesses = emptyMap(), results = emptyMap(), avDone = listOf(0, 0),
                playedIds = items.map { i -> i.id }, message = null,
                rounds = if (s.game == CanapeGame.AV) s.rounds else items.size,
                step = if (s.game == CanapeGame.AV) CanapeStep.AvPick(0, 0) else CanapeStep.Answer(0, 0),
            )
        }
    }

    fun consumeMessage() = _state.update { it.copy(message = null) }

    /** Première réponse de la manche (Devine-moi : sur soi ; Tu préfères : premier choix), puis on passe le téléphone. */
    fun answer(value: GameValue) = _state.update { s ->
        val step = s.step as? CanapeStep.Answer ?: return@update s
        val other = 1 - step.player
        s.copy(answers = s.answers + (step.round to value), step = CanapeStep.Pass(other, CanapeStep.Guess(step.round, other)))
    }

    fun guess(value: GameValue) = _state.update { s ->
        val step = s.step as? CanapeStep.Guess ?: return@update s
        val item = s.items.getOrNull(step.round)
        val auto = s.answers[step.round]?.let { a ->
            if (s.game == CanapeGame.PREFERES) a.choice == value.choice else value.matches(a)
        }
        s.copy(
            guesses = s.guesses + (step.round to value),
            results = if (auto != null && item?.format != com.karelisio.miroir.games.model.ItemFormat.TEXT) s.results + (step.round to auto) else s.results,
            step = CanapeStep.Reveal(step.round),
        )
    }

    /** Réponse libre : on juge ensemble si c'était bien deviné. */
    fun judge(round: Int, ok: Boolean) = _state.update { it.copy(results = it.results + (round to ok)) }

    /** Manche suivante : la personne qui tient le téléphone commence. */
    fun next() = _state.update { s ->
        val step = s.step as? CanapeStep.Reveal ?: return@update s
        val r = step.round + 1
        if (r >= s.items.size) finish(s) else s.copy(step = CanapeStep.Answer(r, s.answererOf(r)))
    }

    fun pick(pick: String) = viewModelScope.launch {
        val s = _state.value
        val step = s.step as? CanapeStep.AvPick ?: return@launch
        val kind = if (pick == "action") ItemKind.ACTION else ItemKind.VERITE
        val item = games.draw(games.filter(setOf(kind)), 1).firstOrNull()
        if (item == null) {
            _state.update { it.copy(message = "Plus rien de ce côté avec vos réglages actuels.") }
            return@launch
        }
        games.markSeen(listOf(item.id))
        _state.update { it.copy(step = CanapeStep.AvCard(step.round, step.player, pick, item), playedIds = it.playedIds + item.id) }
    }

    fun avDone(done: Boolean) = _state.update { s ->
        val step = s.step as? CanapeStep.AvCard ?: return@update s
        val counts = s.avDone.toMutableList().also { if (done) it[step.player]++ }
        val r = step.round + 1
        val updated = s.copy(avDone = counts)
        if (r >= s.rounds) finish(updated) else updated.copy(step = CanapeStep.Pass(1 - step.player, CanapeStep.AvPick(r, 1 - step.player)))
    }

    fun passed() = _state.update { s -> (s.step as? CanapeStep.Pass)?.let { s.copy(step = it.next) } ?: s }

    fun reset() = _state.update { it.copy(step = CanapeStep.Setup) }

    private fun finish(s: CanapeUi): CanapeUi {
        val score = when (s.game) {
            CanapeGame.AV -> s.avDone.sum()
            else -> s.results.count { it.value }
        }
        // Historique partagé (pas de répétition sur l'autre téléphone) et stats.
        viewModelScope.launch {
            games.createSession(
                GameKind.CANAPE,
                SessionPayload(items = s.playedIds, game = s.game.name.lowercase(), rounds = s.rounds, score = score),
                notify = false,
                status = "done",
            )
        }
        return s.copy(step = CanapeStep.End)
    }
}
