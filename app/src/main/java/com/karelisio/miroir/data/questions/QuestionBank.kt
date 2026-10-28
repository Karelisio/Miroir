package com.karelisio.miroir.data.questions

import android.content.Context
import com.karelisio.miroir.data.model.Question
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Banque embarquée (assets/questions.json), chargée une fois et gardée en mémoire. */
class QuestionBank(private val context: Context) {

    @Serializable
    private data class BankFile(val version: String, val count: Int, val questions: List<Question>)

    private val mutex = Mutex()
    @Volatile private var cache: Map<String, Question>? = null
    @Volatile private var ordered: List<Question> = emptyList()

    suspend fun all(): List<Question> {
        load()
        return ordered
    }

    suspend fun byId(id: String): Question? = load()[id]

    private suspend fun load(): Map<String, Question> = cache ?: mutex.withLock {
        cache ?: withContext(Dispatchers.IO) {
            val file = context.assets.open("questions.json").bufferedReader().use { it.readText() }
            val bank = json.decodeFromString<BankFile>(file)
            ordered = bank.questions.sortedBy { it.id }
            ordered.associateBy { it.id }.also { cache = it }
        }
    }

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        /** Remplace le marqueur {prenom} des questions d'anniversaire. */
        fun render(text: String, birthdayName: String?): String =
            text.replace("{prenom}", birthdayName?.takeIf { it.isNotBlank() } ?: "toi")
    }
}
