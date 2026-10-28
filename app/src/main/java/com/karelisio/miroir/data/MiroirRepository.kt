package com.karelisio.miroir.data

import android.content.Context
import android.net.Uri
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging
import com.karelisio.miroir.BuildConfig
import com.karelisio.miroir.data.crypto.PairCrypto
import com.karelisio.miroir.data.draw.QuestionDraw
import com.karelisio.miroir.data.local.CustomQuestionEntity
import com.karelisio.miroir.data.local.DayEntity
import com.karelisio.miroir.data.local.MiroirDao
import com.karelisio.miroir.data.model.AnswerPayload
import com.karelisio.miroir.data.model.Category
import com.karelisio.miroir.data.model.CustomQuestionPayload
import com.karelisio.miroir.data.model.Member
import com.karelisio.miroir.data.model.Pack
import com.karelisio.miroir.data.model.ProfilePayload
import com.karelisio.miroir.data.model.Question
import com.karelisio.miroir.data.model.ReactionPayload
import com.karelisio.miroir.data.model.SharedSettings
import com.karelisio.miroir.data.prefs.AppPrefs
import com.karelisio.miroir.data.prefs.PairCredentials
import com.karelisio.miroir.data.prefs.Prefs
import com.karelisio.miroir.data.questions.QuestionBank
import com.karelisio.miroir.data.remote.AnswerRequest
import com.karelisio.miroir.data.remote.ApiException
import com.karelisio.miroir.data.remote.CustomQuestionRequest
import com.karelisio.miroir.data.remote.DayDto
import com.karelisio.miroir.data.remote.MiroirApi
import com.karelisio.miroir.data.remote.PairRequest
import com.karelisio.miroir.data.remote.ProfileRequest
import com.karelisio.miroir.data.remote.ReactRequest
import com.karelisio.miroir.data.remote.SettingsRequest
import com.karelisio.miroir.data.remote.SkipRequest
import com.karelisio.miroir.data.remote.StateRequest
import com.karelisio.miroir.data.remote.StateResponse
import com.karelisio.miroir.data.remote.TodayProposal
import com.karelisio.miroir.data.remote.TokenRequest
import com.karelisio.miroir.notifications.Notifier
import com.karelisio.miroir.sync.SyncScheduler
import com.karelisio.miroir.widget.MiroirWidget
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import kotlinx.serialization.json.Json
import java.io.IOException
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.MonthDay
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/** Événements détectés lors d'une synchronisation, transformés en notifications si besoin. */
sealed interface SyncEvent {
    data object Paired : SyncEvent
    data class PartnerAnswered(val day: String, val iAnswered: Boolean) : SyncEvent
    data class Revealed(val day: String) : SyncEvent
    data class Reaction(val day: String, val emoji: String?, val comment: String?) : SyncEvent
    data object CustomQuestionAdded : SyncEvent
    data class QuestionChanged(val day: String) : SyncEvent
}

sealed interface SkipResult {
    data object Done : SkipResult
    data object AlreadyUsed : SkipResult
    data object AlreadyAnswered : SkipResult
    data object Offline : SkipResult
    data object Retry : SkipResult
}

