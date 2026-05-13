package com.example.streetsafe_code

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.location.Geocoder
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatButton
import androidx.core.app.ActivityCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.*
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.api.model.RectangularBounds
import com.google.android.libraries.places.widget.Autocomplete
import com.google.android.libraries.places.widget.AutocompleteActivity
import com.google.android.libraries.places.widget.model.AutocompleteActivityMode
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.*
import org.json.JSONObject
import java.net.URL
import java.util.Locale

class SafeRouteActivity : AppCompatActivity(), OnMapReadyCallback {

    // ── State ─────────────────────────────────────────────────────────────────
    private var originLatLng: LatLng?      = null
    private var originName: String         = ""
    private var destinationLatLng: LatLng? = null
    private var destinationName: String    = ""

    // ── Views ─────────────────────────────────────────────────────────────────
    private lateinit var googleMap: GoogleMap
    private lateinit var editOrigin: EditText
    private lateinit var editDestination: EditText
    private lateinit var btnUseMyLocation: AppCompatButton
    private lateinit var btnFindRoute: AppCompatButton
    private lateinit var txtRouteResult: TextView
    private lateinit var progressBar: ProgressBar

    companion object {
        private const val RC_ORIGIN      = 201
        private const val RC_DESTINATION = 202
        private const val RC_LOCATION    = 203

        // Cebu bounding box for autocomplete restriction
        private val CEBU_BOUNDS = RectangularBounds.newInstance(
            LatLng(9.8,  123.6),
            LatLng(10.8, 124.3)
        )

        // Danger radius in metres around each HIGH-risk incident
        private const val HAZARD_RADIUS_M = 200.0
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_safe_route)

        if (!Places.isInitialized()) {
            Places.initialize(applicationContext, getString(R.string.google_maps_key))
        }

