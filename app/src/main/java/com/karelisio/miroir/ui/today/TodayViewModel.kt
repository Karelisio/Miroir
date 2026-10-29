package com.karelisio.miroir.ui.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.karelisio.miroir.AppContainer
import com.karelisio.miroir.data.MiroirRepository
import com.karelisio.miroir.data.SkipResult
import com.karelisio.miroir.data.local.DayEntity
import com.karelisio.miroir.data.model.AnswerPayload
import com.karelisio.miroir.data.model.Member
import com.karelisio.miroir.data.model.Question
import com.karelisio.miroir.data.model.ReactionPayload
import com.karelisio.miroir.data.remote.ApiException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import java.io.IOException
import java.time.LocalDate
import java.time.YearMonth

enum class Phase { ANSWER, WAITING, READY_TO_REVEAL, REVEALED }

data class TodayUi(
    val loading: Boolean = true,
    val paired: Boolean = false,
    val partnerJoined: Boolean = false,
    val day: LocalDate = LocalDate.now(),
    val entity: DayEntity? = null,
    val question: Question? = null,
    val questionText: String = "",
    val phase: Phase = Phase.ANSWER,
    val myAnswer: AnswerPayload? = null,
    val partnerAnswer: AnswerPayload? = null,
    val partnerAnswered: Boolean = false,
    val mySynced: Boolean = true,
    val myReaction: ReactionPayload? = null,
    /** Ma réaction (emoji + petit mot) est arrivée sur le serveur. */
    val myReactionSynced: Boolean = true,
    val partnerReaction: ReactionPayload? = null,
    val canSkip: Boolean = false,
    val streak: Int = 0,
    val bestStreak: Int = 0,
    val me: Member = Member.A,
    val myName: String = "",
    val partnerName: String = "",
    val skippedPermissions: Set<String> = emptySet(),
    val permissionReminderDismissedAt: Long = 0,
    /** Question d'hier sans ma réponse : rattrapable jusqu'à ce soir minuit. */
    val catchUp: DayEntity? = null,
    val nudgeSent: Boolean = false,
    /** Date de votre anniversaire de couple (compteur « ensemble depuis »). */
    val together: LocalDate? = null,
    /** Mois précédent à récapituler (première semaine du mois, tant qu'il n'a pas été vu). */
    val recapMonth: YearMonth? = null,
)

sealed interface TodayMessage {
    data class Text(val text: String) : TodayMessage
}

private val json = Json { ignoreUnknownKeys = true }

fun DayEntity.myAnswerPayload(): AnswerPayload? = myAnswer?.let { runCatching { json.decodeFromString(AnswerPayload.serializer(), it) }.getOrNull() }
fun DayEntity.partnerAnswerPayload(): AnswerPayload? = partnerAnswer?.let { runCatching { json.decodeFromString(AnswerPayload.serializer(), it) }.getOrNull() }
fun DayEntity.myReactionPayload(): ReactionPayload? = myReaction?.let { runCatching { json.decodeFromString(ReactionPayload.serializer(), it) }.getOrNull() }
fun DayEntity.partnerReactionPayload(): ReactionPayload? = partnerReaction?.let { runCatching { json.decodeFromString(ReactionPayload.serializer(), it) }.getOrNull() }

/** Série de jours consécutifs où les deux ont répondu (aujourd'hui compte s'il est complet). */
fun streakOf(days: List<DayEntity>, today: LocalDate): Int {
    val both = days.filter { it.myAnswer != null && it.partnerAnswered }.mapNotNull { runCatching { LocalDate.parse(it.day) }.getOrNull() }.toSet()
    var d = if (today in both) today else today.minusDays(1)
    var n = 0
    while (d in both) { n++; d = d.minusDays(1) }
    return n
}

fun bestStreakOf(days: List<DayEntity>): Int {
    val both = days.filter { it.myAnswer != null && it.partnerAnswered }.mapNotNull { runCatching { LocalDate.parse(it.day) }.getOrNull() }.sorted()
    var best = 0
    var run = 0
    var prev: LocalDate? = null
    for (d in both) {
        run = if (prev != null && prev.plusDays(1) == d) run + 1 else 1
        best = maxOf(best, run)
        prev = d
    }
    return best
}

@OptIn(ExperimentalCoroutinesApi::class)
class TodayViewModel(private val c: AppContainer) : ViewModel() {
    private val repo = c.repository
    private val messages = Channel<TodayMessage>(Channel.BUFFERED)
    val events = messages.receiveAsFlow()
    val refreshing = MutableStateFlow(false)
    val skipping = MutableStateFlow(false)

    private val dayWithEntity = repo.todayFlow.flatMapLatest { d -> repo.dao.observeDay(d.toString()).map { d to it } }

    private val nudgeSent = MutableStateFlow(false)

