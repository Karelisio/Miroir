package com.karelisio.miroir

import com.karelisio.miroir.data.model.Question
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

/** Charge la vraie banque embarquée (assets/questions.json) pour les tests JVM. */
object TestBank {
    @Serializable
    private data class BankFile(val version: String, val count: Int, val questions: List<Question>)

    private val json = Json { ignoreUnknownKeys = true }

    val questions: List<Question> by lazy {
        val file = listOf(File("src/main/assets/questions.json"), File("app/src/main/assets/questions.json")).first { it.exists() }
        json.decodeFromString(BankFile.serializer(), file.readText()).questions.sortedBy { it.id }
    }

    fun byId(id: String) = questions.first { it.id == id }
}
