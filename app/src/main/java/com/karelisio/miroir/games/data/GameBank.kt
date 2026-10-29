package com.karelisio.miroir.games.data

import android.content.Context
import com.karelisio.miroir.games.model.BankFile
import com.karelisio.miroir.games.model.GameCategory
import com.karelisio.miroir.games.model.GameItem
import com.karelisio.miroir.games.model.ItemFormat
import com.karelisio.miroir.games.model.ItemKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

/** Banque embarquée (assets/games.json, versionnée par empreinte), chargée une fois à la demande. */
class GameBank(private val context: Context) {
    private val json = Json { ignoreUnknownKeys = true }
    private val mutex = Mutex()
    @Volatile private var cache: Map<String, GameItem>? = null
    @Volatile var version: String = ""
        private set

    suspend fun all(): Collection<GameItem> = load().values

    suspend fun byId(id: String): GameItem? = load()[id]

    private suspend fun load(): Map<String, GameItem> = cache ?: mutex.withLock {
        cache ?: withContext(Dispatchers.IO) {
            val file = runCatching {
                context.assets.open("games.json").bufferedReader().use { json.decodeFromString(BankFile.serializer(), it.readText()) }
            }.getOrNull()
            version = file?.version.orEmpty()
            file?.items.orEmpty().mapNotNull { b ->
                val category = GameCategory.of(b.c) ?: return@mapNotNull null
                val kind = ItemKind.of(b.m) ?: return@mapNotNull null
                GameItem(
                    id = b.id, category = category, kind = kind, intensity = b.i.coerceIn(1, 3), text = b.t,
                    format = ItemFormat.of(b.f), options = b.o.orEmpty(), min = b.lo, max = b.hi, remote = b.r,
                )
            }.associateBy { it.id }
        }.also { cache = it }
    }
}
