package com.androiddevs.runningapp.di

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build // 🌟 NUEVO: Para validar la versión de Android
import androidx.core.app.NotificationCompat
import com.androiddevs.runningapp.R
import com.androiddevs.runningapp.other.Constants
import com.androiddevs.runningapp.ui.MainActivity
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices // 🌟 NUEVO: Requerido para la API moderna
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ServiceComponent
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.scopes.ServiceScoped

/**
 * ServiceModule, provides dependencies for the TrackingService
 */
@Module
@InstallIn(ServiceComponent::class)
object ServiceModule {

    @ServiceScoped
    @Provides
    fun providesFusedLocationProviderClient(
        @ApplicationContext context: Context
    ) = LocationServices.getFusedLocationProviderClient(context) // 🌟 CORREGIDO: Sintaxis moderna de Google

    @ServiceScoped
    @Provides
    fun provideBaseNotificationBuilder(
        @ApplicationContext context: Context,
        pendingIntent: PendingIntent
    ) = NotificationCompat.Builder(context, Constants.NOTIFICATION_CHANNEL_ID)
        .setAutoCancel(false)
        .setOngoing(true)
        .setSmallIcon(R.drawable.ic_directions_run_black_24dp)
        .setContentTitle("Running App")
        .setContentText("00:00:00")
        .setContentIntent(pendingIntent)

    @ServiceScoped
    @Provides
    fun provideActivityPendingIntent(
        @ApplicationContext context: Context
    ): PendingIntent {
        // 🌟 CORREGIDO: Bandera inmutable obligatoria para Android 12, 13 y 14
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }

        return PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).apply {
                action = Constants.ACTION_SHOW_TRACKING_FRAGMENT
            },
            flags
        )
    }
}