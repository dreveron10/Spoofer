package com.diego.mocklocation

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.location.Criteria
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import java.util.Locale

/**
 * Foreground service that pushes a fixed location into Android's test
 * providers once per second. Requires this app to be selected under
 * Developer options > Select mock location app.
 */
class MockLocationService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var locationManager: LocationManager
    private val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
    private val activeProviders = mutableListOf<String>()

    private var lat = 0.0
    private var lon = 0.0

    private val tick = object : Runnable {
        override fun run() {
            pushLocation()
            handler.postDelayed(this, UPDATE_INTERVAL_MS)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        locationManager = getSystemService(LOCATION_SERVICE) as LocationManager
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Stop request from the notification button.
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }

        lat = intent?.getDoubleExtra(EXTRA_LAT, lat) ?: lat
        lon = intent?.getDoubleExtra(EXTRA_LON, lon) ?: lon

        try {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                buildNotification(),
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
                } else {
                    0
                }
            )
        } catch (e: Exception) {
            fail(Failure.COULD_NOT_START)
            return START_NOT_STICKY
        }

        if (!registerProviders()) {
            fail(Failure.NOT_MOCK_APP)
            return START_NOT_STICKY
        }

        lastFailure = null
        currentLat = lat
        currentLon = lon
        isRunning = true
        handler.removeCallbacks(tick)
        handler.post(tick)
        notifyStateChanged()
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacks(tick)
        activeProviders.forEach { provider ->
            try {
                locationManager.setTestProviderEnabled(provider, false)
                locationManager.removeTestProvider(provider)
            } catch (e: Exception) {
                // Provider was never registered or already removed.
            }
        }
        activeProviders.clear()
        isRunning = false
        notifyStateChanged()
        super.onDestroy()
    }

    private fun fail(reason: Failure) {
        lastFailure = reason
        stopSelf()
    }

    /** Returns false if this app is not the selected mock location app. */
    private fun registerProviders(): Boolean {
        activeProviders.clear()
        for (provider in providers) {
            try {
                try {
                    locationManager.removeTestProvider(provider)
                } catch (e: Exception) {
                    // No stale test provider to clear.
                }
                locationManager.addTestProvider(
                    provider,
                    false, // requiresNetwork
                    false, // requiresSatellite
                    false, // requiresCell
                    false, // hasMonetaryCost
                    true,  // supportsAltitude
                    true,  // supportsSpeed
                    true,  // supportsBearing
                    Criteria.POWER_LOW,
                    Criteria.ACCURACY_FINE
                )
                locationManager.setTestProviderEnabled(provider, true)
                activeProviders.add(provider)
            } catch (e: SecurityException) {
                return false
            } catch (e: IllegalArgumentException) {
                // This provider is unavailable on the device; skip it.
            }
        }
        return activeProviders.isNotEmpty()
    }

    private fun pushLocation() {
        for (provider in activeProviders) {
            val location = Location(provider).apply {
                latitude = lat
                longitude = lon
                altitude = 0.0
                accuracy = 3f
                speed = 0f
                bearing = 0f
                time = System.currentTimeMillis()
                elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()
            }
            try {
                locationManager.setTestProviderLocation(provider, location)
            } catch (e: Exception) {
                // Mock app selection was revoked while running.
                fail(Failure.NOT_MOCK_APP)
                return
            }
        }
    }

    private fun buildNotification(): Notification {
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Mock location", NotificationManager.IMPORTANCE_LOW)
        )
        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        val stop = PendingIntent.getService(
            this,
            1,
            Intent(this, MockLocationService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentTitle("Mock location is ON")
            .setContentText(String.format(Locale.US, "%.6f, %.6f", lat, lon))
            .setContentIntent(openApp)
            .addAction(android.R.drawable.ic_media_pause, "STOP", stop)
            .setOngoing(true)
            .build()
    }

    enum class Failure { NOT_MOCK_APP, COULD_NOT_START }

    companion object {
        const val EXTRA_LAT = "lat"
        const val EXTRA_LON = "lon"
        const val ACTION_STOP = "com.diego.mocklocation.STOP"
        private const val CHANNEL_ID = "mock_location"
        private const val NOTIFICATION_ID = 1
        private const val UPDATE_INTERVAL_MS = 1000L

        @Volatile
        var isRunning = false
            private set

        /** The point currently being sent, valid while [isRunning] is true. */
        @Volatile
        var currentLat = 0.0
            private set

        @Volatile
        var currentLon = 0.0
            private set

        /** Why the last start attempt failed. The activity reads and clears this. */
        @Volatile
        var lastFailure: Failure? = null

        /** Set by the activity while it is visible; called on the main thread. */
        var stateListener: (() -> Unit)? = null

        private fun notifyStateChanged() {
            stateListener?.invoke()
        }
    }
}
