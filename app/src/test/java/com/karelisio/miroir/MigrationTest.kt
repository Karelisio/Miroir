package com.karelisio.miroir

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.karelisio.miroir.data.local.MiroirDatabase
import com.karelisio.miroir.games.data.GameSessionEntity
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Base créée exactement comme en v2 (schéma exporté par Room), puis ouverte en v3 avec la migration. */
@RunWith(AndroidJUnit4::class)
class MigrationTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val name = "migration-test.db"

    @Test
    fun migration_2_vers_3_conserve_l_historique_et_ajoute_les_jeux() = runBlocking {
        context.deleteDatabase(name)
        val schema = JSONObject(File("schemas/com.karelisio.miroir.data.local.MiroirDatabase/2.json").readText()).getJSONObject("database")
        val entities = schema.getJSONArray("entities")
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context).name(name).callback(object : SupportSQLiteOpenHelper.Callback(2) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    for (i in 0 until entities.length()) {
                        val e = entities.getJSONObject(i)
                        db.execSQL(e.getString("createSql").replace("\${TABLE_NAME}", e.getString("tableName")))
                    }
                    val setup = schema.getJSONArray("setupQueries")
                    for (i in 0 until setup.length()) db.execSQL(setup.getString(i))
                }

                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
            }).build(),
        )
        // Une ligne par table, toutes colonnes renseignées selon leur type.
        val db2 = helper.writableDatabase
        for (i in 0 until entities.length()) {
            val e = entities.getJSONObject(i)
            val values = ContentValues()
            val fields = e.getJSONArray("fields")
            for (f in 0 until fields.length()) {
                val field = fields.getJSONObject(f)
                val column = field.getString("columnName")
                when (field.getString("affinity")) {
                    "INTEGER" -> values.put(column, 0)
                    else -> values.put(column, if (column == "day") "2026-10-28" else "x$column")
                }
            }
            db2.insert(e.getString("tableName"), SQLiteDatabase.CONFLICT_REPLACE, values)
        }
        helper.close()

        val db = Room.databaseBuilder(context, MiroirDatabase::class.java, name)
            .addMigrations(MiroirDatabase.MIGRATION_1_2, MiroirDatabase.MIGRATION_2_3)
            .allowMainThreadQueries()
            .build()
        assertEquals("2026-10-28", db.dao().days().single().day)
        assertEquals(1, db.dao().custom().size)
        db.games().upsertSession(GameSessionEntity("s1", "duel", "all", "A", "{}", createdAt = 1, updatedAt = 1))
        assertEquals(1, db.games().sessions().size)
        assertEquals(0, db.games().answers().size)
        db.close()
        context.deleteDatabase(name)
        Unit
    }
}
