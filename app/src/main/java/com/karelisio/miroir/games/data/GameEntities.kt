package com.karelisio.miroir.games.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Une partie (à distance, canapé ou défi). [payload] : SessionPayload en clair, déchiffré localement. */
@Entity(tableName = "game_sessions")
data class GameSessionEntity(
    @PrimaryKey val id: String,
    val kind: String,
    val reveal: String,
    val createdBy: String,
    val payload: String,
    val status: String = "open",
    val createdAt: Long,
    val updatedAt: Long,
    val serverRev: Long = 0,
    /** Envoyée au Worker. */
    val synced: Boolean = false,
    /** Statut modifié localement, à envoyer. */
    val statusDirty: Boolean = false,
    /** Prévenir l'autre à la création (« Nouveau défi »). */
    val notify: Boolean = false,
)

/** Réponse d'un membre à un élément. [payload] null : l'autre a répondu, mais ce n'est pas encore visible. */
@Entity(tableName = "game_answers", primaryKeys = ["sessionId", "member", "item"])
data class GameAnswerEntity(
    val sessionId: String,
    val member: String,
    val item: String,
    val payload: String?,
    val at: Long,
    val synced: Boolean = true,
    /** Demander une notification à l'autre à l'envoi. */
    val notify: Boolean = false,
)

@Entity(tableName = "game_custom")
data class GameCustomEntity(
    @PrimaryKey val id: String,
    val author: String,
    val payload: String,
    val createdAt: Long,
    val deleted: Boolean = false,
    val synced: Boolean = false,
)

/** Ma réponse à une envie (0 non, 1 peut-être, 2 oui). */
@Entity(tableName = "game_wishes")
data class GameWishEntity(
    @PrimaryKey val itemId: String,
    val answer: Int,
    val at: Long,
    val synced: Boolean = false,
)

/** Envie commune révélée par le serveur (oui + oui, ou oui + peut-être « à discuter »). */
@Entity(tableName = "game_matches")
data class GameMatchEntity(
    @PrimaryKey val itemId: String,
    val kind: String,
    val at: Long,
    val seen: Boolean = false,
)

/** Entrées déjà jouées : pas de répétition tant que la catégorie n'est pas épuisée. */
@Entity(tableName = "game_seen")
data class GameSeenEntity(
    @PrimaryKey val itemId: String,
    val at: Long,
)

/** « Nos révélations » : réponses de l'autre épinglées. */
@Entity(tableName = "game_pins", primaryKeys = ["sessionId", "item"])
data class GamePinEntity(
    val sessionId: String,
    val item: String,
    val question: String,
    val answer: String,
    val author: String,
    val at: Long,
)
