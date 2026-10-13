package com.example.background_location_tracker

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.location.Location
import java.util.concurrent.Executors

class LocationDatabase internal constructor(context: Context, name: String = "locations.db") :
    SQLiteOpenHelper(context.applicationContext, name, null, 1) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""CREATE TABLE locations (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            latitude REAL NOT NULL, longitude REAL NOT NULL,
            accuracy REAL, timestamp INTEGER NOT NULL)""")
        db.execSQL("CREATE INDEX locations_time ON locations(timestamp DESC, id DESC)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Version 1 has no migrations. Never silently discard a recorded route.
        error("Unsupported database migration: $oldVersion -> $newVersion")
    }

    fun insertLocation(location: Location): Map<String, Any?> {
        val values = ContentValues().apply {
            put("latitude", location.latitude)
            put("longitude", location.longitude)
            if (location.hasAccuracy()) put("accuracy", location.accuracy.toDouble())
            else putNull("accuracy")
            put("timestamp", location.time)
        }
        val id = writableDatabase.insertOrThrow("locations", null, values)
        return mapOf("id" to id, "latitude" to location.latitude,
            "longitude" to location.longitude,
            "accuracy" to if (location.hasAccuracy()) location.accuracy.toDouble() else null,
            "timestamp" to location.time)
    }

    fun getLastLocation(): Map<String, Any?>? = getLocationHistory(1).firstOrNull()

    fun getLocationHistory(limit: Int = 50): List<Map<String, Any?>> {
        val records = mutableListOf<Map<String, Any?>>()
        readableDatabase.query("locations", null, null, null, null, null,
            "timestamp DESC, id DESC", limit.coerceIn(1, 50).toString()).use { cursor ->
            while (cursor.moveToNext()) records.add(cursor.record())
        }
        return records
    }

    private fun Cursor.record(): Map<String, Any?> = mapOf(
        "id" to getLong(getColumnIndexOrThrow("id")),
        "latitude" to getDouble(getColumnIndexOrThrow("latitude")),
        "longitude" to getDouble(getColumnIndexOrThrow("longitude")),
        "accuracy" to getColumnIndexOrThrow("accuracy").let { if (isNull(it)) null else getDouble(it) },
        "timestamp" to getLong(getColumnIndexOrThrow("timestamp")))

    companion object {
        // One queue serializes Flutter reads and service writes without blocking the main thread.
        val executor = Executors.newSingleThreadExecutor()
        @Volatile private var instance: LocationDatabase? = null
        fun get(context: Context): LocationDatabase = instance ?: synchronized(this) {
            instance ?: LocationDatabase(context).also { instance = it }
        }
    }
}
