package com.androiddevs.runningapp.repositories

import com.androiddevs.runningapp.db.Run
import com.androiddevs.runningapp.db.RunDao
import com.androiddevs.runningapp.db.RunPoint
import javax.inject.Inject

class MainRepository @Inject constructor(
    val runDao: RunDao
) {
    suspend fun insertRun(run: Run) = runDao.insertRun(run)

    suspend fun insertRunWithPoints(run: Run, points: List<RunPoint>) {
        val runId = runDao.insertRun(run).toInt()
        if (points.isNotEmpty()) {
            runDao.insertRunPoints(points.map { it.copy(runId = runId) })
        }
    }

    suspend fun deleteRun(run: Run) = runDao.deleteRun(run)

    fun getAllRunsSortedByDate() = runDao.getAllRunsSortedByDate()

    fun getAllRunsSortedByTimeInMillis() = runDao.getAllRunsSortedByTimeInMillis()

    fun getAllRunsSortedByDistance() = runDao.getAllRunsSortedByDistance()

    fun getAllRunsSortedByCaloriesBurned() = runDao.getAllRunsSortedByCaloriesBurned()

    fun getAllRunsSortedByAvgSpeed() = runDao.getAllRunsSortedByAvgSpeed()

    fun getTotalDistance() = runDao.getTotalDistance()

    fun getTotalTimeInMillis() = runDao.getTotalTimeInMillis()

    fun getTotalAvgSpeed() = runDao.getTotalAvgSpeed()

    fun getTotalCaloriesBurned() = runDao.getTotalCaloriesBurned()

    fun getRunPoints(runId: Int) = runDao.getRunPoints(runId)
}
