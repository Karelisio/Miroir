package com.karelisio.miroir.games.data

import android.content.Context
import com.karelisio.miroir.data.MiroirRepository
import com.karelisio.miroir.data.crypto.PairCrypto
import com.karelisio.miroir.data.model.Member
import com.karelisio.miroir.data.prefs.PairCredentials
import com.karelisio.miroir.data.prefs.Prefs
import com.karelisio.miroir.data.remote.ApiException
import com.karelisio.miroir.data.remote.MiroirApi
import com.karelisio.miroir.diagnostics.Diagnostics
import com.karelisio.miroir.games.model.CustomGameItem
import com.karelisio.miroir.games.model.GameAnswer
import com.karelisio.miroir.games.model.GameCategory
import com.karelisio.miroir.games.model.GameItem
import com.karelisio.miroir.games.model.GameKind
import com.karelisio.miroir.games.model.GameSettings
import com.karelisio.miroir.games.model.ItemFormat
import com.karelisio.miroir.games.model.ItemKind
import com.karelisio.miroir.games.model.MatchKind
import com.karelisio.miroir.games.model.SessionPayload
import com.karelisio.miroir.games.model.Wish
import com.karelisio.miroir.games.model.WishBackup
import com.karelisio.miroir.sync.SyncScheduler
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import java.io.IOException
import java.time.LocalDate
import java.time.temporal.IsoFields

/** Ce qu'une synchro des jeux a apporté de neuf (pour l'interface). */
data class GameSyncResult(val newMatches: Int = 0)

/**
 * Données de « De l'autre côté ». Tout passe par le Worker existant, chiffré avec la clé du couple :
 * le serveur ne voit que des identifiants opaques. Les écritures partent d'abord en base locale
 * (file hors ligne), puis sont envoyées par [flushPending] dès que le réseau le permet.
 */