    val state: StateFlow<TodayUi> = combine(dayWithEntity, repo.dao.observeDays(), c.prefs.data, nudgeSent) { (day, entity), days, prefs, nudged ->
        val question = entity?.let { repo.resolveQuestion(it.questionId) }
        val mine = entity?.myAnswerPayload()
        val theirs = entity?.partnerAnswerPayload()
        val phase = when {
            mine == null -> Phase.ANSWER
            theirs == null -> Phase.WAITING
            entity.revealSeen -> Phase.REVEALED
            else -> Phase.READY_TO_REVEAL
        }
        TodayUi(
            loading = entity == null && prefs.paired && prefs.partnerJoined,
            paired = prefs.paired,
            partnerJoined = prefs.partnerJoined,
            day = day,
            entity = entity,
            question = question,
            questionText = entity?.questionText.orEmpty(),
            phase = phase,
            myAnswer = mine,
            partnerAnswer = theirs,
            partnerAnswered = entity?.partnerAnswered == true,
            mySynced = entity?.mySynced ?: true,
            myReaction = entity?.myReactionPayload(),
            myReactionSynced = entity?.myReactionSynced ?: true,
            partnerReaction = entity?.partnerReactionPayload(),
            canSkip = entity != null && entity.skips == 0 && mine == null && !entity.partnerAnswered,
            streak = streakOf(days, day),
            bestStreak = bestStreakOf(days),
            me = prefs.member,
            myName = prefs.myName,
            partnerName = prefs.partnerDisplayName,
            skippedPermissions = prefs.skippedPermissions,
            permissionReminderDismissedAt = prefs.permissionReminderDismissedAt,
            catchUp = days.firstOrNull { it.day == day.minusDays(1).toString() && it.myAnswer == null && it.questionText.isNotBlank() },
            nudgeSent = nudged,
            together = prefs.shared.anniversary?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
            recapMonth = YearMonth.from(day).minusMonths(1).takeIf { prev ->
                day.dayOfMonth <= 7 && prefs.lastRecapSeen != prev.toString() &&
                    days.any { it.day.startsWith(prev.toString()) && it.myAnswer != null && it.partnerAnswered }
            },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TodayUi())

    init {
        // À chaque changement de jour (minuit, fuseau), on crée l'entrée du jour et on synchronise.
        viewModelScope.launch {
            repo.todayFlow.collect {
                nudgeSent.value = false
                repo.ensureToday()
                refresh(silent = true)
            }
        }
    }

    fun refresh(silent: Boolean = false) {
        if (refreshing.value) return
        viewModelScope.launch {
            refreshing.value = true
            try {
                repo.flushPending()
                repo.sync()
            } catch (e: IOException) {
                if (!silent) messages.send(TodayMessage.Text(if (e is ApiException) "Le serveur a répondu une erreur." else "Hors ligne : on réessaiera plus tard."))
            } finally {
                refreshing.value = false
            }
        }
    }

    fun submit(answer: AnswerPayload) {
        val day = state.value.entity?.day ?: return
        viewModelScope.launch {
            if (!repo.submitAnswer(day, answer)) messages.send(TodayMessage.Text("La question vient de changer : découvre la nouvelle !"))
        }
    }

    fun nudge() {
        viewModelScope.launch {
            val text = when (repo.nudge()) {
                MiroirRepository.NudgeResult.Sent -> { nudgeSent.value = true; "Coucou envoyé à ${state.value.partnerName} 💜" }
                MiroirRepository.NudgeResult.TooSoon -> { nudgeSent.value = true; "Tu as déjà envoyé un coucou il y a peu." }
                MiroirRepository.NudgeResult.Offline -> "Connexion nécessaire pour envoyer un coucou."
            }
            messages.send(TodayMessage.Text(text))
        }
    }

    fun checkUpdate() {
        viewModelScope.launch { c.updates.check(manual = true) }
    }

    fun skip() {
        if (skipping.value) return
        viewModelScope.launch {
            skipping.value = true
            val text = when (repo.skipToday()) {
                SkipResult.Done -> null
                SkipResult.AlreadyUsed -> "Le joker du jour a déjà été utilisé."
                SkipResult.AlreadyAnswered -> "Quelqu'un a déjà répondu : impossible de passer."
                SkipResult.Offline -> "Connexion nécessaire pour passer la question."
                SkipResult.Retry -> "Le serveur n'a pas encore cette journée : réessaie dans un instant."
            }
            skipping.value = false
            text?.let { messages.send(TodayMessage.Text(it)) }
        }
    }

    fun react(emoji: String?, comment: String?) {
        val day = state.value.entity?.day ?: return
        viewModelScope.launch { repo.react(day, emoji, comment) }
    }

    fun markRevealSeen() {
        val day = state.value.entity?.day ?: return
        viewModelScope.launch { repo.markRevealSeen(day) }
    }

    fun markRecapSeen(month: String) {
        viewModelScope.launch { c.prefs.update { lastRecapSeen(month) } }
    }

    fun dismissPermissionReminder() {
        viewModelScope.launch { c.prefs.update { permissionReminderDismissed(System.currentTimeMillis()) } }
    }
}
