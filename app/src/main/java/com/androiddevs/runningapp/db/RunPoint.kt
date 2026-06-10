package com.androiddevs.runningapp.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "run_points",
    foreignKeys = [
        ForeignKey(
            entity = Run::class,
            parentColumns = ["id"],
            childColumns = ["runId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("runId")]
)
data class RunPoint(
    val runId: Int,
    val latitude: Double,
    val longitude: Double,
    val segmentIndex: Int,
    val pointIndex: Int,
    val recordedAt: Long
) {
    @PrimaryKey(autoGenerate = true)
    var id: Int? = null
}
