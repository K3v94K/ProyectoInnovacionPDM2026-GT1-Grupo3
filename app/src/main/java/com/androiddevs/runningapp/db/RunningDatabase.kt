package com.androiddevs.runningapp.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

/**
 * Base de datos local Room para carreras y puntos GPS del historial.
 */
@Database(
    entities = [Run::class, RunPoint::class],
    version = 3,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class RunningDatabase : RoomDatabase() {

    abstract fun getRunDao(): RunDao
}
