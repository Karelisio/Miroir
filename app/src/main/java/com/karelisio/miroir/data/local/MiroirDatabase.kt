package com.karelisio.miroir.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [DayEntity::class, CustomQuestionEntity::class], version = 2, exportSchema = true)
abstract class MiroirDatabase : RoomDatabase() {
    abstract fun dao(): MiroirDao

    companion object {
        /** v2 : questions favorites. */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE days ADD COLUMN favorite INTEGER NOT NULL DEFAULT 0")
            }
        }

        fun create(context: Context): MiroirDatabase =
            Room.databaseBuilder(context, MiroirDatabase::class.java, "miroir.db")
                .addMigrations(MIGRATION_1_2)
                .build()
    }
}
