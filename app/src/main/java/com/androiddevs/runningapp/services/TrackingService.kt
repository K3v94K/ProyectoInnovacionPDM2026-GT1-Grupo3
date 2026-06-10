package com.androiddevs.runningapp.services

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.NotificationManager.IMPORTANCE_LOW
import android.app.PendingIntent
import android.app.PendingIntent.FLAG_UPDATE_CURRENT
import android.app.PendingIntent.FLAG_IMMUTABLE // 🌟 NUEVO: Requerido para Android 12+
import android.content.Context
import android.content.Intent
import android.location.Location
import android.os.Build
import android.os.Looper
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.MutableLiveData
// 🌟 CORREGIDO: Se eliminó el import obsoleto de androidx.lifecycle.observe
import com.androiddevs.runningapp.R
import com.androiddevs.runningapp.other.Constants
import com.androiddevs.runningapp.other.Constants.Companion.ACTION_PAUSE_SERVICE
import com.androiddevs.runningapp.other.Constants.Companion.ACTION_SHOW_TRACKING_FRAGMENT
import com.androiddevs.runningapp.other.Constants.Companion.ACTION_START_OR_RESUME_SERVICE
import com.androiddevs.runningapp.other.Constants.Companion.ACTION_STOP_SERVICE
import com.androiddevs.runningapp.other.Constants.Companion.FASTEST_LOCATION_UPDATE_INTERVAL
import com.androiddevs.runningapp.other.Constants.Companion.LOCATION_UPDATE_INTERVAL
import com.androiddevs.runningapp.other.Constants.Companion.NOTIFICATION_CHANNEL_ID
import com.androiddevs.runningapp.other.Constants.Companion.NOTIFICATION_CHANNEL_NAME
import com.androiddevs.runningapp.other.Constants.Companion.NOTIFICATION_ID
import com.androiddevs.runningapp.other.TrackingUtility
import com.androiddevs.runningapp.ui.MainActivity
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.Priority // 🌟 NUEVO: Requerido por la API moderna de mapas
import com.google.android.gms.maps.model.LatLng
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

typealias Polyline = MutableList<LatLng>
typealias Polylines = MutableList<Polyline>

@AndroidEntryPoint
class TrackingService : LifecycleService() {

    private val timeRunInSeconds = MutableLiveData<Long>()

    private var isFirstRun = true
    private var serviceKilled = false

    companion object {
        val timeRunInMillis = MutableLiveData<Long>()
        val isTracking = MutableLiveData<Boolean>()
        val pathPoints = MutableLiveData<Polylines>()
    }

    @Inject
    lateinit var baseNotificationBuilder: NotificationCompat.Builder

    private lateinit var curNotification: NotificationCompat.Builder

    @Inject
    lateinit var fusedLocationProviderClient: FusedLocationProviderClient

    override fun onCreate() {
        super.onCreate()
        curNotification = baseNotificationBuilder
        postInitialValues()

        // 🌟 CORREGIDO: Sintaxis limpia nativa de LiveData
        isTracking.observe(this) { trackingState ->
            updateNotificationTrackingState(trackingState)
            updateLocationChecking(trackingState)
        }
    }

    private fun postInitialValues() {
        timeRunInMillis.postValue(0L)
        isTracking.postValue(false)
        pathPoints.postValue(mutableListOf())
        timeRunInSeconds.postValue(0L)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.let {
            when (it.action) {
                ACTION_START_OR_RESUME_SERVICE -> {
                    if(isFirstRun) {
                        startForegroundService()
                        isFirstRun = false
                        serviceKilled = false
                    } else {
                        startTimer()
                    }
                }
                ACTION_PAUSE_SERVICE -> {
                    Timber.d("Paused Service")
                    pauseService()
                }
                ACTION_STOP_SERVICE -> {
                    Timber.d("Stopped service.")
                    killService()
                }
            }
        }
        return super.onStartCommand(intent, flags, startId)
    }

    private fun killService() {
        serviceKilled = true
        isFirstRun = true
        pauseService()
        postInitialValues()

        // 🌟 CORREGIDO: Soporte de detención retrocompatible para Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        stopSelf()
    }

