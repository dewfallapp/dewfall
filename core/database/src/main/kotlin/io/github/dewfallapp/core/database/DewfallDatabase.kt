package io.github.dewfallapp.core.database

import android.content.Context
import androidx.room3.ColumnTypeConverters
import androidx.room3.Database
import androidx.room3.Room
import androidx.room3.RoomDatabase
import androidx.sqlite.driver.AndroidSQLiteDriver

/** The name of the database file in the app's own storage. */
const val DATABASE_NAME = "dewfall.db"

/**
 * Dewfall's database. It stays at version 1 until the first public release, so a schema change
 * before then needs no migration. See docs/architecture.md#database-tables.
 */
@Database(
    entities = [ChannelEntity::class, VideoEntity::class, WatchStateEntity::class],
    version = 1,
    exportSchema = true,
)
@ColumnTypeConverters(DatabaseConverters::class)
abstract class DewfallDatabase : RoomDatabase() {
    abstract fun channelDao(): ChannelDao

    abstract fun videoDao(): VideoDao

    abstract fun watchStateDao(): WatchStateDao
}

/**
 * Builds the database in [DATABASE_NAME], with AndroidSQLiteDriver, the SQLite that comes with the
 * phone's Android version. Build one and keep it for the app's whole run.
 */
fun buildDewfallDatabase(context: Context): DewfallDatabase =
    Room.databaseBuilder<DewfallDatabase>(context.applicationContext, DATABASE_NAME)
        .setDriver(AndroidSQLiteDriver())
        .build()
