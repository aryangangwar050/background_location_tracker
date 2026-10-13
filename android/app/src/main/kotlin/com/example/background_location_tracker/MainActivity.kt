package com.example.background_location_tracker

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.EventChannel
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel

class MainActivity : FlutterActivity() {
    private var commandChannel: MethodChannel? = null
    private var eventChannel: EventChannel? = null
    private var permissionResult: MethodChannel.Result? = null
    private var permissionName: String? = null
    private var permissionCompletion: (() -> Unit)? = null
    private var resumed = false
    private val main = Handler(Looper.getMainLooper())

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        commandChannel = MethodChannel(flutterEngine.dartExecutor.binaryMessenger, "tracker/commands").also {
            it.setMethodCallHandler(::handleCommand)
        }
        eventChannel = EventChannel(flutterEngine.dartExecutor.binaryMessenger, "tracker/locations").also {
            it.setStreamHandler(object : EventChannel.StreamHandler {
                override fun onListen(arguments: Any?, events: EventChannel.EventSink) {
                    TrackingState.listener = { events.success(it) }
                    TrackingState.emitStatus(this@MainActivity)
                }
                override fun onCancel(arguments: Any?) { TrackingState.listener = null }
            })
        }
    }

    private fun handleCommand(call: MethodCall, result: MethodChannel.Result) {
        try {
            when (call.method) {
                "getTrackingStatus" -> result.success(TrackingState.status(this))
                "getLastLocation", "getLocationHistory" -> {
                    LocationDatabase.executor.execute {
                        try {
                            val database = LocationDatabase.get(applicationContext)
                            val value = if (call.method == "getLastLocation") database.getLastLocation()
                                else database.getLocationHistory()
                            main.post { result.success(value) }
                        } catch (error: Exception) {
                            main.post { result.error("DATABASE_ERROR", error.message, null) }
                        }
                    }
                }
                "startTracking" -> {
                    if (!resumed) {
                        result.error("APP_NOT_VISIBLE", "Open the app before starting tracking.", null)
                    } else if (!TrackingState.foregroundGranted(this)) {
                        result.error("LOCATION_PERMISSION_DENIED", "Grant location permission first.", null)
                    } else if (!TrackingState.locationEnabled(this)) {
                        result.error("LOCATION_DISABLED", "Enable device location services, then retry.", null)
                    } else if (TrackingState.running || TrackingState.starting) {
                        result.success(TrackingState.status(this))
                    } else {
                        val intent = Intent(this, LocationTrackingService::class.java).setAction(LocationTrackingService.ACTION_START)
                        if (Build.VERSION.SDK_INT >= 26) startForegroundService(intent) else startService(intent)
                        TrackingState.starting = true
                        TrackingState.setError(this, null)
                        result.success(TrackingState.status(this))
                    }
                }
                "stopTracking" -> {
                    TrackingState.setRequested(this, false)
                    TrackingState.setError(this, null)
                    if (TrackingState.running || TrackingState.starting) {
                        startService(Intent(this, LocationTrackingService::class.java).setAction(LocationTrackingService.ACTION_STOP))
                    }
                    result.success(TrackingState.status(this))
                }
                "requestForegroundPermission" -> requestPermission(arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
                    Manifest.permission.ACCESS_COARSE_LOCATION, result)
                "requestNotificationPermission" -> {
                    if (Build.VERSION.SDK_INT >= 33) requestPermission(arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                        Manifest.permission.POST_NOTIFICATIONS, result)
                    else result.success(TrackingState.status(this))
                }
                "requestBackgroundPermission" -> {
                    if (!TrackingState.foregroundGranted(this)) {
                        result.error("LOCATION_PERMISSION_DENIED", "Grant foreground location before background location.", null)
                    } else if (Build.VERSION.SDK_INT < 29 || TrackingState.granted(this, Manifest.permission.ACCESS_BACKGROUND_LOCATION)) {
                        result.success(TrackingState.status(this))
                    } else if (Build.VERSION.SDK_INT == 29) {
                        requestPermission(arrayOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION), Manifest.permission.ACCESS_BACKGROUND_LOCATION, result)
                    } else {
                        // Android 11+ requires a separate, user-directed Settings flow.
                        openSettings()
                        result.success(TrackingState.status(this))
                    }
                }
                "openAppSettings" -> { openSettings(); result.success(null) }
                "openLocationSettings" -> { startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)); result.success(null) }
                else -> result.notImplemented()
            }
        } catch (error: Exception) {
            if (call.method == "startTracking") {
                TrackingState.starting = false
                TrackingState.setRequested(this, false)
                TrackingState.setError(this, error.message)
            }
            result.error("NATIVE_ERROR", error.message ?: "Native operation failed.", mapOf("method" to call.method))
        }
    }

    private fun requestPermission(permissions: Array<String>, required: String, result: MethodChannel.Result) {
        if (TrackingState.granted(this, required)) { result.success(TrackingState.status(this)); return }
        if (!resumed) { result.error("APP_NOT_VISIBLE", "Open the app to request permissions.", null); return }
        if (permissionResult != null) { result.error("PERMISSION_BUSY", "Another permission request is pending.", null); return }
        permissionResult = result
        permissionName = required
        try { requestPermissions(permissions, PERMISSION_REQUEST) }
        catch (error: Exception) {
            permissionResult = null
            permissionName = null
            result.error("PERMISSION_ERROR", error.message, null)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST) {
            val permission = permissionName
            val completion = {
                val result = permissionResult
                permissionResult = null
                permissionName = null
                permissionCompletion = null
                if (permission != null && !TrackingState.granted(this, permission)) {
                    result?.error("PERMISSION_DENIED", "Permission denied. You can change it in app Settings.",
                        mapOf("permission" to permission, "permanentlyDenied" to !shouldShowRequestPermissionRationale(permission)))
                } else result?.success(TrackingState.status(this))
                Unit
            }
            // Reply only after the permission overlay closes and the activity is visible again.
            if (resumed) completion() else permissionCompletion = completion
        }
    }

    private fun openSettings() {
        startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
    }
    override fun onResume() {
        super.onResume()
        resumed = true
        permissionCompletion?.invoke()
        TrackingState.emitStatus(this)
    }
    override fun onPause() { resumed = false; super.onPause() }
    override fun cleanUpFlutterEngine(flutterEngine: FlutterEngine) {
        TrackingState.listener = null
        eventChannel?.setStreamHandler(null)
        commandChannel?.setMethodCallHandler(null)
        permissionResult?.error("ACTIVITY_DESTROYED", "Activity closed during permission request.", null)
        permissionResult = null
        permissionCompletion = null
        permissionName = null
        super.cleanUpFlutterEngine(flutterEngine)
    }
    companion object { private const val PERMISSION_REQUEST = 101 }
}