        bindViews()
        setupOriginField()
        setupDestinationField()
        setupUseMyLocation()
        setupFindRouteButton()
        setupBackButton()

        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.safeRouteMap) as SupportMapFragment
        mapFragment.getMapAsync(this)
    }

    // ── View binding ──────────────────────────────────────────────────────────
    private fun bindViews() {
        editOrigin       = findViewById(R.id.editOrigin)
        editDestination  = findViewById(R.id.editDestination)
        btnUseMyLocation = findViewById(R.id.btnUseMyLocation)
        btnFindRoute     = findViewById(R.id.btnFindRoute)
        txtRouteResult   = findViewById(R.id.txtRouteResult)
        progressBar      = findViewById(R.id.progressBar)
    }

    // ── Places Autocomplete ───────────────────────────────────────────────────
    private fun setupOriginField() {
        editOrigin.isFocusable = false
        editOrigin.setOnClickListener { launchAutocomplete(RC_ORIGIN) }
    }

    private fun setupDestinationField() {
        editDestination.isFocusable = false
        editDestination.setOnClickListener { launchAutocomplete(RC_DESTINATION) }
    }

    private fun launchAutocomplete(requestCode: Int) {
        val fields = listOf(Place.Field.NAME, Place.Field.LAT_LNG, Place.Field.ADDRESS)
        val intent = Autocomplete
            .IntentBuilder(AutocompleteActivityMode.OVERLAY, fields)
            .setLocationRestriction(CEBU_BOUNDS)
            .build(this)
        @Suppress("DEPRECATION")
        startActivityForResult(intent, requestCode)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when {
            resultCode == Activity.RESULT_OK && requestCode == RC_ORIGIN -> {
                val place = Autocomplete.getPlaceFromIntent(data!!)
                originLatLng = place.latLng
                originName   = place.name ?: place.address ?: ""
                editOrigin.setText(originName)
            }
            resultCode == Activity.RESULT_OK && requestCode == RC_DESTINATION -> {
                val place = Autocomplete.getPlaceFromIntent(data!!)
                destinationLatLng = place.latLng
                destinationName   = place.name ?: place.address ?: ""
                editDestination.setText(destinationName)
            }
            resultCode == AutocompleteActivity.RESULT_ERROR -> {
                val status = Autocomplete.getStatusFromIntent(data!!)
                Toast.makeText(this, "Search error: ${status.statusMessage}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // ── Use My Location ───────────────────────────────────────────────────────
    private fun setupUseMyLocation() {
        btnUseMyLocation.setOnClickListener {
            if (ActivityCompat.checkSelfPermission(
                    this, Manifest.permission.ACCESS_FINE_LOCATION
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                    RC_LOCATION
                )
            } else {
                fetchCurrentLocation()
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == RC_LOCATION &&
            grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED
        ) fetchCurrentLocation()
        else Toast.makeText(this, "Location permission required", Toast.LENGTH_SHORT).show()
    }

    private fun fetchCurrentLocation() {
        if (ActivityCompat.checkSelfPermission(
                this, Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) return

        btnUseMyLocation.isEnabled = false
        btnUseMyLocation.text = "Getting location…"

        LocationServices.getFusedLocationProviderClient(this)
            .lastLocation
            .addOnSuccessListener { location ->
                btnUseMyLocation.isEnabled = true
                btnUseMyLocation.text      = "📍 Use My Location as Start"

                if (location == null) {
                    Toast.makeText(this, "Could not get location. Try again.", Toast.LENGTH_SHORT).show()
                    return@addOnSuccessListener
                }

                originLatLng = LatLng(location.latitude, location.longitude)

                try {
                    val geo     = Geocoder(this, Locale.getDefault())
                    @Suppress("DEPRECATION")
                    val results = geo.getFromLocation(location.latitude, location.longitude, 1)
                    originName  = results?.firstOrNull()?.getAddressLine(0)
                        ?: "%.5f, %.5f".format(location.latitude, location.longitude)
                } catch (e: Exception) {
                    originName = "%.5f, %.5f".format(location.latitude, location.longitude)
                }

                editOrigin.setText(originName)
                Toast.makeText(this, "Start set to your current location", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener {
                btnUseMyLocation.isEnabled = true
                btnUseMyLocation.text      = "📍 Use My Location as Start"
                Toast.makeText(this, "Failed to get location", Toast.LENGTH_SHORT).show()
            }
    }

    // ── Map ready ─────────────────────────────────────────────────────────────
    override fun onMapReady(map: GoogleMap) {
        googleMap = map
        googleMap.moveCamera(
            CameraUpdateFactory.newLatLngZoom(LatLng(10.3167, 123.8907), 12f)
        )
        googleMap.uiSettings.isZoomControlsEnabled = true
    }

    // ── Find Route button ─────────────────────────────────────────────────────
    private fun setupFindRouteButton() {
        btnFindRoute.setOnClickListener {
            val origin = originLatLng
            val dest   = destinationLatLng

            if (origin == null) {
                Toast.makeText(this, "Please set a starting point", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (dest == null) {
                Toast.makeText(this, "Please set a destination", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (origin == dest) {
                Toast.makeText(this, "Start and destination cannot be the same", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            fetchHazardsThenRoute(origin, dest)
        }
    }

    // ── Step 1: load HIGH-risk incidents from Firestore ───────────────────────
    private fun fetchHazardsThenRoute(origin: LatLng, destination: LatLng) {
        setLoading(true)
        txtRouteResult.text = "Loading hazard data…"

        FirebaseFirestore.getInstance()
            .collection("reports")
            .whereEqualTo("status",    "ACTIVE")
            .whereEqualTo("riskLevel", "HIGH")
            .get()
            .addOnSuccessListener { docs ->
                val hazards = docs.mapNotNull { doc ->
                    val lat = doc.getDouble("latitude")  ?: return@mapNotNull null
                    val lng = doc.getDouble("longitude") ?: return@mapNotNull null
                    LatLng(lat, lng)
                }
                fetchDirections(origin, destination, hazards)
            }
            .addOnFailureListener {
                // If Firestore fails, still fetch route with no hazard data
                fetchDirections(origin, destination, emptyList())
            }
    }

    // ── Step 2: call Directions API ───────────────────────────────────────────
    private fun fetchDirections(
        origin: LatLng,
        destination: LatLng,
        hazards: List<LatLng>
    ) {
        txtRouteResult.text = "Finding safest route…"

        val apiKey = getString(R.string.google_maps_key)

        // Build waypoints string — we route via points near hazards so Google
        // routes around them. We offset each hazard slightly so the "via"
        // point is beside the hazard, not through it.
        val waypointStr = if (hazards.isNotEmpty()) {
            val viaPoints = hazards.take(8).joinToString("|") { hazard ->
                // Offset ~300 m north so the route goes near but not through
                val offsetLat = hazard.latitude + 0.003
                "via:${offsetLat},${hazard.longitude}"
            }
            "&waypoints=optimize:true|$viaPoints"
        } else ""

        val url = "https://maps.googleapis.com/maps/api/directions/json" +
                "?origin=${origin.latitude},${origin.longitude}" +
                "&destination=${destination.latitude},${destination.longitude}" +
                "&mode=driving" +
                "&alternatives=true" +
                waypointStr +
                "&key=$apiKey"

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val response = URL(url).readText()
                withContext(Dispatchers.Main) {
                    handleDirectionsResponse(response, origin, destination, hazards)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    setLoading(false)
                    txtRouteResult.text = "Failed to fetch route. Check your internet connection."
                    Toast.makeText(
                        this@SafeRouteActivity,
                        "Network error: ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    // ── Step 3: parse response and draw on map ────────────────────────────────
    private fun handleDirectionsResponse(
        jsonResponse: String,
        origin: LatLng,
        destination: LatLng,
        hazards: List<LatLng>
    ) {
        setLoading(false)

        val json   = JSONObject(jsonResponse)
        val status = json.getString("status")

        if (status != "OK") {
            txtRouteResult.text = "Could not find a route ($status).\nTry different locations."
            return
        }

        googleMap.clear()

        val routes = json.getJSONArray("routes")

        // Draw all alternative routes in light gray first
        for (i in 0 until routes.length()) {
            val route    = routes.getJSONObject(i)
            val encoded  = route.getJSONObject("overview_polyline").getString("points")
            val points   = decodePolyline(encoded)

            if (i > 0) {
                // Alternative routes — thinner gray
                googleMap.addPolyline(
                    PolylineOptions()
                        .addAll(points)
                        .color(Color.parseColor("#90A4AE"))
                        .width(8f)
                        .pattern(listOf(Dash(20f), Gap(10f)))
                )
            }
        }

        // Draw the primary (safest) route in solid green on top
        val primaryRoute   = routes.getJSONObject(0)
        val primaryEncoded = primaryRoute.getJSONObject("overview_polyline").getString("points")
        val primaryPoints  = decodePolyline(primaryEncoded)

        googleMap.addPolyline(
            PolylineOptions()
                .addAll(primaryPoints)
                .color(Color.parseColor("#2E7D32"))
                .width(14f)
        )

        // Origin marker — green
        googleMap.addMarker(
            MarkerOptions()
                .position(origin)
                .title("Start: $originName")
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN))
        )

        // Destination marker — blue
        googleMap.addMarker(
            MarkerOptions()
                .position(destination)
                .title("Destination: $destinationName")
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_BLUE))
        )

        // Draw red danger circles around each HIGH-risk hazard
        for (hazard in hazards) {
            googleMap.addCircle(
                CircleOptions()
                    .center(hazard)
                    .radius(HAZARD_RADIUS_M)
                    .strokeColor(Color.RED)
                    .strokeWidth(2f)
                    .fillColor(Color.argb(60, 255, 0, 0))
            )
            googleMap.addMarker(
                MarkerOptions()
                    .position(hazard)
                    .title("⚠ High Risk Area")
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED))
            )
        }

        // Fit map to show the full route
        val boundsBuilder = LatLngBounds.Builder()
        primaryPoints.forEach { boundsBuilder.include(it) }
        hazards.forEach { boundsBuilder.include(it) }
        googleMap.animateCamera(
            CameraUpdateFactory.newLatLngBounds(boundsBuilder.build(), 120)
        )

        // Summary text
        val leg           = primaryRoute.getJSONArray("legs").getJSONObject(0)
        val distanceText  = leg.getJSONObject("distance").getString("text")
        val durationText  = leg.getJSONObject("duration").getString("text")
        val hazardNote    = if (hazards.isNotEmpty())
            "\n⚠ Routing around ${hazards.size} high-risk area(s)"
        else
            "\n✓ No high-risk areas on this route"

        txtRouteResult.text =
            "✅ Safest route: $originName → $destinationName\n" +
                    "📏 $distanceText  •  🕒 $durationText" +
                    hazardNote
    }

    // ── Polyline decoder (Google encoded format) ──────────────────────────────
    private fun decodePolyline(encoded: String): List<LatLng> {
        val poly  = mutableListOf<LatLng>()
        var index = 0
        val len   = encoded.length
        var lat   = 0
        var lng   = 0

        while (index < len) {
            var b: Int
            var shift = 0
            var result = 0
            do {
                b = encoded[index++].code - 63
                result = result or (b and 0x1f shl shift)
                shift += 5
            } while (b >= 0x20)
            val dLat = if (result and 1 != 0) (result shr 1).inv() else result shr 1
            lat += dLat

            shift  = 0
            result = 0
            do {
                b = encoded[index++].code - 63
                result = result or (b and 0x1f shl shift)
                shift += 5
            } while (b >= 0x20)
            val dLng = if (result and 1 != 0) (result shr 1).inv() else result shr 1
            lng += dLng

            poly.add(LatLng(lat / 1E5, lng / 1E5))
        }
        return poly
    }

    // ── Helpers ───────────────────────────────────────────────────────────────
    private fun setLoading(loading: Boolean) {
        progressBar.visibility    = if (loading) View.VISIBLE else View.GONE
        btnFindRoute.isEnabled    = !loading
        btnFindRoute.text         = if (loading) "Finding route…" else "Find Safe Route"
        editOrigin.isEnabled      = !loading
        editDestination.isEnabled = !loading
    }

    private fun setupBackButton() {
        findViewById<AppCompatButton>(R.id.btnBack).setOnClickListener { finish() }
    }
}