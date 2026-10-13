package com.example.background_location_tracker

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import android.os.Handler
import android.os.Looper

object TrackingState {
    @Volatile var running = false
    @Volatile var starting = false
    var listener: ((Map<String, Any?>) -> Unit)? = null
    private val main = Handler(Looper.getMainLooper())
    private fun preferences(context: Context) = context.getSharedPreferences("tracking", Context.MODE_PRIVATE)
    fun requested(context: Context) = preferences(context).getBoolean("requested", false)
    fun setRequested(context: Context, value: Boolean) {
        preferences(context).edit().putBoolean("requested", value).apply()
    }
    fun setError(context: Context, message: String?) {
        preferences(context).edit().putString("error", message).apply()
    }
    fun granted(context: Context, permission: String) =
        context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
    fun foregroundGranted(context: Context) = granted(context, Manifest.permission.ACCESS_FINE_LOCATION) ||
        granted(context, Manifest.permission.ACCESS_COARSE_LOCATION)
    fun locationEnabled(context: Context): Boolean {
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return if (Build.VERSION.SDK_INT >= 28) manager.isLocationEnabled else
            manager.isProviderEnabled(LocationManager.GPS_PROVIDER) || manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    }
    fun status(context: Context): Map<String, Any?> = mapOf(
        "running" to running, "starting" to starting, "requested" to requested(context),
        "foregroundGranted" to foregroundGranted(context),
        "precise" to granted(context, Manifest.permission.ACCESS_FINE_LOCATION),
        "backgroundGranted" to (Build.VERSION.SDK_INT < 29 || granted(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION)),
        "notificationsGranted" to (Build.VERSION.SDK_INT < 33 || granted(context, Manifest.permission.POST_NOTIFICATIONS)),
        "locationEnabled" to locationEnabled(context),
        "error" to preferences(context).getString("error", null))
    fun emit(event: Map<String, Any?>) {
        main.post { listener?.invoke(event) }
    }
    fun emitStatus(context: Context) = emit(mapOf("type" to "status", "status" to status(context)))
}