class MiroirRepository(
    private val context: Context,
    val dao: MiroirDao,
    val prefs: Prefs,
    private val api: MiroirApi,
    val bank: QuestionBank,
) {
    val json = Json { ignoreUnknownKeys = true; encodeDefaults = false; explicitNulls = false }
    private val syncMutex = Mutex()
    private val writeMutex = Mutex()
    @Volatile private var cryptoCache: Pair<String, PairCrypto>? = null

    // ---------------------------------------------------------------- jour et fuseau

    fun zone(p: AppPrefs): ZoneId =
        p.shared.timezone?.let { runCatching { ZoneId.of(it) }.getOrNull() } ?: ZoneId.systemDefault()

    suspend fun today(): LocalDate = LocalDate.now(zone(prefs.current()))

    /** Émet la date du jour (fuseau du couple) et change automatiquement à minuit. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val todayFlow: Flow<LocalDate> = prefs.data.map { zone(it) }.distinctUntilChanged().flatMapLatest { z ->
        flow {
            while (true) {
                emit(LocalDate.now(z))
                val now = LocalDateTime.now(z)
                val millisToMidnight = ChronoUnit.MILLIS.between(now, now.toLocalDate().plusDays(1).atStartOfDay())
                delay(millisToMidnight.coerceIn(1_000, 60_000))
            }
        }
    }.distinctUntilChanged()

    private suspend fun crypto(c: PairCredentials): PairCrypto {
        cryptoCache?.let { (id, crypto) -> if (id == c.pairId + c.key) return crypto }
        return PairCrypto(PairCrypto.unb64(c.key), c.pairId).also { cryptoCache = (c.pairId + c.key) to it }
    }

    // ---------------------------------------------------------------- appairage

    /** Contenu du QR : la clé AES n'est transmise que de téléphone à téléphone. */
    data class Invite(val uri: String)

    suspend fun createPair(serverUrl: String): Invite {
        val creds = PairCredentials(
            pairId = PairCrypto.b64(PairCrypto.randomBytes(16)),
            secret = PairCrypto.b64(PairCrypto.randomBytes(32)),
            key = PairCrypto.b64(PairCrypto.randomBytes(32)),
            member = Member.A.key,
            serverUrl = serverUrl.trim().trimEnd('/'),
        )
        val p = prefs.current()
        val crypto = crypto(creds)
        api.pair(creds, PairRequest("create", profilePayload(crypto, Member.A, p), fcmTokenOrNull()))
        resetLocal()
        prefs.saveCredentials(creds)
        prefs.update {
            shared(SharedSettings(timezone = ZoneId.systemDefault().id), 0)
            sharedDirty = true
            partnerJoined = false
            fcmTokenSent = true
        }
        // Pousse tout de suite les réglages partagés (fuseau du couple).
        SyncScheduler.enqueueSend(context)
        return inviteFor(creds)
    }

    suspend fun currentInvite(): Invite? = prefs.credentials()?.takeIf { it.memberEnum == Member.A }?.let { inviteFor(it) }

    private fun inviteFor(c: PairCredentials): Invite {
        val uri = Uri.Builder().scheme("miroir").authority("pair")
            .appendQueryParameter("v", "1")
            .appendQueryParameter("id", c.pairId)
            .appendQueryParameter("s", c.secret)
            .appendQueryParameter("k", c.key)
            .appendQueryParameter("u", c.serverUrl)
            .build().toString()
        return Invite(uri)
    }

    fun parseInvite(raw: String): PairCredentials? = runCatching {
        val uri = Uri.parse(raw.trim())
        if (uri.scheme != "miroir" || uri.authority != "pair" || uri.getQueryParameter("v") != "1") return null
        val id = uri.getQueryParameter("id")!!
        val s = uri.getQueryParameter("s")!!
        val k = uri.getQueryParameter("k")!!
        require(PairCrypto.unb64(k).size == 32 && s.length >= 32 && id.length >= 16)
        PairCredentials(id, s, k, Member.B.key, uri.getQueryParameter("u").orEmpty().ifBlank { BuildConfig.WORKER_URL })
    }.getOrNull()

    suspend fun joinPair(creds: PairCredentials) {
        val p = prefs.current()
        api.pair(creds, PairRequest("join", profilePayload(crypto(creds), Member.B, p), fcmTokenOrNull()))
        resetLocal()
        prefs.saveCredentials(creds)
        prefs.update { partnerJoined = true; fcmTokenSent = true; profileDirty = false }
        runCatching { sync() }
    }

    /** Renvoie false hors ligne : la paire doit être supprimée côté serveur, sinon l'autre répondrait dans le vide. */
    suspend fun unpair(): Boolean {
        val c = prefs.credentials()
        if (c != null) {
            try {
                api.unpair(c)
            } catch (e: ApiException) {
                if (e.status != 401) return false // 401 : déjà supprimée sur le serveur
            } catch (e: IOException) {
                return false
            }
        }
        forgetPairing()
        return true
    }

    suspend fun currentPairId(): String? = prefs.credentials()?.pairId

    suspend fun forgetPairing() {
        resetLocal()
        prefs.clearPairing()
        cryptoCache = null
        MiroirWidget.refresh(context)
    }

    private suspend fun resetLocal() {
        dao.clearDays()
        dao.clearCustom()
    }

    private fun profilePayload(crypto: PairCrypto, member: Member, p: AppPrefs): String =
        crypto.encrypt(json.encodeToString(ProfilePayload.serializer(), ProfilePayload(p.myName, p.myBirthday)), "profile:${member.key}")

    private suspend fun fcmTokenOrNull(): String? {
        if (!BuildConfig.FIREBASE_ENABLED || FirebaseApp.getApps(context).isEmpty()) return null
        val messaging = FirebaseMessaging.getInstance()
        runCatching { messaging.register() }
        @Suppress("DEPRECATION")
        return runCatching { messaging.token.await() }.getOrNull()?.also { t ->
            prefs.update { fcmToken(t) }
        }
    }

    // ---------------------------------------------------------------- questions

    suspend fun customQuestions(): List<Pair<Question, CustomQuestionEntity>> =
        dao.custom().filter { !it.deleted }.mapNotNull { e -> customQuestion(e)?.let { it to e } }

    fun customQuestion(e: CustomQuestionEntity): Question? = runCatching {
        val p = json.decodeFromString(CustomQuestionPayload.serializer(), e.payload)
        Question(e.id, Category.PERSO.key, p.type, p.text, p.options, p.minLabel, p.maxLabel, listOf("perso"))
    }.getOrNull()

    suspend fun resolveQuestion(id: String): Question? =
        bank.byId(id) ?: dao.customById(id)?.let { customQuestion(it) }

    /** Nom de la personne dont c'est l'anniversaire ce jour-là (pour le marqueur {prenom}). */
    private fun birthdayName(day: LocalDate, p: AppPrefs): String? {
        val md = MonthDay.from(day)
        fun matches(b: String?) = b?.let { runCatching { MonthDay.from(LocalDate.parse(it)) == md }.getOrNull() } == true
        return when {
            matches(p.myBirthday) -> p.myName
            matches(p.partnerBirthday) -> p.partnerName
            else -> null
        }
    }

    suspend fun renderText(q: Question, day: LocalDate): String =
        if (q.pack == Pack.ANNIVERSAIRE.key) QuestionBank.render(q.text, birthdayName(day, prefs.current())) else q.text

    private suspend fun drawFor(c: PairCredentials, day: LocalDate, skipIndex: Int, exclude: Set<String>): Question {
        val p = prefs.current()
        val past = dao.days().filter { it.day < day.toString() }
        val recentLimit = day.minusDays(60).toString()
        val used = past.flatMap { listOfNotNull(it.questionId, it.skippedQuestionId) }.toSet()
        val recent = past.filter { it.day >= recentLimit }.flatMap { listOfNotNull(it.questionId, it.skippedQuestionId) }.toSet()
        val birthdays = listOfNotNull(p.myBirthday, p.partnerBirthday).mapNotNull {
            runCatching { MonthDay.from(LocalDate.parse(it)) }.getOrNull()
        }
        return QuestionDraw.draw(
            QuestionDraw.Input(
                pairId = c.pairId,
                day = day,
                skipIndex = skipIndex,
                bank = bank.all(),
                custom = customQuestions().map { (q, e) -> QuestionDraw.CustomCandidate(q, e.createdDay) },
                disabledCategories = p.shared.disabledCategories,
                packsEnabled = p.shared.packsEnabled,
                anniversary = p.shared.anniversary?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
                birthdays = birthdays,
                used = used,
                recent = recent,
                exclude = exclude,
            ),
        )
    }

    /** Garantit une entrée locale pour aujourd'hui (tirage hors ligne si le serveur est injoignable). */
    suspend fun ensureToday(): DayEntity? = writeMutex.withLock {
        val c = prefs.credentials() ?: return null
        val day = today()
        dao.day(day.toString())?.let { return it }
        val q = drawFor(c, day, 0, emptySet())
        val entity = DayEntity(
            day = day.toString(),
            questionId = q.id,
            tag = crypto(c).tag(q.id),
            questionText = renderText(q, day),
            category = q.category,
            pack = q.pack,
        )
        dao.upsertDay(entity)
        entity
    }

    // ---------------------------------------------------------------- actions

    /** Renvoie false si la question a changé entre-temps (l'autre l'a passée). */
    suspend fun submitAnswer(day: String, answer: AnswerPayload): Boolean {
        writeMutex.withLock {
            val e = dao.day(day) ?: return false
            if (e.questionId != answer.questionId) return false
            if (e.myAnswer != null) return true
            dao.upsertDay(e.copy(myAnswer = json.encodeToString(AnswerPayload.serializer(), answer), myAnsweredAt = answer.at, mySynced = false))
        }
        SyncScheduler.enqueueSend(context)
        MiroirWidget.refresh(context)
        return true
    }

    suspend fun react(day: String, emoji: String?, comment: String?) {
        writeMutex.withLock {
            val e = dao.day(day) ?: return
            val payload = ReactionPayload(emoji, comment?.trim()?.takeIf { it.isNotEmpty() }, System.currentTimeMillis())
            dao.upsertDay(e.copy(myReaction = json.encodeToString(ReactionPayload.serializer(), payload), myReactionSynced = false))
        }
        SyncScheduler.enqueueSend(context)
    }

    suspend fun markRevealSeen(day: String) = writeMutex.withLock {
        dao.day(day)?.let { if (!it.revealSeen) dao.upsertDay(it.copy(revealSeen = true)) }
    }

    /** "Passer" : une seule fois par jour, et seulement si personne n'a encore répondu. */
    suspend fun skipToday(): SkipResult {
        val c = prefs.credentials() ?: return SkipResult.Offline
        val crypto = crypto(c)
        val day = today()
        var current = ensureToday() ?: return SkipResult.Offline
        if (!current.locked) {
            // Jour pas encore verrouillé : une synchro récupère d'abord l'état serveur.
            runCatching { sync() }
            current = dao.day(day.toString()) ?: current
        }
        if (current.myAnswer != null || current.partnerAnswered) return SkipResult.AlreadyAnswered
        if (current.skips >= 1) return SkipResult.AlreadyUsed
        val next = drawFor(c, day, current.skips + 1, setOf(current.questionId))
        return try {
            val res = api.skip(
                c,
                SkipRequest(
                    day = day.toString(),
                    expectedSkips = current.skips,
                    tag = crypto.tag(next.id),
                    ref = crypto.encrypt(next.id, "ref:$day"),
                    currentTag = current.tag,
                    currentRef = crypto.encrypt(current.questionId, "ref:$day"),
                ),
            )
            // Pas de mise à jour de la révision locale ici : seule sync() l'avance.
            res.day?.let { mergeDays(c, listOf(it)) }
            MiroirWidget.refresh(context)
            SkipResult.Done
        } catch (e: ApiException) {
            e.error.day?.let { mergeDays(c, listOf(it)) }
            when (e.error.error) {
                "already_answered" -> SkipResult.AlreadyAnswered
                "skip_used" -> SkipResult.AlreadyUsed
                else -> SkipResult.Retry
            }
        } catch (e: IOException) {
            SkipResult.Offline
        }
    }

    suspend fun addCustomQuestion(payload: CustomQuestionPayload): String {
        val c = prefs.credentials() ?: error("non appairé")
        val id = "perso_" + PairCrypto.b64(PairCrypto.randomBytes(12))
        dao.upsertCustom(
            CustomQuestionEntity(
                id = id,
                author = c.member,
                payload = json.encodeToString(CustomQuestionPayload.serializer(), payload),
                createdDay = payload.createdDay,
                createdAt = System.currentTimeMillis(),
                synced = false,
            ),
        )
        SyncScheduler.enqueueSend(context)
        return id
    }

    suspend fun deleteCustomQuestion(id: String) {
        val e = dao.customById(id) ?: return
        dao.upsertCustom(e.copy(deleted = true, synced = false))
        SyncScheduler.enqueueSend(context)
    }

    suspend fun updateProfile(name: String, birthday: String?) {
        prefs.update { myProfile(name.trim(), birthday); profileDirty = true }
        if (prefs.current().paired) SyncScheduler.enqueueSend(context)
    }

    suspend fun updateShared(transform: (SharedSettings) -> SharedSettings) {
        prefs.update { shared(transform(shared), sharedVersion); sharedDirty = true }
        SyncScheduler.enqueueSend(context)
    }

    // ---------------------------------------------------------------- synchronisation

    /** Envoie tout ce qui attend en file. Renvoie false s'il faut réessayer plus tard (réseau). */
    suspend fun flushPending(): Boolean {
        val c = prefs.credentials() ?: return true
        val crypto = crypto(c)
        val me = c.memberEnum
        try {
            prefs.fcmToken().let { (token, sent) ->
                if (token != null && !sent) {
                    api.token(c, TokenRequest(token))
                    prefs.update { fcmTokenSent = true }
                }
            }
            var p = prefs.current()
            if (p.profileDirty) {
                api.profile(c, ProfileRequest(profilePayload(crypto, me, p)))
                val sent = p
                // Modifié pendant l'envoi : on laisse le drapeau pour un nouvel envoi.
                prefs.update { if (myName == sent.myName && myBirthday == sent.myBirthday) profileDirty = false }
            }
            p = prefs.current()
            if (p.sharedDirty) pushShared(c, crypto, p)

            for (q in dao.pendingCustom()) {
                api.customQuestion(c, CustomQuestionRequest(q.id, crypto.encrypt(q.payload, "custom:${q.id}"), q.deleted))
                writeMutex.withLock {
                    dao.customById(q.id)?.let { now -> if (now.payload == q.payload && now.deleted == q.deleted) dao.upsertCustom(now.copy(synced = true)) }
                }
            }
            for (e in dao.pendingAnswers()) {
                val answer = e.myAnswer ?: continue
                try {
                    val res = api.answer(
                        c,
                        AnswerRequest(e.day, e.tag, crypto.encrypt(e.questionId, "ref:${e.day}"), crypto.encrypt(answer, "answer:${e.day}:${me.key}")),
                    )
                    res.day?.let { postEvents(mergeDays(c, listOf(it))) }
                } catch (ex: ApiException) {
                    when (ex.error.error) {
                        "question_changed" -> ex.error.day?.let { postEvents(mergeDays(c, listOf(it))) }
                        // Réponse trop ancienne pour le serveur : on arrête de réessayer.
                        else -> if (ex.status in 400..499) writeMutex.withLock { dao.day(e.day)?.let { dao.upsertDay(it.copy(mySynced = true)) } } else throw ex
                    }
                }
            }
            for (e in dao.pendingReactions()) {
                val reaction = e.myReaction ?: continue
                try {
                    api.react(c, ReactRequest(e.day, crypto.encrypt(reaction, "reaction:${e.day}:${me.key}")))
                } catch (ex: ApiException) {
                    if (ex.status !in 400..499) throw ex
                }
                // Une nouvelle réaction tapée pendant l'envoi reste en attente.
                writeMutex.withLock { dao.day(e.day)?.let { if (it.myReaction == reaction) dao.upsertDay(it.copy(myReactionSynced = true)) } }
            }
            return true
        } catch (e: ApiException) {
            if (e.status == 401) {
                forgetPairing()
                return true
            }
            return e.status !in 500..599
        } catch (e: IOException) {
            return false
        }
    }

    private suspend fun pushShared(c: PairCredentials, crypto: PairCrypto, p: AppPrefs) {
        var base = p.sharedVersion
        repeat(3) {
            val payload = crypto.encrypt(json.encodeToString(SharedSettings.serializer(), p.shared), "settings")
            try {
                val res = api.settings(c, SettingsRequest(payload, base))
                prefs.update {
                    val changed = shared != p.shared
                    shared(shared, res.settings.version)
                    sharedDirty = changed
                }
                return
            } catch (e: ApiException) {
                // Conflit : la dernière écriture l'emporte, on rejoue sur la version serveur.
                if (e.error.error != "settings_conflict") throw e
                base = e.error.settings?.version ?: return
            }
        }
    }

    /** Synchronisation incrémentale (révision du couple) + verrouillage de la question du jour. */
    suspend fun sync(): List<SyncEvent> = syncMutex.withLock {
        val c = prefs.credentials() ?: return emptyList()
        val crypto = crypto(c)
        val today = ensureToday()
        val proposal = today?.let { TodayProposal(it.day, it.tag, crypto.encrypt(it.questionId, "ref:${it.day}")) }
        val before = prefs.current()
        val res = try {
            api.state(c, StateRequest(since = before.rev, today = proposal))
        } catch (e: ApiException) {
            if (e.status == 401) forgetPairing()
            throw e
        }
        val events = mergeState(c, crypto, res, before)
        prefs.update { rev(res.rev) }
        MiroirWidget.refresh(context)
        postEvents(events)
        events
    }

    private suspend fun mergeState(c: PairCredentials, crypto: PairCrypto, res: StateResponse, before: AppPrefs): List<SyncEvent> {
        val me = c.memberEnum
        val events = mutableListOf<SyncEvent>()

        val partner = res.members.firstOrNull { it.member == me.other.key }
        if (partner != null) {
            val profile = crypto.decryptOrNull(partner.profile, "profile:${partner.member}")
                ?.let { runCatching { json.decodeFromString(ProfilePayload.serializer(), it) }.getOrNull() }
            prefs.update {
                partnerJoined = true
                if (profile != null) partnerProfile(profile.name, profile.birthday)
            }
            if (!before.partnerJoined) events += SyncEvent.Paired
        }
        // Réinstallation : on retrouve son propre profil.
        if (before.myName.isBlank()) {
            res.members.firstOrNull { it.member == me.key }
                ?.let { crypto.decryptOrNull(it.profile, "profile:${it.member}") }
                ?.let { runCatching { json.decodeFromString(ProfilePayload.serializer(), it) }.getOrNull() }
                ?.let { prof -> prefs.update { myProfile(prof.name, prof.birthday) } }
        }

        val s = res.settings
        if (s.payload != null && s.version != before.sharedVersion && !prefs.current().sharedDirty) {
            crypto.decryptOrNull(s.payload, "settings")
                ?.let { runCatching { json.decodeFromString(SharedSettings.serializer(), it) }.getOrNull() }
                ?.let { shared -> prefs.update { shared(shared, s.version) } }
        }

        for (q in res.customQuestions) {
            val local = dao.customById(q.id)
            if (local != null && !local.synced) continue
            val payload = crypto.decryptOrNull(q.payload, "custom:${q.id}") ?: continue
            val createdDay = runCatching { json.decodeFromString(CustomQuestionPayload.serializer(), payload).createdDay }.getOrNull() ?: continue
            dao.upsertCustom(CustomQuestionEntity(q.id, q.author, payload, createdDay, q.createdAt, q.deleted, synced = true))
            if (local == null && !q.deleted && q.author != me.key) events += SyncEvent.CustomQuestionAdded
        }

        events += mergeDays(c, res.days)
        return events
    }

    private suspend fun mergeDays(c: PairCredentials, days: List<DayDto>): List<SyncEvent> = writeMutex.withLock {
        val crypto = crypto(c)
        val me = c.memberEnum
        val today = today().toString()
        val events = mutableListOf<SyncEvent>()
        for (d in days) {
            val existing = dao.day(d.day)
            // Réponse /state plus ancienne qu'un état déjà fusionné (skip ou réponse concurrents) : ignorée.
            if (existing != null && existing.locked && d.rev < existing.serverRev) continue
            val questionId = crypto.decryptOrNull(d.ref, "ref:${d.day}") ?: continue
            val question = resolveQuestion(questionId)
            val sameQuestion = existing?.tag == d.tag

            val mine = d.answers[me.key]
            val theirs = d.answers[me.other.key]
            val serverMine = crypto.decryptOrNull(mine?.payload, "answer:${d.day}:${me.key}")
            val localPending = existing?.myAnswer?.takeIf { sameQuestion && !existing.mySynced }
            if (existing?.myAnswer != null && !existing.mySynced && !sameQuestion) events += SyncEvent.QuestionChanged(d.day)
            val myAnswer = serverMine ?: localPending ?: existing?.myAnswer?.takeIf { sameQuestion }
            val partnerAnswer = crypto.decryptOrNull(theirs?.payload, "answer:${d.day}:${me.other.key}")

            val serverMyReaction = d.reactions[me.key]?.let { crypto.decryptOrNull(it.payload, "reaction:${d.day}:${me.key}") }
            val partnerReaction = d.reactions[me.other.key]?.let { crypto.decryptOrNull(it.payload, "reaction:${d.day}:${me.other.key}") }
            val keepLocalReaction = existing?.myReactionSynced == false

            val day = LocalDate.parse(d.day)
            val updated = DayEntity(
                day = d.day,
                questionId = questionId,
                tag = d.tag,
                // Question inconnue (banque plus récente chez l'autre) : texte vide, l'écran propose une mise à jour.
                questionText = question?.let { renderText(it, day) } ?: existing?.questionText?.takeIf { sameQuestion }.orEmpty(),
                category = question?.category ?: existing?.category?.takeIf { sameQuestion } ?: Category.PERSO.key,
                pack = question?.pack ?: existing?.pack?.takeIf { sameQuestion },
                skips = d.skips,
                skippedQuestionId = crypto.decryptOrNull(d.skippedRef, "ref:${d.day}"),
                locked = true,
                myAnswer = myAnswer,
                myAnsweredAt = if (myAnswer != null) (mine?.at ?: existing?.myAnsweredAt) else null,
                mySynced = mine != null || (myAnswer == null),
                partnerAnswered = theirs != null,
                partnerAnswer = partnerAnswer,
                partnerAnsweredAt = theirs?.at,
                myReaction = if (keepLocalReaction) existing.myReaction else serverMyReaction ?: existing?.myReaction,
                myReactionSynced = !keepLocalReaction,
                partnerReaction = partnerReaction,
                revealSeen = (existing?.revealSeen ?: false) && sameQuestion,
                serverRev = d.rev,
            )
            dao.upsertDay(updated)

            if (theirs != null && existing?.partnerAnswered != true && d.day == today) events += SyncEvent.PartnerAnswered(d.day, myAnswer != null)
            if (partnerAnswer != null && existing != null && existing.partnerAnswer == null && existing.myAnswer != null) {
                events += SyncEvent.Revealed(d.day)
            }
            if (partnerReaction != null && partnerReaction != existing?.partnerReaction) {
                val r = runCatching { json.decodeFromString(ReactionPayload.serializer(), partnerReaction) }.getOrNull()
                events += SyncEvent.Reaction(d.day, r?.emoji, r?.comment)
            }
        }
        events
    }

    private suspend fun postEvents(events: List<SyncEvent>) {
        if (events.isNotEmpty()) Notifier.onSyncEvents(context, events, prefs.current())
    }
}
