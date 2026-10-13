package com.example.background_location_tracker

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.location.Location
import android.os.Build
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TrackerInstrumentedTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun sqlitePreservesFixesOrdersTiesAndLimitsHistory() {
        val name = "instrumented_locations.db"
        context.deleteDatabase(name)
        val database = LocationDatabase(context, name)
        try {
            assertNull(database.getLastLocation())
            assertTrue(database.getLocationHistory().isEmpty())
            repeat(55) { index ->
                val fix = Location("test").apply {
                    latitude = 12.0 + index / 1000.0
                    longitude = 77.0
                    time = 1700000000000L + index / 2
                    if (index != 54) accuracy = 3.5f
                }
                database.insertLocation(fix)
            }
            val records = database.getLocationHistory()
            assertEquals(50, records.size)
            assertEquals(55L, records.first()["id"])
            assertEquals(6L, records.last()["id"])
            assertNull(records.first()["accuracy"])
            assertEquals(3.5, records[1]["accuracy"])
            assertEquals(records.first(), database.getLastLocation())
        } finally { database.close() }
        val reopened = LocationDatabase(context, name)
        try { assertEquals(55L, reopened.getLastLocation()!!["id"]) }
        finally { reopened.close(); context.deleteDatabase(name) }
    }

    @Test fun visibleStartIsIdempotentAndStopCleansForegroundService() {
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        automation.grantRuntimePermission(context.packageName, Manifest.permission.ACCESS_COARSE_LOCATION)
        automation.grantRuntimePermission(context.packageName, Manifest.permission.ACCESS_FINE_LOCATION)
        if (Build.VERSION.SDK_INT >= 33) automation.grantRuntimePermission(context.packageName, Manifest.permission.POST_NOTIFICATIONS)
        assertTrue(TrackingState.locationEnabled(context))
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                repeat(2) {
                    val intent = Intent(activity, LocationTrackingService::class.java).setAction(LocationTrackingService.ACTION_START)
                    if (Build.VERSION.SDK_INT >= 26) activity.startForegroundService(intent) else activity.startService(intent)
                }
            }
            waitUntil { TrackingState.running }
            assertTrue(TrackingState.requested(context))
            assertFalse(TrackingState.starting)
            val notifications = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            assertTrue(notifications.activeNotifications.any { it.id == 1 })
            scenario.moveToState(androidx.lifecycle.Lifecycle.State.CREATED)
            assertTrue(TrackingState.running)
            scenario.moveToState(androidx.lifecycle.Lifecycle.State.RESUMED)
            scenario.onActivity { activity ->
                repeat(2) { activity.startService(Intent(activity, LocationTrackingService::class.java).setAction(LocationTrackingService.ACTION_STOP)) }
            }
            waitUntil { !TrackingState.running && !TrackingState.starting }
            assertFalse(TrackingState.requested(context))
            waitUntil { notifications.activeNotifications.none { it.id == 1 } }
        }
    }

    private fun waitUntil(condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 15000
        while (!condition() && System.currentTimeMillis() < deadline) Thread.sleep(100)
        assertTrue("Timed out waiting for tracker state", condition())
    }
}
