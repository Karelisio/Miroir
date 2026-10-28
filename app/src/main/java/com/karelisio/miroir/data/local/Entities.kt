package com.karelisio.miroir.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Une journée du couple : question verrouillée, réponses (déchiffrées), réactions. */
@Entity(tableName = "days")
data class DayEntity(
    @PrimaryKey val day: String,
    val questionId: String,
    val tag: String,
    val questionText: String,
    val category: String,
    val pack: String? = null,
    val skips: Int = 0,
    val skippedQuestionId: String? = null,
    /** Verrou confirmé par le serveur (sinon proposition locale hors ligne). */
    val locked: Boolean = false,
    val myAnswer: String? = null,
    val myAnsweredAt: Long? = null,
    val mySynced: Boolean = false,
    val partnerAnswered: Boolean = false,
    val partnerAnswer: String? = null,
    val partnerAnsweredAt: Long? = null,
    val myReaction: String? = null,
    val myReactionSynced: Boolean = true,
    val partnerReaction: String? = null,
    /** La cérémonie de révélation a été vue sur ce téléphone. */
    val revealSeen: Boolean = false,
    val serverRev: Long = 0,
    /** Favori ⭐ (local à ce téléphone). */
    val favorite: Boolean = false,
) {
    val bothAnswered: Boolean get() = myAnswer != null && partnerAnswer != null
}

@Entity(tableName = "custom_questions")
data class CustomQuestionEntity(
    @PrimaryKey val id: String,
    val author: String,
    val payload: String,
    val createdDay: String,
    val createdAt: Long,
    val deleted: Boolean = false,
    val synced: Boolean = false,
)
