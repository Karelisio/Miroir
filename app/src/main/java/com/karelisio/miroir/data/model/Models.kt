package com.karelisio.miroir.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

enum class Member(val key: String) {
    A("A"), B("B");

    val other: Member get() = if (this == A) B else A

    companion object {
        fun of(key: String?): Member? = entries.firstOrNull { it.key == key }
    }
}

enum class Category(val key: String, val label: String, val emoji: String) {
    FUN("fun", "Fun", "🎈"),
    SOUVENIRS("souvenirs", "Souvenirs", "📸"),
    PROFOND("profond", "Profond", "🌊"),
    FUTUR("futur", "Futur", "🔭"),
    NOUS_DEUX("nous_deux", "Nous deux", "💞"),
    TU_PREFERES("tu_preferes", "Tu préfères", "⚖️"),
    QUI_DE_NOUS("qui_de_nous", "Qui de nous deux", "👀"),
    HYPOTHETIQUE("hypothetique", "Hypothétique", "🪄"),
    VALEURS("valeurs", "Valeurs", "🧭"),
    GOUTS("gouts", "Goûts", "🍓"),
    ENFANCE("enfance", "Enfance", "🧸"),
    VOYAGES("voyages", "Voyages", "✈️"),
    QUOTIDIEN("quotidien", "Petites choses du quotidien", "☕"),
    PERSO("perso", "Vos questions", "✍️");

    companion object {
        val drawable = entries.filter { it != PERSO }
        fun of(key: String?): Category = entries.firstOrNull { it.key == key } ?: FUN
    }
}

enum class Pack(val key: String, val label: String, val emoji: String) {
    NOEL("noel", "Spécial Noël", "🎄"),
    NOUVEL_AN("nouvel_an", "Nouvel an", "🥂"),
    SAINT_VALENTIN("saint_valentin", "Saint-Valentin", "💌"),
    ETE("ete", "Spécial été", "☀️"),
    ANNIVERSAIRE_COUPLE("anniversaire_couple", "Notre anniversaire", "💍"),
    ANNIVERSAIRE("anniversaire", "Joyeux anniversaire", "🎂");

    companion object {
        fun of(key: String?): Pack? = entries.firstOrNull { it.key == key }
    }
}

@Serializable
enum class QuestionType {
    @SerialName("text") TEXT,
    @SerialName("choice") CHOICE,
    @SerialName("scale") SCALE,
    @SerialName("who") WHO,
}

@Serializable
data class Question(
    val id: String,
    val category: String,
    val type: QuestionType,
    val text: String,
    val options: List<String> = emptyList(),
    val minLabel: String? = null,
    val maxLabel: String? = null,
    val tags: List<String> = emptyList(),
    val pack: String? = null,
) {
    val categoryEnum: Category get() = Category.of(category)
    val packEnum: Pack? get() = Pack.of(pack)
}

/** Réponse chiffrée envoyée au Worker. [who] est absolu ("A" ou "B"), pas relatif. */
@Serializable
data class AnswerPayload(
    val questionId: String,
    val type: QuestionType,
    val text: String? = null,
    val choice: Int? = null,
    val scale: Int? = null,
    val who: String? = null,
    val at: Long,
)

@Serializable
data class ReactionPayload(val emoji: String? = null, val comment: String? = null, val at: Long)

@Serializable
data class ProfilePayload(val name: String, val birthday: String? = null)

/** Réglages partagés par le couple (chiffrés côté serveur, version optimiste). */
@Serializable
data class SharedSettings(
    val disabledCategories: Set<String> = emptySet(),
    val packsEnabled: Boolean = true,
    val anniversary: String? = null,
    val timezone: String? = null,
)

@Serializable
data class CustomQuestionPayload(
    val text: String,
    val type: QuestionType,
    val options: List<String> = emptyList(),
    val minLabel: String? = null,
    val maxLabel: String? = null,
    val createdDay: String,
)
