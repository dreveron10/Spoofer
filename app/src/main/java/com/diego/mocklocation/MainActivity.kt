package com.diego.mocklocation

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var map: MapView
    private lateinit var latInput: EditText
    private lateinit var lonInput: EditText
    private lateinit var status: TextView
    private lateinit var favorites: FavoritesStore
    private var marker: Marker? = null

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // osmdroid requires a user agent before the map view is inflated.
        Configuration.getInstance().userAgentValue = packageName
        setContentView(R.layout.activity_main)

        favorites = FavoritesStore(this)
        map = findViewById(R.id.map)
        latInput = findViewById(R.id.latInput)
        lonInput = findViewById(R.id.lonInput)
        status = findViewById(R.id.status)

        map.setTileSource(TileSourceFactory.MAPNIK)
        map.setMultiTouchControls(true)
        map.controller.setZoom(18.0)
        map.controller.setCenter(GeoPoint(DEFAULT_LAT, DEFAULT_LON))

        val tapOverlay = MapEventsOverlay(object : MapEventsReceiver {
            override fun singleTapConfirmedHelper(p: GeoPoint): Boolean {
                setPoint(p.latitude, p.longitude, recenter = false)
                return true
            }

            override fun longPressHelper(p: GeoPoint): Boolean = false
        })
        map.overlays.add(0, tapOverlay)

        findViewById<Button>(R.id.goButton).setOnClickListener {
            readInputs()?.let { (lat, lon) -> setPoint(lat, lon, recenter = true) }
        }
        findViewById<Button>(R.id.startButton).setOnClickListener { startMocking() }
        findViewById<Button>(R.id.stopButton).setOnClickListener { stopMocking() }
        findViewById<Button>(R.id.saveButton).setOnClickListener { saveFavorite() }
        findViewById<Button>(R.id.favoritesButton).setOnClickListener { showFavorites() }

        // Preselect the default point so Start works without picking anything.
        setPoint(DEFAULT_LAT, DEFAULT_LON, recenter = false)

        requestPermissionsIfNeeded()
    }

    override fun onResume() {
        super.onResume()
        map.onResume()
        refreshStatus()
    }

    override fun onPause() {
        map.onPause()
        super.onPause()
    }

    private fun requestPermissionsIfNeeded() {
        val wanted = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            wanted.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        val missing = wanted.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) {
            permissionLauncher.launch(missing.toTypedArray())
        }
    }

    private fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    /** Returns the typed coordinates, or null (with a toast) if they are invalid. */
    private fun readInputs(): Pair<Double, Double>? {
        val lat = latInput.text.toString().toDoubleOrNull()
        val lon = lonInput.text.toString().toDoubleOrNull()
        if (lat == null || lon == null || lat !in -90.0..90.0 || lon !in -180.0..180.0) {
            toast("Enter a valid latitude (-90 to 90) and longitude (-180 to 180)")
            return null
        }
        return lat to lon
    }

    private fun setPoint(lat: Double, lon: Double, recenter: Boolean) {
        latInput.setText(String.format(Locale.US, "%.6f", lat))
        lonInput.setText(String.format(Locale.US, "%.6f", lon))

        val point = GeoPoint(lat, lon)
        val m = marker ?: Marker(map).also {
            it.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            it.setInfoWindow(null)
            map.overlays.add(it)
            marker = it
        }
        m.position = point
        if (recenter) {
            map.controller.animateTo(point)
        }
        map.invalidate()
    }

    private fun startMocking() {
        val (lat, lon) = readInputs() ?: return
        if (!hasLocationPermission()) {
            toast("Location permission is required to start")
            requestPermissionsIfNeeded()
            return
        }
        setPoint(lat, lon, recenter = false)
        val intent = Intent(this, MockLocationService::class.java)
            .putExtra(MockLocationService.EXTRA_LAT, lat)
            .putExtra(MockLocationService.EXTRA_LON, lon)
        ContextCompat.startForegroundService(this, intent)
        // The service starts asynchronously, so check its real state shortly after.
        status.postDelayed({ refreshStatus() }, 500)
    }

    private fun stopMocking() {
        stopService(Intent(this, MockLocationService::class.java))
        status.postDelayed({ refreshStatus() }, 300)
    }

    private fun refreshStatus() {
        status.text = if (MockLocationService.isRunning) {
            "Mocking active. Press Start again to move to a new point."
        } else {
            "Stopped. Tap the map to pick a point."
        }
    }

    private fun saveFavorite() {
        val (lat, lon) = readInputs() ?: return
        val nameInput = EditText(this).apply { hint = "Name" }
        AlertDialog.Builder(this)
            .setTitle("Save spot")
            .setView(nameInput)
            .setPositiveButton("Save") { _, _ ->
                val name = nameInput.text.toString().trim().ifEmpty {
                    String.format(Locale.US, "%.4f, %.4f", lat, lon)
                }
                favorites.add(Favorite(name, lat, lon))
                toast("Saved")
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showFavorites() {
        val list = favorites.all()
        if (list.isEmpty()) {
            toast("No saved spots yet")
            return
        }
        val names = list.map { it.name }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Saved spots")
            .setItems(names) { _, which ->
                val f = list[which]
                setPoint(f.lat, f.lon, recenter = true)
            }
            .setNeutralButton("Delete one") { _, _ -> showDeleteFavorite() }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun showDeleteFavorite() {
        val list = favorites.all()
        val names = list.map { it.name }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Delete which spot?")
            .setItems(names) { _, which ->
                favorites.removeAt(which)
                toast("Deleted")
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private companion object {
        // Default point: Antonio Lucchetti building (Mechanical Engineering), UPRM.
        const val DEFAULT_LAT = 18.218075
        const val DEFAULT_LON = -67.143258
    }
}
