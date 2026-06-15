package com.androiddevs.runningapp.other

import android.graphics.Color
import android.location.LocationManager
import com.github.mikephil.charting.data.LineDataSet

class Constants {

    companion object {
        const val MAP_VIEW_BUNDLE_KEY = "MapViewBundleKey"

        const val REQUEST_CODE_LOCATION_PERMISSION = 0

        // Base de datos
        const val DATABASE_NAME = "running_db"

        // Opciones de seguimiento GPS
        const val LOCATION_UPDATE_INTERVAL = 5000L
        const val FASTEST_LOCATION_UPDATE_INTERVAL = 2000L
        const val MAX_ACCEPTABLE_LOCATION_ACCURACY = 25f
        const val MIN_DISTANCE_BETWEEN_ROUTE_POINTS = 4f

        // Opciones del mapa
        const val POLYLINE_COLOR = Color.RED
        const val POLYLINE_WIDTH = 8f
        const val MAP_ZOOM = 15f

        // Temporizador
        const val TIMER_UPDATE_INTERVAL = 50L

        // Grafica de lineas
        val LINE_DATA_MODE = LineDataSet.Mode.CUBIC_BEZIER

        // Vista del mapa
        const val MAP_VIEW_HEIGHT_IN_DP = 200f

        // Notificaciones
        const val NOTIFICATION_CHANNEL_ID = "tracking_channel"
        const val NOTIFICATION_CHANNEL_NAME = "Tracking"
        const val NOTIFICATION_ID = 1
        const val GOAL_REACHED_NOTIFICATION_ID = 2

        // Preferencias locales del usuario
        const val SHARED_PREFERENCES_NAME = "sharedPref"
        const val KEY_NAME = "KEY_NAME"
        const val KEY_WEIGHT = "KEY_WEIGHT"
        const val KEY_FIRST_TIME_TOGGLE = "KEY_FIRST_TIME_TOGGLE"

        // Acciones enviadas al servicio de seguimiento
        const val ACTION_SHOW_TRACKING_FRAGMENT = "ACTION_SHOW_TRACKING_FRAGMENT"
        const val ACTION_START_OR_RESUME_SERVICE = "ACTION_START_SERVICE"
        const val ACTION_PAUSE_SERVICE = "ACTION_PAUSE_SERVICE"
        const val ACTION_STOP_SERVICE = "ACTION_STOP_SERVICE"
        const val EXTRA_GOAL_TYPE = "EXTRA_GOAL_TYPE"
        const val EXTRA_GOAL_VALUE = "EXTRA_GOAL_VALUE"
    }
}