class GameRepository(
    private val context: Context,
    val dao: GameDao,
    private val prefs: Prefs,
    api: MiroirApi,
    private val miroir: MiroirRepository,
) {
    val bank = GameBank(context)
    private val api = GameApi(api)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false; explicitNulls = false }
    private val writeMutex = Mutex()
    private val syncMutex = Mutex()

    init {
        miroir.resetHooks += { clearAll() }
    }

    // ---------------------------------------------------------------- entrées

    fun customItem(e: GameCustomEntity): GameItem? = runCatching {
        val c = json.decodeFromString(CustomGameItem.serializer(), e.payload)
        GameItem(
            id = GameItem.CUSTOM_PREFIX + e.id,
            category = GameCategory.of(c.category) ?: return null,
            kind = ItemKind.of(c.kind) ?: return null,
            intensity = c.intensity.coerceIn(1, 3),
            text = c.text,
            format = ItemFormat.of(c.format),
            options = c.options,
            min = c.min,
            max = c.max,
            remote = c.remote,
            author = e.author,
        )
    }.getOrNull()

    suspend fun customItems(): List<GameItem> = dao.custom().filter { !it.deleted }.mapNotNull(::customItem)

    suspend fun allItems(): List<GameItem> = bank.all() + customItems()

    suspend fun item(id: String): GameItem? =
        if (id.startsWith(GameItem.CUSTOM_PREFIX)) customItems().firstOrNull { it.id == id } else bank.byId(id)

    suspend fun items(ids: List<String>): List<GameItem> {
        val custom = if (ids.any { it.startsWith(GameItem.CUSTOM_PREFIX) }) customItems().associateBy { it.id } else emptyMap()
        return ids.mapNotNull { custom[it] ?: bank.byId(it) }
    }

    /** Filtre courant : intensité commune (minimum des deux) et union des exclusions. */
    suspend fun filter(kinds: Set<ItemKind>, formats: Set<ItemFormat>? = null, remoteOnly: Boolean = false): DrawFilter {
        val p = prefs.current()
        return DrawFilter(kinds = kinds, intensity = p.gamesIntensity, excluded = p.gamesExcluded, formats = formats, remoteOnly = remoteOnly)
    }

    suspend fun draw(filter: DrawFilter, n: Int): List<GameItem> =
        GameDraw.draw(allItems(), filter, dao.seen().associate { it.itemId to it.at }, n)

    suspend fun markSeen(ids: Collection<String>) {
        if (ids.isNotEmpty()) dao.upsertSeen(ids.map { GameSeenEntity(it, System.currentTimeMillis()) })
    }

    // ---------------------------------------------------------------- parties

    suspend fun me(): Member = prefs.credentials()?.memberEnum ?: prefs.current().member

    fun newId(): String = PairCrypto.b64(PairCrypto.randomBytes(16))

    fun payloadOf(s: GameSessionEntity): SessionPayload =
        runCatching { json.decodeFromString(SessionPayload.serializer(), s.payload) }.getOrDefault(SessionPayload())

    fun answerOf(a: GameAnswerEntity): GameAnswer? = a.payload?.let { runCatching { json.decodeFromString(GameAnswer.serializer(), it) }.getOrNull() }

    fun observeSessions(): Flow<List<GameSessionEntity>> = dao.observeSessions()

    suspend fun createSession(
        kind: GameKind,
        payload: SessionPayload,
        notify: Boolean = true,
        id: String = newId(),
        status: String = "open",
    ): String {
        val now = System.currentTimeMillis()
        writeMutex.withLock {
            dao.upsertSession(
                GameSessionEntity(
                    id = id, kind = kind.key, reveal = kind.reveal, createdBy = me().key,
                    payload = json.encodeToString(SessionPayload.serializer(), payload),
                    status = status, createdAt = now, updatedAt = now, synced = false, notify = notify,
                ),
            )
        }
        markSeen(payload.items)
        SyncScheduler.enqueueSend(context)
        return id
    }

    /** Une réponse donnée ne se modifie plus (sauf éléments publics « ~ » et parties ouvertes). */
    suspend fun answer(sessionId: String, item: String, answer: GameAnswer, notify: Boolean = false) {
        writeMutex.withLock {
            val session = dao.session(sessionId) ?: return
            val me = me().key
            val existing = dao.answers(sessionId).firstOrNull { it.member == me && it.item == item }
            val editable = session.reveal == "open" || item.startsWith("~")
            if (existing?.payload != null && !editable) return
            dao.upsertAnswer(
                GameAnswerEntity(
                    sessionId = sessionId, member = me, item = item,
                    payload = json.encodeToString(GameAnswer.serializer(), answer.copy(at = answer.at.takeIf { it > 0 } ?: System.currentTimeMillis())),
                    at = System.currentTimeMillis(), synced = false, notify = notify,
                ),
            )
            dao.upsertSession(session.copy(updatedAt = System.currentTimeMillis()))
        }
        SyncScheduler.enqueueSend(context)
    }

    suspend fun setStatus(sessionId: String, status: String) {
        writeMutex.withLock {
            val s = dao.session(sessionId) ?: return
            if (s.status == status) return
            dao.upsertSession(s.copy(status = status, updatedAt = System.currentTimeMillis(), statusDirty = s.synced || s.statusDirty))
        }
        SyncScheduler.enqueueSend(context)
    }

    /** Défi de la semaine : tiré le lundi, commun aux deux (la première proposition envoyée l'emporte). */
    suspend fun weeklyDefi(day: LocalDate? = null): GameSessionEntity? {
        val week = isoWeek(day ?: miroir.today())
        val id = "defi-week-$week"
        dao.session(id)?.let { return it }
        val item = draw(filter(setOf(ItemKind.DEFI)), 1).firstOrNull() ?: return null
        createSession(GameKind.DEFI, SessionPayload(items = listOf(item.id), week = week), notify = false, id = id)
        return dao.session(id)
    }

    // ---------------------------------------------------------------- envies, réglages, perso, épingles

    suspend fun setWish(itemId: String, answer: Int) {
        dao.upsertWishes(listOf(GameWishEntity(itemId, answer.coerceIn(Wish.NO, Wish.YES), System.currentTimeMillis(), synced = false)))
        SyncScheduler.enqueueSend(context)
    }

    suspend fun updateSettings(transform: (GameSettings) -> GameSettings) {
        prefs.update {
            gamesSettings(transform(gamesSettings).let { it.copy(intensity = it.intensity.coerceIn(1, 3)) })
            gamesSettingsDirty = true
        }
        SyncScheduler.enqueueSend(context)
    }

    suspend fun addCustom(item: CustomGameItem): String {
        val id = newId()
        dao.upsertCustom(GameCustomEntity(id, me().key, json.encodeToString(CustomGameItem.serializer(), item), System.currentTimeMillis(), synced = false))
        SyncScheduler.enqueueSend(context)
        return id
    }

    suspend fun deleteCustom(id: String) {
        val e = dao.custom().firstOrNull { it.id == id } ?: return
        dao.upsertCustom(e.copy(deleted = true, synced = false))
        SyncScheduler.enqueueSend(context)
    }

    suspend fun pin(p: GamePinEntity) = dao.upsertPin(p)
    suspend fun unpin(sessionId: String, item: String) = dao.deletePin(sessionId, item)

    // ---------------------------------------------------------------- envoi (file hors ligne)

    /** Envoie tout ce qui attend. Faux s'il reste quelque chose (réseau) : le travail sera relancé. */
    suspend fun flushPending(): Boolean {
        val c = prefs.credentials() ?: return true
        if (!prefs.current().partnerJoined) return true
        val crypto = miroir.crypto(c)
        val me = c.memberEnum.key
        return try {
            pushSettings(c, crypto, me)
            for (e in dao.pendingCustom()) {
                api.custom(c, GameCustomRequest(e.id, crypto.encrypt(e.payload, "game:custom:${e.id}"), e.deleted))
                writeMutex.withLock { dao.custom().firstOrNull { it.id == e.id }?.let { if (it.payload == e.payload && it.deleted == e.deleted) dao.upsertCustom(it.copy(synced = true)) } }
            }
            for (s in dao.pendingSessions()) {
                if (!s.synced) {
                    val res = api.createSession(
                        c,
                        CreateSessionRequest(s.id, s.kind, s.reveal, crypto.encrypt(s.payload, "game:session:${s.id}"), s.status, notify = s.notify),
                    )
                    writeMutex.withLock { dao.upsertSession(s.copy(synced = true, statusDirty = false, notify = false)) }
                    // Partie déjà créée par l'autre (ex. défi de la semaine) : la version du serveur l'emporte.
                    res.session?.let { mergeSessions(crypto, me, listOf(it)) }
                } else if (s.statusDirty) {
                    val res = api.status(c, SessionStatusRequest(s.id, s.status))
                    writeMutex.withLock { dao.session(s.id)?.let { if (it.status == s.status) dao.upsertSession(it.copy(statusDirty = false)) } }
                    res.session?.let { mergeSessions(crypto, me, listOf(it)) }
                }
            }
            for (a in dao.pendingAnswers()) {
                val payload = a.payload ?: continue
                val res = try {
                    api.answer(c, GameAnswerRequest(a.sessionId, a.item, crypto.encrypt(payload, answerContext(a.sessionId, a.member, a.item)), a.notify))
                } catch (e: ApiException) {
                    if (e.status == 404 || e.status == 409) {
                        // Partie inconnue ou annulée sur le serveur : on abandonne cette réponse.
                        Diagnostics.record("jeux", "réponse abandonnée (${e.error.error})")
                        writeMutex.withLock { dao.upsertAnswer(a.copy(synced = true)) }
                        continue
                    }
                    throw e
                }
                writeMutex.withLock {
                    dao.answers(a.sessionId).firstOrNull { it.member == a.member && it.item == a.item }?.let {
                        if (it.payload == a.payload) dao.upsertAnswer(it.copy(synced = true, notify = false))
                    }
                }
                res.session?.let { mergeSessions(crypto, me, listOf(it)) }
            }
            pushWishes(c, crypto, c.memberEnum)
            true
        } catch (e: IOException) {
            if (e is ApiException) Diagnostics.record("jeux", "envoi refusé (${e.status} ${e.error.error})", e)
            false
        }
    }

    private suspend fun pushSettings(c: PairCredentials, crypto: PairCrypto, me: String) {
        val p = prefs.current()
        if (!p.gamesSettingsDirty) return
        val sent = p.gamesSettings
        api.settings(c, GameSettingsRequest(crypto.encrypt(json.encodeToString(GameSettings.serializer(), sent), "game:settings:$me")))
        prefs.update { if (gamesSettings == sent) gamesSettingsDirty = false }
    }

    private suspend fun pushWishes(c: PairCredentials, crypto: PairCrypto, me: Member) {
        while (true) {
            val batch = dao.pendingWishes(MAX_WISHES)
            if (batch.isEmpty()) return
            val res = api.wishes(
                c,
                WishesRequest(
                    batch.map { w ->
                        val (t1, t2) = wishTags(crypto, me, w.itemId, w.answer)
                        val slot = wishSlot(crypto, me, w.itemId)
                        WishEntryDto(slot, t1, t2, crypto.encrypt(json.encodeToString(WishBackup.serializer(), WishBackup(w.itemId, w.answer, w.at)), "game:wish:$slot"))
                    },
                ),
            )
            writeMutex.withLock {
                val current = dao.wishes().associateBy { it.itemId }
                dao.upsertWishes(batch.filter { current[it.itemId]?.answer == it.answer }.map { it.copy(synced = true) })
            }
            applyMatches(crypto, me, res.matches)
        }
    }

    // ---------------------------------------------------------------- synchro

    suspend fun sync(): GameSyncResult = syncMutex.withLock {
        val c = prefs.credentials() ?: return GameSyncResult()
        if (!prefs.current().partnerJoined) return GameSyncResult()
        val crypto = miroir.crypto(c)
        val me = c.memberEnum
        val before = prefs.current()
        val res = api.state(c, GameStateRequest(since = before.gamesRev, wishesSince = before.gamesWishesRev))

        mergeSessions(crypto, me.key, res.sessions)

        for (s in res.settings) {
            val settings = crypto.decryptOrNull(s.payload, "game:settings:${s.member}")
                ?.let { runCatching { json.decodeFromString(GameSettings.serializer(), it) }.getOrNull() } ?: continue
            if (s.member == me.key) {
                // Nouveau téléphone : on retrouve ses réglages, sauf modification locale en attente.
                prefs.update { if (!gamesSettingsDirty) gamesSettings(settings) }
            } else {
                prefs.update { partnerGamesSettings(settings) }
            }
        }

        if (res.custom.isNotEmpty()) writeMutex.withLock {
            val local = dao.custom().associateBy { it.id }
            for (q in res.custom) {
                if (local[q.id]?.synced == false) continue
                val plain = crypto.decryptOrNull(q.payload, "game:custom:${q.id}") ?: continue
                dao.upsertCustom(GameCustomEntity(q.id, q.author, plain, q.createdAt, q.deleted, synced = true))
            }
        }

        if (res.wishes.mine.isNotEmpty()) writeMutex.withLock {
            val local = dao.wishes().associateBy { it.itemId }
            val restored = res.wishes.mine.mapNotNull { w ->
                crypto.decryptOrNull(w.payload, "game:wish:${w.slot}")
                    ?.let { runCatching { json.decodeFromString(WishBackup.serializer(), it) }.getOrNull() }
            }.filter { b -> local[b.item]?.let { it.synced && it.at <= b.at } ?: true }
            dao.upsertWishes(restored.map { GameWishEntity(it.item, it.answer, it.at, synced = true) })
        }
        val newMatches = applyMatches(crypto, me, res.wishes.matches)

        prefs.update {
            gamesRev(res.rev)
            gamesWishesRev(res.wishes.rev)
        }
        GameSyncResult(newMatches)
    }

    private suspend fun mergeSessions(crypto: PairCrypto, me: String, sessions: List<GameSessionDto>) {
        if (sessions.isEmpty()) return
        val seenIds = mutableListOf<String>()
        writeMutex.withLock {
            for (s in sessions) {
                val local = dao.session(s.id)
                val plain = crypto.decryptOrNull(s.payload, "game:session:${s.id}") ?: continue
                if (local == null || local.payload != plain) {
                    seenIds += runCatching { json.decodeFromString(SessionPayload.serializer(), plain).items }.getOrDefault(emptyList())
                }
                dao.upsertSession(
                    GameSessionEntity(
                        id = s.id, kind = s.kind, reveal = s.reveal, createdBy = s.createdBy, payload = plain,
                        status = if (local?.statusDirty == true && local.synced) local.status else s.status,
                        createdAt = s.createdAt, updatedAt = maxOf(s.updatedAt, local?.updatedAt ?: 0), serverRev = s.rev,
                        synced = true, statusDirty = local?.statusDirty == true && local.synced,
                    ),
                )
                val localAnswers = dao.answers(s.id).associateBy { it.member to it.item }
                val merged = s.answers.mapNotNull { a ->
                    val mine = localAnswers[a.member to a.item]
                    if (mine != null && !mine.synced) return@mapNotNull null // envoi en attente : on garde la version locale
                    val plainAnswer = a.payload?.let { crypto.decryptOrNull(it, answerContext(s.id, a.member, a.item)) }
                    GameAnswerEntity(s.id, a.member, a.item, plainAnswer ?: mine?.payload?.takeIf { a.member == me }, a.at, synced = true)
                }
                dao.upsertAnswers(merged)
            }
        }
        markSeen(seenIds)
    }

    /** Traduit les étiquettes communes renvoyées par le serveur en envies révélées. Renvoie les nouvelles. */
    private suspend fun applyMatches(crypto: PairCrypto, me: Member, matches: List<String>): Int = writeMutex.withLock {
        val set = matches.toSet()
        val found = mutableMapOf<String, MatchKind>()
        for (w in dao.wishes()) {
            if (w.answer == Wish.NO) continue
            val kind = when (w.answer) {
                Wish.YES -> when {
                    crypto.wishTag("oo:${w.itemId}") in set -> MatchKind.MATCH
                    crypto.wishTag(cross(me, yes = true) + ":${w.itemId}") in set -> MatchKind.DISCUSS
                    else -> null
                }
                else -> MatchKind.DISCUSS.takeIf { crypto.wishTag(cross(me, yes = false) + ":${w.itemId}") in set }
            } ?: continue
            found[w.itemId] = kind
        }
        val existing = dao.matches().associateBy { it.itemId }
        val now = System.currentTimeMillis()
        val fresh = found.filter { (id, kind) -> existing[id]?.kind != kind.name }
        dao.upsertMatches(fresh.map { (id, kind) -> GameMatchEntity(id, kind.name, now, seen = false) })
        if (found.isEmpty()) dao.clearMatches() else dao.keepMatches(found.keys.toList())
        fresh.size
    }

    suspend fun markMatchesSeen() = dao.markMatchesSeen()

    private suspend fun clearAll() {
        dao.clearSessions(); dao.clearAnswers(); dao.clearCustom(); dao.clearWishes(); dao.clearMatches(); dao.clearSeen(); dao.clearPins()
    }

    companion object {
        private const val MAX_WISHES = 100

        fun answerContext(sessionId: String, member: String, item: String) = "game:answer:$sessionId:$member:$item"

        fun isoWeek(day: LocalDate): String = "%d-W%02d".format(day.get(IsoFields.WEEK_BASED_YEAR), day.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR))

        /**
         * Étiquettes d'une envie, calculées pour que le serveur ne voie coïncider que oui+oui (« oo ») et
         * oui+peut-être (« AyBm » : A oui, B peut-être ; « ByAm » : l'inverse). Un « non » n'envoie que du hasard.
         */
        fun wishTags(crypto: PairCrypto, me: Member, itemId: String, answer: Int): Pair<String, String> = when (answer) {
            Wish.YES -> crypto.wishTag("oo:$itemId") to crypto.wishTag(cross(me, yes = true) + ":$itemId")
            Wish.MAYBE -> crypto.wishTag(cross(me, yes = false) + ":$itemId") to randomTag()
            else -> randomTag() to randomTag()
        }

        /** Étiquette croisée : mon oui rencontre son peut-être, ou mon peut-être rencontre son oui. */
        fun cross(me: Member, yes: Boolean): String = if ((me == Member.A) == yes) "AyBm" else "ByAm"

        fun wishSlot(crypto: PairCrypto, me: Member, itemId: String) = crypto.wishTag("slot:${me.key}:$itemId")

        private fun randomTag() = PairCrypto.b64(PairCrypto.randomBytes(24))
    }
}
