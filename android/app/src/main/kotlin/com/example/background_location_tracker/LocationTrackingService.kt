package com.example.background_location_tracker

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.Handler
import android.os.Looper
import com.google.android.gms.location.LocationAvailability
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import java.text.DateFormat
import java.util.Date
import java.util.Locale

class LocationTrackingService : Service() {
    private val fused by lazy { LocationServices.getFusedLocationProviderClient(this) }
    private val notifications by lazy { getSystemService(NotificationManager::class.java) }
    private val main = Handler(Looper.getMainLooper())
    private var subscribed = false
    private var generation = 0
    private var foreground = false

    private val callback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            if (!subscribed) return
            val session = generation
            result.locations.forEach { location ->
                LocationDatabase.executor.execute {
                    try {
                        val record = LocationDatabase.get(this@LocationTrackingService).insertLocation(location)
                        main.post {
                            if (subscribed && session == generation) {
                                val text = String.format(Locale.US, "%.6f, %.6f • %s", location.latitude,
                                    location.longitude, DateFormat.getTimeInstance().format(Date(location.time)))
                                notifications.notify(NOTIFICATION_ID, notification(text))
                                TrackingState.setError(this@LocationTrackingService, null)
                                TrackingState.emit(mapOf("type" to "location", "location" to record))
                            }
                        }
                    } catch (error: Exception) {
                        main.post { if (session == generation) fail("Could not save location: ${error.message}") }
                    }
                }
            }
        }
        override fun onLocationAvailability(availability: LocationAvailability) {
            if (subscribed) {
                TrackingState.setError(this@LocationTrackingService,
                    if (availability.isLocationAvailable) null else "Waiting for a location signal. Check device location settings.")
                TrackingState.emitStatus(this@LocationTrackingService)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= 26) {
            notifications.createNotificationChannel(NotificationChannel(CHANNEL_ID,
                "Location tracking", NotificationManager.IMPORTANCE_LOW))
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopTracking()
            stopSelf(startId)
            return START_NOT_STICKY
        }
        if (intent == null && !TrackingState.requested(this)) {
            stopSelf(startId)
            return START_NOT_STICKY
        }
        if (subscribed) return START_STICKY
        if (!TrackingState.foregroundGranted(this) || !TrackingState.locationEnabled(this)) {
            fail("Location permission and enabled location services are required.")
            return START_NOT_STICKY
        }
        try {
            val initial = notification("Waiting for the first location fix…")
            if (Build.VERSION.SDK_INT >= 29) {
                startForeground(NOTIFICATION_ID, initial, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
            } else startForeground(NOTIFICATION_ID, initial)
            foreground = true
            TrackingState.setRequested(this, true)
            TrackingState.setError(this, null)
            TrackingState.starting = true
            subscribed = true
            val session = ++generation
            val priority = if (TrackingState.granted(this, android.Manifest.permission.ACCESS_FINE_LOCATION))
                Priority.PRIORITY_HIGH_ACCURACY else Priority.PRIORITY_BALANCED_POWER_ACCURACY
            val request = LocationRequest.Builder(priority, 10000L)
                .setMinUpdateIntervalMillis(5000L).setWaitForAccurateLocation(false).build()
            fused.requestLocationUpdates(request, callback, Looper.getMainLooper())
                .addOnSuccessListener {
                    if (session == generation && subscribed) {
                        TrackingState.starting = false
                        TrackingState.running = true
                        TrackingState.emitStatus(this)
                    } else if (!subscribed) fused.removeLocationUpdates(callback)
                }.addOnFailureListener { error ->
                    if (session == generation) fail("Location updates failed: ${error.message}")
                }
            TrackingState.emitStatus(this)
        } catch (error: Exception) {
            fail("Unable to start location foreground service: ${error.message}")
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    private fun notification(text: String): Notification {
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val stop = PendingIntent.getService(this, 1, Intent(this, LocationTrackingService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val builder = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(this, CHANNEL_ID) else Notification.Builder(this)
        return builder.setSmallIcon(R.drawable.ic_tracking).setContentTitle("Location tracking active")
            .setContentText(text).setStyle(Notification.BigTextStyle().bigText(text))
            .setContentIntent(open).setOngoing(true).setOnlyAlertOnce(true)
            .addAction(Notification.Action.Builder(null, "Stop", stop).build()).build()
    }

    private fun stopTracking() {
        TrackingState.setRequested(this, false)
        cleanup()
        TrackingState.emitStatus(this)
    }
    private fun cleanup() {
        subscribed = false
        generation++
        fused.removeLocationUpdates(callback)
        TrackingState.running = false
        TrackingState.starting = false
        if (foreground) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            foreground = false
        }
    }
    private fun fail(message: String) {
        TrackingState.setError(this, message)
        stopTracking()
        stopSelf()
    }
    override fun onDestroy() {
        // Preserve user intent on system destruction; an explicit Stop clears it separately.
        cleanup()
        TrackingState.emitStatus(this)
        super.onDestroy()
    }
    override fun onBind(intent: Intent?): IBinder? = null
    // stopWithTask=false keeps this independent of the Flutter activity/task.
    companion object {
        const val ACTION_START = "tracker.START"
        const val ACTION_STOP = "tracker.STOP"
        private const val CHANNEL_ID = "location_tracking"
        private const val NOTIFICATION_ID = 1
    }
}
