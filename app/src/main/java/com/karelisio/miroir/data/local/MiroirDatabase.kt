package com.karelisio.miroir.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.karelisio.miroir.games.data.GameAnswerEntity
import com.karelisio.miroir.games.data.GameCustomEntity
import com.karelisio.miroir.games.data.GameDao
import com.karelisio.miroir.games.data.GameMatchEntity
import com.karelisio.miroir.games.data.GamePinEntity
import com.karelisio.miroir.games.data.GameSeenEntity
import com.karelisio.miroir.games.data.GameSessionEntity
import com.karelisio.miroir.games.data.GameWishEntity

@Database(
    entities = [
        DayEntity::class, CustomQuestionEntity::class,
        GameSessionEntity::class, GameAnswerEntity::class, GameCustomEntity::class, GameWishEntity::class,
        GameMatchEntity::class, GameSeenEntity::class, GamePinEntity::class,
    ],
    version = 3,
    exportSchema = true,
)
abstract class MiroirDatabase : RoomDatabase() {
    abstract fun dao(): MiroirDao
    abstract fun games(): GameDao

    companion object {
        /** v2 : questions favorites. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE days ADD COLUMN favorite INTEGER NOT NULL DEFAULT 0")
            }
        }

        /** v3 : « De l'autre côté » (jeux à deux). Tables nouvelles uniquement : rien n'est touché dans l'existant. */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE `game_sessions` (`id` TEXT NOT NULL, `kind` TEXT NOT NULL, `reveal` TEXT NOT NULL, `createdBy` TEXT NOT NULL, `payload` TEXT NOT NULL, `status` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, `serverRev` INTEGER NOT NULL, `synced` INTEGER NOT NULL, `statusDirty` INTEGER NOT NULL, `notify` INTEGER NOT NULL, PRIMARY KEY(`id`))")
                db.execSQL("CREATE TABLE `game_answers` (`sessionId` TEXT NOT NULL, `member` TEXT NOT NULL, `item` TEXT NOT NULL, `payload` TEXT, `at` INTEGER NOT NULL, `synced` INTEGER NOT NULL, `notify` INTEGER NOT NULL, PRIMARY KEY(`sessionId`, `member`, `item`))")
                db.execSQL("CREATE TABLE `game_custom` (`id` TEXT NOT NULL, `author` TEXT NOT NULL, `payload` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `deleted` INTEGER NOT NULL, `synced` INTEGER NOT NULL, PRIMARY KEY(`id`))")
                db.execSQL("CREATE TABLE `game_wishes` (`itemId` TEXT NOT NULL, `answer` INTEGER NOT NULL, `at` INTEGER NOT NULL, `synced` INTEGER NOT NULL, PRIMARY KEY(`itemId`))")
                db.execSQL("CREATE TABLE `game_matches` (`itemId` TEXT NOT NULL, `kind` TEXT NOT NULL, `at` INTEGER NOT NULL, `seen` INTEGER NOT NULL, PRIMARY KEY(`itemId`))")
                db.execSQL("CREATE TABLE `game_seen` (`itemId` TEXT NOT NULL, `at` INTEGER NOT NULL, PRIMARY KEY(`itemId`))")
                db.execSQL("CREATE TABLE `game_pins` (`sessionId` TEXT NOT NULL, `item` TEXT NOT NULL, `question` TEXT NOT NULL, `answer` TEXT NOT NULL, `author` TEXT NOT NULL, `at` INTEGER NOT NULL, PRIMARY KEY(`sessionId`, `item`))")
            }
        }

        fun create(context: Context): MiroirDatabase =
            Room.databaseBuilder(context, MiroirDatabase::class.java, "miroir.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .build()
    }
}
