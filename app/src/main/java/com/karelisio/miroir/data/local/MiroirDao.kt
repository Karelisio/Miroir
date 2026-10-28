package com.karelisio.miroir.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface MiroirDao {
    @Query("SELECT * FROM days WHERE day = :day")
    fun observeDay(day: String): Flow<DayEntity?>

    @Query("SELECT * FROM days WHERE day = :day")
    suspend fun day(day: String): DayEntity?

    @Query("SELECT * FROM days ORDER BY day DESC")
    fun observeDays(): Flow<List<DayEntity>>

    @Query("SELECT * FROM days ORDER BY day DESC")
    suspend fun days(): List<DayEntity>

    @Query("SELECT * FROM days WHERE myAnswer IS NOT NULL AND mySynced = 0")
    suspend fun pendingAnswers(): List<DayEntity>

    @Query("SELECT * FROM days WHERE myReaction IS NOT NULL AND myReactionSynced = 0")
    suspend fun pendingReactions(): List<DayEntity>

    @Upsert
    suspend fun upsertDay(day: DayEntity)

    @Upsert
    suspend fun upsertDays(days: List<DayEntity>)

    @Query("SELECT * FROM custom_questions ORDER BY createdAt DESC")
    fun observeCustom(): Flow<List<CustomQuestionEntity>>

    @Query("SELECT * FROM custom_questions")
    suspend fun custom(): List<CustomQuestionEntity>

    @Query("SELECT * FROM custom_questions WHERE id = :id")
    suspend fun customById(id: String): CustomQuestionEntity?

    @Query("SELECT * FROM custom_questions WHERE synced = 0")
    suspend fun pendingCustom(): List<CustomQuestionEntity>

    @Upsert
    suspend fun upsertCustom(q: CustomQuestionEntity)

    @Query("DELETE FROM days")
    suspend fun clearDays()

    @Query("DELETE FROM custom_questions")
    suspend fun clearCustom()
}
