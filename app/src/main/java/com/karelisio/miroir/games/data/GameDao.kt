package com.karelisio.miroir.games.data

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface GameDao {
    // Parties
    @Query("SELECT * FROM game_sessions ORDER BY updatedAt DESC")
    fun observeSessions(): Flow<List<GameSessionEntity>>

    @Query("SELECT * FROM game_sessions ORDER BY updatedAt DESC")
    suspend fun sessions(): List<GameSessionEntity>

    @Query("SELECT * FROM game_sessions WHERE id = :id")
    fun observeSession(id: String): Flow<GameSessionEntity?>

    @Query("SELECT * FROM game_sessions WHERE id = :id")
    suspend fun session(id: String): GameSessionEntity?

    @Query("SELECT * FROM game_sessions WHERE synced = 0 OR statusDirty = 1")
    suspend fun pendingSessions(): List<GameSessionEntity>

    @Upsert
    suspend fun upsertSession(s: GameSessionEntity)

    // Réponses
    @Query("SELECT * FROM game_answers")
    fun observeAnswers(): Flow<List<GameAnswerEntity>>

    @Query("SELECT * FROM game_answers")
    suspend fun answers(): List<GameAnswerEntity>

    @Query("SELECT * FROM game_answers WHERE sessionId = :sessionId")
    fun observeAnswers(sessionId: String): Flow<List<GameAnswerEntity>>

    @Query("SELECT * FROM game_answers WHERE sessionId = :sessionId")
    suspend fun answers(sessionId: String): List<GameAnswerEntity>

    @Query("SELECT * FROM game_answers WHERE synced = 0")
    suspend fun pendingAnswers(): List<GameAnswerEntity>

    @Upsert
    suspend fun upsertAnswer(a: GameAnswerEntity)

    @Upsert
    suspend fun upsertAnswers(a: List<GameAnswerEntity>)

    // Questions perso
    @Query("SELECT * FROM game_custom ORDER BY createdAt DESC")
    fun observeCustom(): Flow<List<GameCustomEntity>>

    @Query("SELECT * FROM game_custom")
    suspend fun custom(): List<GameCustomEntity>

    @Query("SELECT * FROM game_custom WHERE synced = 0")
    suspend fun pendingCustom(): List<GameCustomEntity>

    @Upsert
    suspend fun upsertCustom(c: GameCustomEntity)

    // Envies
    @Query("SELECT * FROM game_wishes")
    fun observeWishes(): Flow<List<GameWishEntity>>

    @Query("SELECT * FROM game_wishes")
    suspend fun wishes(): List<GameWishEntity>

    @Query("SELECT * FROM game_wishes WHERE synced = 0 LIMIT :limit")
    suspend fun pendingWishes(limit: Int): List<GameWishEntity>

    @Upsert
    suspend fun upsertWishes(w: List<GameWishEntity>)

    @Query("SELECT * FROM game_matches ORDER BY at DESC")
    fun observeMatches(): Flow<List<GameMatchEntity>>

    @Query("SELECT * FROM game_matches")
    suspend fun matches(): List<GameMatchEntity>

    @Upsert
    suspend fun upsertMatches(m: List<GameMatchEntity>)

    @Query("DELETE FROM game_matches WHERE itemId NOT IN (:keep)")
    suspend fun keepMatches(keep: List<String>)

    @Query("DELETE FROM game_matches")
    suspend fun clearMatches()

    @Query("UPDATE game_matches SET seen = 1")
    suspend fun markMatchesSeen()

    // Historique des entrées jouées
    @Query("SELECT * FROM game_seen")
    suspend fun seen(): List<GameSeenEntity>

    @Upsert
    suspend fun upsertSeen(s: List<GameSeenEntity>)

    @Query("DELETE FROM game_seen WHERE itemId IN (:ids)")
    suspend fun forget(ids: List<String>)

    // Révélations épinglées
    @Query("SELECT * FROM game_pins ORDER BY at DESC")
    fun observePins(): Flow<List<GamePinEntity>>

    @Upsert
    suspend fun upsertPin(p: GamePinEntity)

    @Query("DELETE FROM game_pins WHERE sessionId = :sessionId AND item = :item")
    suspend fun deletePin(sessionId: String, item: String)

    // Dissociation
    @Query("DELETE FROM game_sessions") suspend fun clearSessions()
    @Query("DELETE FROM game_answers") suspend fun clearAnswers()
    @Query("DELETE FROM game_custom") suspend fun clearCustom()
    @Query("DELETE FROM game_wishes") suspend fun clearWishes()
    @Query("DELETE FROM game_seen") suspend fun clearSeen()
    @Query("DELETE FROM game_pins") suspend fun clearPins()
}
