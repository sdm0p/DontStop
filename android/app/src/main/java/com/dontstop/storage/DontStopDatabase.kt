package com.dontstop.storage

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.time.LocalDate

data class DailyUsage(val packageName: String, val activeMs: Long, val swipes: Long) {
    val minutes: Double get() = activeMs / 60_000.0
}

class DontStopDatabase(context: Context) : SQLiteOpenHelper(context, "dontstop.db", null, 1) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""CREATE TABLE daily_usage(day TEXT NOT NULL, package_name TEXT NOT NULL,
            active_ms INTEGER NOT NULL DEFAULT 0, swipes INTEGER NOT NULL DEFAULT 0,
            PRIMARY KEY(day, package_name))""")
        db.execSQL("""CREATE TABLE delivered_milestones(day TEXT NOT NULL, scope TEXT NOT NULL,
            metric TEXT NOT NULL, milestone INTEGER NOT NULL,
            PRIMARY KEY(day, scope, metric, milestone))""")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

    fun add(packageName: String, activeMs: Long = 0, swipes: Long = 0) {
        if (activeMs <= 0 && swipes <= 0) return
        writableDatabase.execSQL(
            """INSERT INTO daily_usage(day,package_name,active_ms,swipes) VALUES(?,?,?,?)
               ON CONFLICT(day,package_name) DO UPDATE SET
               active_ms=active_ms+excluded.active_ms, swipes=swipes+excluded.swipes""",
            arrayOf(LocalDate.now().toString(), packageName, activeMs, swipes)
        )
        prune()
    }

    fun today(): List<DailyUsage> = readDay(LocalDate.now())

    fun history(days: Long = 30): Map<String, List<DailyUsage>> {
        val result = linkedMapOf<String, MutableList<DailyUsage>>()
        readableDatabase.rawQuery(
            "SELECT day,package_name,active_ms,swipes FROM daily_usage WHERE day>=? ORDER BY day DESC",
            arrayOf(LocalDate.now().minusDays(days - 1).toString())
        ).use { c ->
            while (c.moveToNext()) result.getOrPut(c.getString(0)) { mutableListOf() }
                .add(DailyUsage(c.getString(1), c.getLong(2), c.getLong(3)))
        }
        return result
    }

    private fun readDay(day: LocalDate): List<DailyUsage> {
        val rows = mutableListOf<DailyUsage>()
        readableDatabase.rawQuery(
            "SELECT package_name,active_ms,swipes FROM daily_usage WHERE day=?",
            arrayOf(day.toString())
        ).use { c -> while (c.moveToNext()) rows += DailyUsage(c.getString(0), c.getLong(1), c.getLong(2)) }
        return rows
    }

    fun milestoneDelivered(scope: String, metric: String, milestone: Int): Boolean {
        val values = ContentValues().apply {
            put("day", LocalDate.now().toString()); put("scope", scope)
            put("metric", metric); put("milestone", milestone)
        }
        return writableDatabase.insertWithOnConflict(
            "delivered_milestones", null, values, SQLiteDatabase.CONFLICT_IGNORE
        ) == -1L
    }

    fun clearHistory() = writableDatabase.delete("daily_usage", "day<?", arrayOf(LocalDate.now().toString()))

    fun resetToday(metric: String) {
        val column = when (metric) {
            "time" -> "active_ms"
            "swipes" -> "swipes"
            else -> error("Unknown usage metric: $metric")
        }
        writableDatabase.beginTransaction()
        try {
            writableDatabase.execSQL(
                "UPDATE daily_usage SET $column=0 WHERE day=?",
                arrayOf(LocalDate.now().toString())
            )
            writableDatabase.delete(
                "delivered_milestones",
                "day=? AND metric=?",
                arrayOf(LocalDate.now().toString(), metric)
            )
            writableDatabase.setTransactionSuccessful()
        } finally {
            writableDatabase.endTransaction()
        }
    }

    private fun prune() {
        writableDatabase.delete("daily_usage", "day<?", arrayOf(LocalDate.now().minusDays(29).toString()))
        writableDatabase.delete("delivered_milestones", "day<?", arrayOf(LocalDate.now().toString()))
    }
}