    @SuppressLint("MissingPermission")
    private fun updateLocationChecking(isTracking: Boolean) {
        if (isTracking) {
            if (TrackingUtility.hasLocationPermissions(this)) {
                // 🌟 CORREGIDO: Inicialización moderna de LocationRequest compatible con Gradle 8
                val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, LOCATION_UPDATE_INTERVAL)
                    .setMinUpdateIntervalMillis(FASTEST_LOCATION_UPDATE_INTERVAL)
                    .build()

                fusedLocationProviderClient.requestLocationUpdates(request, locationCallback, Looper.getMainLooper())
            }
        } else {
            fusedLocationProviderClient.removeLocationUpdates(locationCallback)
        }
    }

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) { // 🌟 CORREGIDO: Quitamos el tipo nulable que fallaba en compilación externa
            super.onLocationResult(result)
            if(isTracking.value == true) {
                result.locations.let { locations ->
                    for(location in locations) {
                        addPathPoint(location)
                    }
                }
            }
        }
    }

    private var isTimerEnabled = false
    private var lapTime = 0L
    private var timeRun = 0L
    private var timeStarted = 0L
    private var lastSecondTimestamp = 0L

    private fun startTimer() {
        addEmptyPolyline()
        isTracking.postValue(true)
        timeStarted = System.currentTimeMillis()
        isTimerEnabled = true
        CoroutineScope(Dispatchers.Main).launch {
            while (isTracking.value == true) {
                lapTime = System.currentTimeMillis() - timeStarted
                timeRunInMillis.postValue(timeRun + lapTime)
                if (timeRunInMillis.value!! >= lastSecondTimestamp + 1000L) {
                    timeRunInSeconds.postValue((timeRunInSeconds.value ?: 0L) + 1)
                    lastSecondTimestamp += 1000L
                }
                delay(Constants.TIMER_UPDATE_INTERVAL)
            }
            timeRun += lapTime
        }
    }

    private fun pauseService() {
        isTimerEnabled = false
        isTracking.postValue(false)
    }

    private fun addPathPoint(location: Location?) {
        location?.let {
            val pos = LatLng(location.latitude, location.longitude)
            pathPoints.value?.apply {
                if (isNotEmpty()) {
                    last().add(pos)
                    pathPoints.postValue(this)
                }
            }
        }
    }

    private fun addEmptyPolyline() = pathPoints.value?.apply {
        add(mutableListOf())
        pathPoints.postValue(this)
    } ?: pathPoints.postValue(mutableListOf(mutableListOf()))

    private fun startForegroundService() {
        Timber.d("TrackingService started.")

        val notificationManager =
            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            createNotificationChannel(notificationManager)
        }

        startForeground(NOTIFICATION_ID, curNotification.build())
        startTimer()
        isTracking.postValue(true)

        timeRunInSeconds.observe(this) { seconds ->
            if(!serviceKilled) {
                val notification = curNotification
                    .setContentText(TrackingUtility.getFormattedStopWatchTime(seconds * 1000L))
                notificationManager.notify(NOTIFICATION_ID, notification.build())
            }
        }
    }

    private fun updateNotificationTrackingState(isTracking: Boolean) {
        val notificationActionText = if (isTracking) "Pause" else "Resume"

        // 🌟 CORREGIDO: Se agregó FLAG_IMMUTABLE para evitar el crash de seguridad en Android 12/13/14
        val pendingIntentFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            FLAG_UPDATE_CURRENT or FLAG_IMMUTABLE
        } else {
            FLAG_UPDATE_CURRENT
        }

        val pendingIntent = if (isTracking) {
            val pauseIntent = Intent(this, TrackingService::class.java).apply {
                action = ACTION_PAUSE_SERVICE
            }
            PendingIntent.getService(this, 1, pauseIntent, pendingIntentFlags)
        } else {
            val resumeIntent = Intent(this, TrackingService::class.java).apply {
                action = ACTION_START_OR_RESUME_SERVICE
            }
            PendingIntent.getService(this, 2, resumeIntent, pendingIntentFlags)
        }

        val notificationManager =
            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Limpieza de acciones anteriores usando reflexión de forma segura
        try {
            curNotification.javaClass.getDeclaredField("mActions").apply {
                isAccessible = true
                set(curNotification, ArrayList<NotificationCompat.Action>())
            }
        } catch (e: Exception) {
            Timber.e(e)
        }

        if(!serviceKilled) {
            curNotification = baseNotificationBuilder
                .addAction(R.drawable.ic_pause_black_24dp, notificationActionText, pendingIntent)
            notificationManager.notify(NOTIFICATION_ID, curNotification.build())
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun createNotificationChannel(notificationManager: NotificationManager) {
        val channel = NotificationChannel(NOTIFICATION_CHANNEL_ID, NOTIFICATION_CHANNEL_NAME, IMPORTANCE_LOW)
        notificationManager.createNotificationChannel(channel)
    }
}