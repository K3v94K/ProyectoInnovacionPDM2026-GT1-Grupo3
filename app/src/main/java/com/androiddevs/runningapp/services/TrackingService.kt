package com.androiddevs.runningapp.services

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.NotificationManager.IMPORTANCE_LOW
import android.app.PendingIntent
import android.app.PendingIntent.FLAG_IMMUTABLE
import android.app.PendingIntent.FLAG_UPDATE_CURRENT
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.os.Build
import android.os.Looper
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.MutableLiveData
import com.androiddevs.runningapp.R
import com.androiddevs.runningapp.other.Constants
import com.androiddevs.runningapp.other.Constants.Companion.ACTION_PAUSE_SERVICE
import com.androiddevs.runningapp.other.Constants.Companion.ACTION_SHOW_TRACKING_FRAGMENT
import com.androiddevs.runningapp.other.Constants.Companion.ACTION_START_OR_RESUME_SERVICE
import com.androiddevs.runningapp.other.Constants.Companion.ACTION_STOP_SERVICE
import com.androiddevs.runningapp.other.Constants.Companion.EXTRA_GOAL_TYPE
import com.androiddevs.runningapp.other.Constants.Companion.EXTRA_GOAL_VALUE
import com.androiddevs.runningapp.other.Constants.Companion.FASTEST_LOCATION_UPDATE_INTERVAL
import com.androiddevs.runningapp.other.Constants.Companion.GOAL_REACHED_NOTIFICATION_ID
import com.androiddevs.runningapp.other.Constants.Companion.LOCATION_UPDATE_INTERVAL
import com.androiddevs.runningapp.other.Constants.Companion.MAX_ACCEPTABLE_LOCATION_ACCURACY
import com.androiddevs.runningapp.other.Constants.Companion.MIN_DISTANCE_BETWEEN_ROUTE_POINTS
import com.androiddevs.runningapp.other.Constants.Companion.NOTIFICATION_CHANNEL_ID
import com.androiddevs.runningapp.other.Constants.Companion.NOTIFICATION_CHANNEL_NAME
import com.androiddevs.runningapp.other.Constants.Companion.NOTIFICATION_ID
import com.androiddevs.runningapp.other.TrackingUtility
import com.androiddevs.runningapp.ui.MainActivity
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.Priority
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

    // Cronometro interno usado para publicar el tiempo visible de la carrera.
    private val timeRunInSeconds = MutableLiveData<Long>()

    private var isFirstRun = true
    private var serviceKilled = false
    private var isTimerEnabled = false
    private var lapTime = 0L
    private var timeRun = 0L
    private var timeStarted = 0L
    private var lastSecondTimestamp = 0L

    // Meta activa enviada por TrackingFragment al iniciar o reanudar la carrera.
    private var activeGoalType = ""
    private var activeGoalValue = 0f
    private var hasGoalReached = false

    companion object {
        // Estado observado por la interfaz para actualizar mapa, metricas y progreso.
        val timeRunInMillis = MutableLiveData<Long>()
        val isTracking = MutableLiveData<Boolean>()
        val pathPoints = MutableLiveData<Polylines>()
        val currentLocation = MutableLiveData<LatLng?>()
        val goalReached = MutableLiveData<Boolean>()
    }

    @Inject
    lateinit var baseNotificationBuilder: NotificationCompat.Builder

    @Inject
    lateinit var fusedLocationProviderClient: FusedLocationProviderClient

    @set:Inject
    var weight: Float = 80f

    private lateinit var curNotification: NotificationCompat.Builder

    override fun onCreate() {
        super.onCreate()
        curNotification = baseNotificationBuilder
        postInitialValues()

        isTracking.observe(this) { trackingState ->
            updateNotificationTrackingState(trackingState)
            updateLocationChecking(trackingState)
        }
    }

    private fun postInitialValues() {
        // Reinicia los valores publicos cada vez que el servicio se crea o se detiene.
        timeRunInMillis.postValue(0L)
        isTracking.postValue(false)
        pathPoints.postValue(mutableListOf())
        currentLocation.postValue(null)
        goalReached.postValue(false)
        timeRunInSeconds.postValue(0L)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.let {
            when (it.action) {
                ACTION_START_OR_RESUME_SERVICE -> {
                    updateGoalFromIntent(it)
                    if (isFirstRun) {
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
        activeGoalType = ""
        activeGoalValue = 0f
        hasGoalReached = false
        pauseService()
        postInitialValues()

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
                // Solicita ubicacion de alta precision para dibujar el recorrido deportivo.
                val request = LocationRequest.Builder(
                    Priority.PRIORITY_HIGH_ACCURACY,
                    LOCATION_UPDATE_INTERVAL
                )
                    .setMinUpdateIntervalMillis(FASTEST_LOCATION_UPDATE_INTERVAL)
                    .build()

                fusedLocationProviderClient.requestLocationUpdates(
                    request,
                    locationCallback,
                    Looper.getMainLooper()
                )
                fusedLocationProviderClient.lastLocation.addOnSuccessListener { location ->
                    updateCurrentLocation(location)
                }
            }
        } else {
            fusedLocationProviderClient.removeLocationUpdates(locationCallback)
        }
    }

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            super.onLocationResult(result)
            if (isTracking.value == true) {
                result.locations.forEach { location ->
                    addPathPoint(location)
                }
                checkGoalProgress()
            }
        }
    }

    private fun startTimer() {
        addEmptyPolyline()
        isTracking.postValue(true)
        timeStarted = System.currentTimeMillis()
        isTimerEnabled = true
        CoroutineScope(Dispatchers.Main).launch {
            while (isTracking.value == true) {
                lapTime = System.currentTimeMillis() - timeStarted
                timeRunInMillis.postValue(timeRun + lapTime)
                if ((timeRunInMillis.value ?: 0L) >= lastSecondTimestamp + 1000L) {
                    timeRunInSeconds.postValue((timeRunInSeconds.value ?: 0L) + 1)
                    lastSecondTimestamp += 1000L
                    checkGoalProgress()
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
            val pos = LatLng(it.latitude, it.longitude)
            currentLocation.postValue(pos)

            // La ubicacion actual centra el mapa, pero solo puntos confiables entran a la ruta.
            if (!isReliableRoutePoint(it)) {
                return
            }

            pathPoints.value?.apply {
                if (isNotEmpty()) {
                    last().add(pos)
                    pathPoints.postValue(this)
                }
            }
        }
    }

    private fun isReliableRoutePoint(location: Location): Boolean {
        // Descarta lecturas imprecisas para evitar distancia falsa por deriva del GPS.
        if (location.hasAccuracy() && location.accuracy > MAX_ACCEPTABLE_LOCATION_ACCURACY) {
            return false
        }

        val lastPoint = pathPoints.value
            ?.lastOrNull()
            ?.lastOrNull()
            ?: return true

        val distanceFromLastPoint = FloatArray(1)
        Location.distanceBetween(
            lastPoint.latitude,
            lastPoint.longitude,
            location.latitude,
            location.longitude,
            distanceFromLastPoint
        )

        // Descarta micro movimientos cuando el usuario esta quieto o bajo techo.
        return distanceFromLastPoint[0] >= MIN_DISTANCE_BETWEEN_ROUTE_POINTS
    }

    private fun updateCurrentLocation(location: Location?) {
        location?.let {
            currentLocation.postValue(LatLng(it.latitude, it.longitude))
        }
    }

    private fun addEmptyPolyline() = pathPoints.value?.apply {
        add(mutableListOf())
        pathPoints.postValue(this)
    } ?: pathPoints.postValue(mutableListOf(mutableListOf()))

    private fun updateGoalFromIntent(intent: Intent) {
        // La meta se fija al inicio para evitar cambios durante el entrenamiento activo.
        val goalType = intent.getStringExtra(EXTRA_GOAL_TYPE).orEmpty()
        val goalValue = intent.getFloatExtra(EXTRA_GOAL_VALUE, 0f)
        if (goalType.isNotBlank() && goalValue > 0f && activeGoalType.isBlank()) {
            activeGoalType = goalType
            activeGoalValue = goalValue
            hasGoalReached = false
            goalReached.postValue(false)
        }
    }

    private fun checkGoalProgress() {
        // Evita notificar mas de una vez la misma meta.
        if (hasGoalReached || activeGoalType.isBlank() || activeGoalValue <= 0f) {
            return
        }

        val currentValue = when (activeGoalType) {
            "DISTANCE" -> calculateCurrentDistanceInMeters() / 1000f
            "TIME" -> (timeRunInMillis.value ?: 0L) / 1000f / 60f
            "CALORIES" -> calculateCaloriesBurned(calculateCurrentDistanceInMeters()).toFloat()
            else -> return
        }

        if (currentValue >= activeGoalValue) {
            hasGoalReached = true
            goalReached.postValue(true)
            showGoalReachedNotification()
        }
    }

    private fun calculateCurrentDistanceInMeters(): Int {
        var distanceInMeters = 0
        for (polyline in pathPoints.value.orEmpty()) {
            distanceInMeters += TrackingUtility.calculatePolylineLength(polyline).toInt()
        }
        return distanceInMeters
    }

    private fun calculateCaloriesBurned(distanceInMeters: Int): Int {
        return ((distanceInMeters / 1000f) * weight).toInt()
    }

    private fun showGoalReachedNotification() {
        // Android 13+ requiere permiso explicito para publicar notificaciones.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        val goalName = when (activeGoalType) {
            "DISTANCE" -> getString(R.string.goal_type_distance)
            "TIME" -> getString(R.string.goal_type_time)
            "CALORIES" -> getString(R.string.goal_type_calories)
            else -> getString(R.string.goal_notification_title)
        }

        val notificationManager =
            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            createNotificationChannel(notificationManager)
        }

        val notification = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_directions_run_black_24dp)
            .setContentTitle(getString(R.string.goal_notification_title))
            .setContentText(getString(R.string.goal_reached_format, goalName))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(getMainActivityPendingIntent())
            .build()

        notificationManager.notify(GOAL_REACHED_NOTIFICATION_ID, notification)
    }

    private fun startForegroundService() {
        Timber.d("TrackingService started.")

        val notificationManager =
            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            createNotificationChannel(notificationManager)
        }

        // Mantiene una notificacion persistente mientras el seguimiento esta activo.
        startForeground(NOTIFICATION_ID, curNotification.build())
        startTimer()
        isTracking.postValue(true)

        timeRunInSeconds.observe(this) { seconds ->
            if (!serviceKilled) {
                val notification = curNotification
                    .setContentText(TrackingUtility.getFormattedStopWatchTime(seconds * 1000L))
                notificationManager.notify(NOTIFICATION_ID, notification.build())
            }
        }
    }

    private fun updateNotificationTrackingState(isTracking: Boolean) {
        // Cambia la accion de la notificacion entre pausar y reanudar.
        val notificationActionText = if (isTracking) {
            getString(R.string.stop_text)
        } else {
            getString(R.string.start_text)
        }

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

        try {
            curNotification.javaClass.getDeclaredField("mActions").apply {
                isAccessible = true
                set(curNotification, ArrayList<NotificationCompat.Action>())
            }
        } catch (e: Exception) {
            Timber.e(e)
        }

        if (!serviceKilled) {
            curNotification = baseNotificationBuilder
                .addAction(R.drawable.ic_pause_black_24dp, notificationActionText, pendingIntent)
            notificationManager.notify(NOTIFICATION_ID, curNotification.build())
        }
    }

    private fun getMainActivityPendingIntent(): PendingIntent {
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            FLAG_UPDATE_CURRENT or FLAG_IMMUTABLE
        } else {
            FLAG_UPDATE_CURRENT
        }
        return PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                action = ACTION_SHOW_TRACKING_FRAGMENT
            },
            flags
        )
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun createNotificationChannel(notificationManager: NotificationManager) {
        val channel = NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            NOTIFICATION_CHANNEL_NAME,
            IMPORTANCE_LOW
        )
        notificationManager.createNotificationChannel(channel)
    }
}
