package com.karelisio.miroir.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [DayEntity::class, CustomQuestionEntity::class], version = 1, exportSchema = true)
abstract class MiroirDatabase : RoomDatabase() {
    abstract fun dao(): MiroirDao

    companion object {
        fun create(context: Context): MiroirDatabase =
            Room.databaseBuilder(context, MiroirDatabase::class.java, "miroir.db").build()
    }
}
