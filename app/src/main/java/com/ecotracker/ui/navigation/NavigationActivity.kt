package com.ecotracker.ui.navigation

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.location.Location
import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.ecotracker.R
import com.ecotracker.data.LocalEcoRepository
import com.ecotracker.data.LoggableActivity
import com.ecotracker.databinding.ActivityNavigationBinding
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import java.net.URLEncoder
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.round

class NavigationActivity : AppCompatActivity() {

    private lateinit var binding: ActivityNavigationBinding
    private lateinit var repository: LocalEcoRepository
    private lateinit var fusedLocationClient: FusedLocationProviderClient

    private var currentUserLocation: GeoPoint? = null
    private var currentStartLocation: GeoPoint? = null
    private var currentDestination: GeoPoint? = null
    private var currentRoutePolyline: Polyline? = null
    private var userMarker: Marker? = null
    private var destinationMarker: Marker? = null

    private var lastWalkDistanceKm: Double = 0.0
    private var lastCo2SavedKg: Double = 0.0
    private var lastAwardedPoints: Int = 0
    private var lastDestinationName: String = ""
    private var isRouteCalculated: Boolean = false
    private var isCurrentRouteLogged: Boolean = false

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
        val coarseGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] ?: false
        if (fineGranted || coarseGranted) {
            startLocationTracking()
        } else {
            Toast.makeText(this, "Location permission denied. Showing campus view.", Toast.LENGTH_SHORT).show()
            setDefaultCampusLocation()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize OSMDroid configuration with custom User-Agent per OSM usage policy
        Configuration.getInstance().load(this, getSharedPreferences("osmdroid", Context.MODE_PRIVATE))
        Configuration.getInstance().userAgentValue = "EcoTracker-App/1.0 (Android; Sustainability Walking Navigation)"

        binding = ActivityNavigationBinding.inflate(layoutInflater)
        setContentView(binding.root)

        repository = LocalEcoRepository(applicationContext)
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        setupMap()
        setupListeners()
        checkLocationPermission()
    }

    private fun setupMap() {
        binding.mapView.setTileSource(TileSourceFactory.MAPNIK)
        binding.mapView.setMultiTouchControls(true)
        binding.mapView.controller.setZoom(16.5)

        // Apply dark mode tile filter if in night mode
        applyMapTheme()

        // Default initial center
        val defaultPoint = GeoPoint(12.9716, 77.5946)
        binding.mapView.controller.setCenter(defaultPoint)

        binding.etSearchSource.setText("My Location")
        binding.btnLogWalk.isEnabled = false
        binding.btnLogWalk.text = "Log Walk (+0 points)"
    }

    private fun applyMapTheme() {
        val isNight = (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
        if (isNight) {
            val matrix = ColorMatrix(floatArrayOf(
                -0.85f, 0f, 0f, 0f, 255f,
                0f, -0.85f, 0f, 0f, 255f,
                0f, 0f, -0.85f, 0f, 255f,
                0f, 0f, 0f, 1f, 0f
            ))
            binding.mapView.overlayManager.tilesOverlay.setColorFilter(ColorMatrixColorFilter(matrix))
        } else {
            binding.mapView.overlayManager.tilesOverlay.setColorFilter(null)
        }
    }

    private fun setupListeners() {
        binding.btnBack.setOnClickListener {
            finish()
        }

        binding.fabMyLocation.setOnClickListener {
            currentUserLocation?.let {
                binding.mapView.controller.animateTo(it)
                binding.mapView.controller.setZoom(17.5)
            } ?: run {
                checkLocationPermission()
            }
        }

        binding.btnUseMyLocation.setOnClickListener {
            binding.etSearchSource.setText("My Location")
            currentStartLocation = currentUserLocation ?: GeoPoint(12.9716, 77.5946)
            Toast.makeText(this, "Source set to My Location", Toast.LENGTH_SHORT).show()
            if (binding.etSearchDestination.text.toString().isNotBlank()) {
                performRouteSearch()
            }
        }

        binding.btnSearch.setOnClickListener {
            performRouteSearch()
        }

        binding.etSearchDestination.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                performRouteSearch()
                true
            } else false
        }

        binding.etSearchSource.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH || actionId == EditorInfo.IME_ACTION_NEXT) {
                performRouteSearch()
                true
            } else false
        }

        binding.btnLogWalk.setOnClickListener {
            if (!isRouteCalculated) {
                Toast.makeText(this, "No valid route calculated. Cannot award points.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (isCurrentRouteLogged) {
                Toast.makeText(this, "This walk has already been logged!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Prevent duplicate rewards and duplicate taps immediately
            isCurrentRouteLogged = true
            binding.btnLogWalk.isEnabled = false
            binding.btnLogWalk.text = "Walk Logged ✓"

            val startDesc = binding.etSearchSource.text.toString().trim().ifBlank { "My Location" }
            val destDesc = if (lastDestinationName.isNotBlank()) lastDestinationName else binding.etSearchDestination.text.toString().trim()

            val activity = LoggableActivity(
                label = "Walked ${String.format(Locale.getDefault(), "%.2f km", lastWalkDistanceKm)}: $startDesc to $destDesc",
                points = lastAwardedPoints,
                co2Kg = lastCo2SavedKg,
                waterLitres = 0
            )

            // Save route, distance, activity, and points to user's Firestore and Room records
            repository.logActivity(activity)

            Toast.makeText(
                this,
                "Logged walk! +$lastAwardedPoints Eco Points 🌿 (Saved to Firestore & Room)",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun checkLocationPermission() {
        val fineLocation = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
        val coarseLocation = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)

        if (fineLocation == PackageManager.PERMISSION_GRANTED || coarseLocation == PackageManager.PERMISSION_GRANTED) {
            startLocationTracking()
        } else {
            locationPermissionLauncher.launch(
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
            )
        }
    }

    private fun startLocationTracking() {
        try {
            fusedLocationClient.lastLocation.addOnSuccessListener { loc: Location? ->
                if (loc != null) {
                    updateUserLocation(GeoPoint(loc.latitude, loc.longitude))
                } else {
                    setDefaultCampusLocation()
                }
            }

            val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5000)
                .setMinUpdateDistanceMeters(5f)
                .build()

            fusedLocationClient.requestLocationUpdates(request, object : LocationCallback() {
                override fun onLocationResult(result: LocationResult) {
                    val loc = result.lastLocation ?: return
                    updateUserLocation(GeoPoint(loc.latitude, loc.longitude))
                }
            }, mainLooper)
        } catch (e: SecurityException) {
            setDefaultCampusLocation()
        }
    }

    private fun setDefaultCampusLocation() {
        val campusCenter = GeoPoint(12.9716, 77.5946)
        updateUserLocation(campusCenter)
    }

    private fun updateUserLocation(point: GeoPoint) {
        val isFirstFix = (currentUserLocation == null)
        currentUserLocation = point

        if (userMarker == null) {
            userMarker = Marker(binding.mapView).apply {
                title = "Your Location"
                icon = ContextCompat.getDrawable(this@NavigationActivity, R.drawable.ic_navigation)
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
            }
            binding.mapView.overlays.add(userMarker)
        }
        userMarker?.position = point

        if (isFirstFix) {
            binding.mapView.controller.setCenter(point)
        }
        binding.mapView.invalidate()
    }

    private fun performRouteSearch() {
        val sourceQuery = binding.etSearchSource.text.toString().trim()
        val destQuery = binding.etSearchDestination.text.toString().trim()

        if (destQuery.isEmpty()) {
            Toast.makeText(this, "Please enter a destination to search", Toast.LENGTH_SHORT).show()
            return
        }

        binding.progressRouting.visibility = View.VISIBLE
        binding.btnLogWalk.isEnabled = false
        binding.btnLogWalk.text = "Calculating route..."
        binding.tvDistanceEta.text = "Calculating walking route..."
        isRouteCalculated = false
        isCurrentRouteLogged = false

        CoroutineScope(Dispatchers.IO).launch {
            // 1. Resolve Start Location
            val startPoint: GeoPoint? = if (sourceQuery.isEmpty() ||
                sourceQuery.equals("my location", ignoreCase = true) ||
                sourceQuery.equals("current location", ignoreCase = true)) {
                currentUserLocation ?: GeoPoint(12.9716, 77.5946)
            } else {
                geocodeLocation(sourceQuery)
            }

            if (startPoint == null) {
                withContext(Dispatchers.Main) {
                    binding.progressRouting.visibility = View.GONE
                    binding.btnLogWalk.isEnabled = false
                    binding.btnLogWalk.text = "Route Failed - Start Not Found"
                    binding.tvDistanceEta.text = "Could not find start point"
                    Toast.makeText(this@NavigationActivity, "Could not find starting point: \"$sourceQuery\"", Toast.LENGTH_SHORT).show()
                }
                return@launch
            }

            // 2. Resolve Destination Location
            val destPoint = geocodeLocation(destQuery)
            if (destPoint == null) {
                withContext(Dispatchers.Main) {
                    binding.progressRouting.visibility = View.GONE
                    binding.btnLogWalk.isEnabled = false
                    binding.btnLogWalk.text = "Route Failed - Destination Not Found"
                    binding.tvDistanceEta.text = "Could not find destination"
                    Toast.makeText(this@NavigationActivity, "Could not find destination: \"$destQuery\"", Toast.LENGTH_SHORT).show()
                }
                return@launch
            }

            withContext(Dispatchers.Main) {
                currentStartLocation = startPoint
                userMarker?.position = startPoint
                currentDestination = destPoint
                lastDestinationName = destQuery

                if (destinationMarker == null) {
                    destinationMarker = Marker(binding.mapView).apply {
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                        icon = ContextCompat.getDrawable(this@NavigationActivity, R.drawable.ic_leaf)
                    }
                    binding.mapView.overlays.add(destinationMarker)
                }
                destinationMarker?.position = destPoint
                destinationMarker?.title = destQuery
                binding.mapView.invalidate()

                calculateWalkingRoute(startPoint, destPoint)
            }
        }
    }

    private fun geocodeLocation(query: String): GeoPoint? {
        return try {
            val encodedQuery = URLEncoder.encode(query, "UTF-8")
            val url = "https://nominatim.openstreetmap.org/search?format=json&q=$encodedQuery&limit=1"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "EcoTracker-App/1.0 (Android; Sustainability Navigation)")
                .build()

            val response = httpClient.newCall(request).execute()
            val body = response.body?.string()
            if (response.isSuccessful && !body.isNullOrBlank() && body.startsWith("[")) {
                val array = JSONArray(body)
                if (array.length() > 0) {
                    val obj = array.getJSONObject(0)
                    GeoPoint(obj.getDouble("lat"), obj.getDouble("lon"))
                } else null
            } else null
        } catch (e: Exception) {
            null
        }
    }

    private fun calculateWalkingRoute(start: GeoPoint, dest: GeoPoint) {
        binding.progressRouting.visibility = View.VISIBLE
        binding.btnLogWalk.isEnabled = false
        binding.btnLogWalk.text = "Calculating route..."
        isRouteCalculated = false

        CoroutineScope(Dispatchers.IO).launch {
            val osrmUrl = "https://router.project-osrm.org/route/v1/walking/" +
                    "${start.longitude},${start.latitude};${dest.longitude},${dest.latitude}" +
                    "?overview=full&geometries=geojson&steps=true"

            var routePoints: List<GeoPoint>? = null
            var distanceMeters = 0.0
            var durationSeconds = 0.0
            val turnSteps = mutableListOf<String>()
            var routeSuccessful = false

            try {
                val request = Request.Builder()
                    .url(osrmUrl)
                    .header("User-Agent", "EcoTracker-App/1.0 (Android; OSRM Walking Directions)")
                    .build()

                val response = httpClient.newCall(request).execute()
                val body = response.body?.string()

                if (response.isSuccessful && !body.isNullOrBlank()) {
                    val json = JSONObject(body)
                    if (json.optString("code") == "Ok") {
                        val routes = json.getJSONArray("routes")
                        if (routes.length() > 0) {
                            val firstRoute = routes.getJSONObject(0)
                            distanceMeters = firstRoute.getDouble("distance")
                            durationSeconds = firstRoute.getDouble("duration")

                            val coordinates = firstRoute.getJSONObject("geometry").getJSONArray("coordinates")
                            val pts = mutableListOf<GeoPoint>()
                            for (i in 0 until coordinates.length()) {
                                val coord = coordinates.getJSONArray(i)
                                pts.add(GeoPoint(coord.getDouble(1), coord.getDouble(0)))
                            }
                            routePoints = pts

                            // Parse turn steps
                            val legs = firstRoute.getJSONArray("legs")
                            if (legs.length() > 0) {
                                val steps = legs.getJSONObject(0).getJSONArray("steps")
                                for (j in 0 until steps.length()) {
                                    val stepObj = steps.getJSONObject(j)
                                    val maneuver = stepObj.optJSONObject("maneuver")?.optString("type") ?: "continue"
                                    val modifier = stepObj.optJSONObject("maneuver")?.optString("modifier") ?: ""
                                    val name = stepObj.optString("name").ifBlank { "walkway" }
                                    turnSteps.add("• ${maneuver.replaceFirstChar { it.uppercase() }} $modifier onto $name")
                                }
                            }
                            routeSuccessful = true
                        }
                    }
                }
            } catch (e: Exception) {
                routeSuccessful = false
            }

            withContext(Dispatchers.Main) {
                binding.progressRouting.visibility = View.GONE

                if (!routeSuccessful || routePoints == null || routePoints.isEmpty()) {
                    // Never award points if route calculation fails!
                    isRouteCalculated = false
                    currentRoutePolyline?.let { binding.mapView.overlays.remove(it) }
                    binding.mapView.invalidate()

                    binding.btnLogWalk.isEnabled = false
                    binding.btnLogWalk.text = "Route Failed - Cannot Log"
                    binding.tvDistanceEta.text = "Route calculation failed"
                    binding.tvCo2SavingsBanner.text = "Check connection or search terms"
                    binding.tvTurnByTurn.text = "Directions unavailable. Cannot award points for failed routes."
                    Toast.makeText(
                        this@NavigationActivity,
                        "Route calculation failed. Eco-points cannot be awarded.",
                        Toast.LENGTH_LONG
                    ).show()
                } else {
                    // Route succeeded: calculate distance and award 5 eco-points per km rounded to nearest whole point
                    val distanceKm = distanceMeters / 1000.0
                    val points = round(distanceKm * 5.0).toInt().coerceAtLeast(1)

                    drawRoute(routePoints, distanceMeters, durationSeconds, turnSteps, points)
                }
            }
        }
    }

    private fun drawRoute(
        points: List<GeoPoint>,
        distanceMeters: Double,
        durationSeconds: Double,
        steps: List<String>,
        pointsAwarded: Int
    ) {
        currentRoutePolyline?.let { binding.mapView.overlays.remove(it) }

        val polyline = Polyline(binding.mapView).apply {
            setPoints(points)
            outlinePaint.color = Color.parseColor("#2E7D32")
            outlinePaint.strokeWidth = 9f
        }
        currentRoutePolyline = polyline
        binding.mapView.overlays.add(0, polyline)

        val distanceKm = distanceMeters / 1000.0
        val durationMinutes = (durationSeconds / 60.0).toInt().coerceAtLeast(1)
        val co2Saved = distanceKm * 0.15 // 0.15 kg CO2 saved per km walked vs car

        lastWalkDistanceKm = distanceKm
        lastCo2SavedKg = co2Saved
        lastAwardedPoints = pointsAwarded
        isRouteCalculated = true
        isCurrentRouteLogged = false

        // Display walking distance and ETA
        binding.tvDistanceEta.text = String.format(Locale.getDefault(), "%.2f km • ~%d min walk", distanceKm, durationMinutes)
        binding.tvCo2SavingsBanner.text = String.format(Locale.getDefault(), "🌿 Saves ~%.2f kg CO₂ vs driving a car", co2Saved)

        if (steps.isNotEmpty()) {
            binding.tvTurnByTurn.text = steps.take(2).joinToString("\n")
        } else {
            binding.tvTurnByTurn.text = "Follow highlighted walking route."
        }

        // Show "Log Walk (+X points)" below distance
        binding.btnLogWalk.isEnabled = true
        binding.btnLogWalk.text = String.format(Locale.getDefault(), "Log Walk (+%d points)", pointsAwarded)

        // Center and zoom bounding box
        if (points.isNotEmpty()) {
            val minLat = points.minOf { it.latitude }
            val maxLat = points.maxOf { it.latitude }
            val minLon = points.minOf { it.longitude }
            val maxLon = points.maxOf { it.longitude }
            val box = BoundingBox(maxLat + 0.001, maxLon + 0.001, minLat - 0.001, minLon - 0.001)
            binding.mapView.zoomToBoundingBox(box, true, 80)
        }

        binding.mapView.invalidate()
    }

    override fun onResume() {
        super.onResume()
        applyMapTheme()
        binding.mapView.onResume()
    }

    override fun onPause() {
        super.onPause()
        binding.mapView.onPause()
    }
}
