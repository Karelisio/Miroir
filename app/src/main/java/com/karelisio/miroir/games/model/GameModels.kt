package com.karelisio.miroir.games.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Catégories de « De l'autre côté ». [spicy] : soumises au verrou biométrique optionnel. */
enum class GameCategory(val key: String, val label: String, val emoji: String, val spicy: Boolean = false) {
    SOUVENIRS("souvenirs", "Souvenirs", "📸"),
    NOUS_DEUX("nous_deux", "Nous deux", "💞"),
    FUTUR("futur", "Futur", "🔭"),
    VALEURS("valeurs", "Valeurs", "🧭"),
    FUN("fun", "Fun et absurde", "🤪"),
    ROMANTIQUE("romantique", "Romantique", "🌹"),
    OSE("ose", "Osé", "🌶", spicy = true),
    INTIME("intime", "Intime", "🌶🌶🌶", spicy = true);

    companion object {
        fun of(key: String?): GameCategory? = entries.firstOrNull { it.key == key }
    }
}

/** Nature d'une entrée de la banque (un mode peut en utiliser plusieurs). */
enum class ItemKind(val key: String) {
    DEVINE("devine"), PREFERES("preferes"), VERITE("verite"), ACTION("action"), ENVIE("envie"), DEFI("defi");

    companion object {
        fun of(key: String?): ItemKind? = entries.firstOrNull { it.key == key }
    }
}

enum class ItemFormat(val key: String) {
    CHOICE("choice"), SCALE("scale"), TEXT("text");

    companion object {
        fun of(key: String?): ItemFormat? = entries.firstOrNull { it.key == key }
    }
}

/** Une question, un dilemme, une envie, un gage ou un défi. Les questions perso ont un id « c:… ». */
data class GameItem(
    val id: String,
    val category: GameCategory,
    val kind: ItemKind,
    val intensity: Int,
    val text: String,
    val format: ItemFormat? = null,
    val options: List<String> = emptyList(),
    val min: String? = null,
    val max: String? = null,
    val remote: Boolean = false,
    val author: String? = null,
) {
    val custom: Boolean get() = id.startsWith(CUSTOM_PREFIX)

    /** Remplace le repère {autre} par le prénom de la personne à qui l'on s'adresse. */
    fun render(other: String): String = text.replace("{autre}", other)

    companion object {
        const val CUSTOM_PREFIX = "c:"
    }
}

/** Format compact de assets/games.json (généré par games/build.mjs). */
@Serializable
internal data class BankFile(val version: String, val items: List<BankItem>)

@Serializable
internal data class BankItem(
    val id: String,
    val c: String,
    val m: String,
    val i: Int,
    val t: String,
    val f: String? = null,
    val o: List<String>? = null,
    val lo: String? = null,
    val hi: String? = null,
    val r: Boolean = false,
)

/** Les modes jouables et leur règle de révélation côté serveur. */
enum class GameKind(val key: String, val label: String, val emoji: String, val reveal: String) {
    DEVINE("devine", "Devine-moi", "🔮", "item"),
    DUEL("duel", "Duel à distance", "⚔️", "all"),
    PREFERES("preferes", "Tu préfères", "⚖️", "all"),
    ACTION_VERITE("av", "Action ou vérité", "🎲", "open"),
    SURPRISE("surprise", "Question surprise", "🎁", "open"),
    DEFI("defi", "Défi de la semaine", "🏅", "open"),
    CANAPE("canape", "Mode canapé", "🛋️", "open");

    companion object {
        fun of(key: String?): GameKind? = entries.firstOrNull { it.key == key }
    }
}

/** Contenu chiffré d'une partie. */
@Serializable
data class SessionPayload(
    val items: List<String> = emptyList(),
    /** Devine-moi : le membre qui répond sur lui-même. */
    val answerer: String? = null,
    /** Action ou vérité : nombre de tours prévus. */
    val turns: Int? = null,
    /** Défi de la semaine : « 2026-W44 ». */
    val week: String? = null,
    /** Question surprise : le texte écrit pour l'autre. */
    val question: String? = null,
    /** Mode canapé : jeu joué et résumé (pour les stats et l'historique des deux téléphones). */
    val game: String? = null,
    val rounds: Int? = null,
    val score: Int? = null,
)

/** Valeur de réponse selon le format : choix (index), échelle (1..10) ou texte libre. */
@Serializable
data class GameValue(val choice: Int? = null, val scale: Int? = null, val text: String? = null) {
    /** Pronostic juste ? (échelle à un point près ; texte : jugé par la personne concernée). */
    fun matches(other: GameValue?): Boolean? = when {
        other == null -> null
        choice != null -> choice == other.choice
        scale != null && other.scale != null -> kotlin.math.abs(scale - other.scale) <= 1
        else -> null
    }

    fun display(item: GameItem?): String = when {
        choice != null -> item?.options?.getOrNull(choice) ?: "—"
        scale != null -> "$scale / 10"
        else -> text.orEmpty()
    }
}

/** Réponse chiffrée à un élément d'une partie. */
@Serializable
data class GameAnswer(
    /** Ma réponse sur moi-même. */
    val self: GameValue? = null,
    /** Mon pronostic sur la réponse de l'autre. */
    val guess: GameValue? = null,
    /** Action ou vérité : « action » ou « verite », et l'entrée tirée. */
    val pick: String? = null,
    val item: String? = null,
    /** Action faite, ou pronostic texte jugé juste. */
    val ok: Boolean? = null,
    val at: Long = 0,
)

/** Réglages de jeu de chacun (synchronisés, chiffrés) : l'intensité commune est le minimum des deux. */
@Serializable
data class GameSettings(
    val intensity: Int = 1,
    val excluded: Set<String> = emptySet(),
)

/** Question perso (chiffrée) ajoutée par l'un de vous. */
@Serializable
data class CustomGameItem(
    val category: String,
    val kind: String,
    val intensity: Int,
    val text: String,
    val format: String? = null,
    val options: List<String> = emptyList(),
    val min: String? = null,
    val max: String? = null,
    val remote: Boolean = false,
)

/** Sauvegarde chiffrée de ma réponse à une envie (relue seulement par moi). */
@Serializable
data class WishBackup(val item: String, val answer: Int, val at: Long)

object Wish {
    const val NO = 0
    const val MAYBE = 1
    const val YES = 2
}

@Serializable
enum class MatchKind {
    @SerialName("match") MATCH,
    @SerialName("discuss") DISCUSS,
}
