package com.example.streetsafe_code

import android.Manifest
import android.app.AlertDialog
import android.content.pm.PackageManager
import android.graphics.Color
import android.location.Geocoder
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
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
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject
import java.net.URL
import java.net.URLEncoder
import java.util.Locale

class SafeRouteActivity : AppCompatActivity(), OnMapReadyCallback {

    // ── State ─────────────────────────────────────────────────────────────────
    private var originLatLng: LatLng?      = null
    private var originName: String         = ""
    private var destinationLatLng: LatLng? = null
    private var destinationName: String    = ""

    // Debounce jobs — cancel previous search before starting a new one
    private var originSearchJob: Job?      = null
    private var destSearchJob: Job?        = null

    // ── Views ─────────────────────────────────────────────────────────────────
    private lateinit var googleMap: GoogleMap
    private lateinit var editOrigin: EditText
    private lateinit var editDestination: EditText
    private lateinit var btnUseMyLocation: AppCompatButton
    private lateinit var btnFindRoute: AppCompatButton
    private lateinit var txtRouteResult: TextView
    private lateinit var progressBar: ProgressBar

    // ── Coroutine scope ───────────────────────────────────────────────────────
    private val activityScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    companion object {
        private const val RC_LOCATION     = 203
        private const val HAZARD_RADIUS_M = 200.0
        private const val SEARCH_DELAY_MS = 600L
        private const val ORS_API_KEY     = "eyJvcmciOiI1YjNjZTM1OTc4NTExMTAwMDFjZjYyNDgiLCJpZCI6Ijc0MGQ4YWRkNTE3ZTQzNmNhYjdiYzc5ZjJhYzU2ZTA5IiwiaCI6Im11cm11cjY0In0=" // replace with your key
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_safe_route)

        editOrigin       = findViewById(R.id.editOrigin)
        editDestination  = findViewById(R.id.editDestination)
        btnUseMyLocation = findViewById(R.id.btnUseMyLocation)
        btnFindRoute     = findViewById(R.id.btnFindRoute)
        txtRouteResult   = findViewById(R.id.txtRouteResult)
        progressBar      = findViewById(R.id.progressBar)

        setupOriginField()
        setupDestinationField()
        setupUseMyLocation()
        setupFindRouteButton()
        findViewById<AppCompatButton>(R.id.btnBack).setOnClickListener { finish() }

        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.safeRouteMap) as SupportMapFragment
        mapFragment.getMapAsync(this)
    }

    override fun onDestroy() {
        super.onDestroy()
        activityScope.cancel()
    }

    // ── Map ───────────────────────────────────────────────────────────────────
    override fun onMapReady(map: GoogleMap) {
        googleMap = map
        googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(10.3167, 123.8907), 12f))
        googleMap.uiSettings.isZoomControlsEnabled = true
    }

    // ── Nominatim Search (debounced) ──────────────────────────────────────────
    private fun setupOriginField() {
        editOrigin.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun afterTextChanged(s: Editable?) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val query = s?.toString()?.trim() ?: return
                if (query == originName && originLatLng != null) return
                originLatLng = null

                originSearchJob?.cancel()
                if (query.length < 3) return
                originSearchJob = activityScope.launch {
                    delay(SEARCH_DELAY_MS)
                    if (editOrigin.hasFocus()) searchLocation(query, isOrigin = true)
                }
            }
        })
    }

    private fun setupDestinationField() {
        editDestination.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun afterTextChanged(s: Editable?) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val query = s?.toString()?.trim() ?: return
                if (query == destinationName && destinationLatLng != null) return
                destinationLatLng = null

                destSearchJob?.cancel()
                if (query.length < 3) return
                destSearchJob = activityScope.launch {
                    delay(SEARCH_DELAY_MS)
                    if (editDestination.hasFocus()) searchLocation(query, isOrigin = false)
                }
            }
        })
    }

    private fun searchLocation(query: String, isOrigin: Boolean) {
        activityScope.launch {
            try {
                val encoded  = URLEncoder.encode("$query, Cebu, Philippines", "UTF-8")
                val url      = "https://nominatim.openstreetmap.org/search?q=$encoded&format=json&limit=5&countrycodes=ph"
                val response = withContext(Dispatchers.IO) {
                    val conn = URL(url).openConnection()
                    conn.setRequestProperty("User-Agent", "StreetSafeApp/1.0")
                    conn.connect()
                    conn.getInputStream().bufferedReader().readText()
                }

                val results = JSONArray(response)
                if (results.length() == 0) return@launch

                val currentField = if (isOrigin) editOrigin else editDestination
                if (!currentField.hasFocus() || currentField.text.toString().trim().length < 3) return@launch

                val names = Array(results.length()) { i ->
                    results.getJSONObject(i).getString("display_name")
                        .split(",").take(3).joinToString(", ")
                }

                AlertDialog.Builder(this@SafeRouteActivity)
                    .setTitle(if (isOrigin) "Select Starting Point" else "Select Destination")
                    .setItems(names) { _, index ->
                        val place = results.getJSONObject(index)
                        val lat   = place.getString("lat").toDouble()
                        val lng   = place.getString("lon").toDouble()
                        val name  = names[index]

                        if (isOrigin) {
                            originLatLng = LatLng(lat, lng)
                            originName   = name
                            editOrigin.setText(name)
                            editOrigin.clearFocus()
                        } else {
                            destinationLatLng = LatLng(lat, lng)
                            destinationName   = name
                            editDestination.setText(name)
                            editDestination.clearFocus()
                        }
                    }
                    .setNegativeButton("Cancel", null)
                    .show()

            } catch (e: Exception) {
                // Silent — user can keep typing
            }
        }
    }

    // ── Use My Location ───────────────────────────────────────────────────────
    private fun setupUseMyLocation() {
        btnUseMyLocation.setOnClickListener {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), RC_LOCATION
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
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) return

        btnUseMyLocation.isEnabled = false
        btnUseMyLocation.text      = "Getting location…"

        LocationServices.getFusedLocationProviderClient(this).lastLocation
            .addOnSuccessListener { location ->
                btnUseMyLocation.isEnabled = true
                btnUseMyLocation.text      = "📍 Use My Location"

                if (location == null) {
                    Toast.makeText(this, "Could not get location. Try again.", Toast.LENGTH_SHORT).show()
                    return@addOnSuccessListener
                }

                originLatLng = LatLng(location.latitude, location.longitude)
                try {
                    @Suppress("DEPRECATION")
                    val results = Geocoder(this, Locale.getDefault())
                        .getFromLocation(location.latitude, location.longitude, 1)
                    originName = results?.firstOrNull()?.getAddressLine(0)
                        ?: "%.5f, %.5f".format(location.latitude, location.longitude)
                } catch (e: Exception) {
                    originName = "%.5f, %.5f".format(location.latitude, location.longitude)
                }

                editOrigin.setText(originName)
                editOrigin.clearFocus()
                Toast.makeText(this, "Start set to your current location", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener {
                btnUseMyLocation.isEnabled = true
                btnUseMyLocation.text      = "📍 Use My Location"
                Toast.makeText(this, "Failed to get location", Toast.LENGTH_SHORT).show()
            }
    }

    // ── Find Route ────────────────────────────────────────────────────────────
    private fun setupFindRouteButton() {
        btnFindRoute.setOnClickListener {
            val origin = originLatLng
            val dest   = destinationLatLng

            if (origin == null) {
                Toast.makeText(this, "Please select a starting point from the suggestions", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (dest == null) {
                Toast.makeText(this, "Please select a destination from the suggestions", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            fetchHazardsThenRoute(origin, dest)
        }
    }

    // ── Step 1: Load hazards from Firestore ───────────────────────────────────
    private fun fetchHazardsThenRoute(origin: LatLng, destination: LatLng) {
        setLoading(true)
        txtRouteResult.text = "Loading hazard data…"

        // FIX: also load MEDIUM risk for map display (shown as yellow circles)
        FirebaseFirestore.getInstance()
            .collection("reports")
            .whereEqualTo("status", "ACTIVE")
            .get()
            .addOnSuccessListener { docs ->
                val highHazards = mutableListOf<LatLng>()
                val mediumHazards = mutableListOf<LatLng>()

                for (doc in docs) {
                    val lat   = doc.getDouble("latitude")  ?: continue
                    val lng   = doc.getDouble("longitude") ?: continue
                    val risk  = doc.getString("riskLevel") ?: continue
                    when (risk) {
                        "HIGH"   -> highHazards.add(LatLng(lat, lng))
                        "MEDIUM" -> mediumHazards.add(LatLng(lat, lng))
                    }
                }

                // FIX: pass risk as parameter instead of storing in mutable state
                val risk = evaluateRouteRisk(origin, destination, highHazards)
                handleRoutingByRisk(origin, destination, highHazards, mediumHazards, risk)
            }
            .addOnFailureListener {
                handleRoutingByRisk(origin, destination, emptyList(), emptyList(), RouteRisk.SAFE)
            }
    }

    // ── Step 2: Evaluate risk level ───────────────────────────────────────────
    private fun evaluateRouteRisk(
        origin: LatLng,
        destination: LatLng,
        hazards: List<LatLng>
    ): RouteRisk {
        if (isNearHazard(destination, hazards)) return RouteRisk.DESTINATION_AT_RISK
        // FIX: also warn if origin itself is inside a hazard zone
        if (isNearHazard(origin, hazards))      return RouteRisk.ORIGIN_AT_RISK
        if (hazards.isNotEmpty())               return RouteRisk.ROUTE_AT_RISK
        return RouteRisk.SAFE
    }

    // ── Step 3: Route based on risk ───────────────────────────────────────────
    private fun handleRoutingByRisk(
        origin: LatLng,
        destination: LatLng,
        highHazards: List<LatLng>,
        mediumHazards: List<LatLng>,
        risk: RouteRisk   // FIX: passed as param, not stored in mutable state
    ) {
        when (risk) {
            RouteRisk.DESTINATION_AT_RISK -> {
                Toast.makeText(
                    this,
                    "⚠ Destination is inside a HIGH-risk area. Proceeding anyway.",
                    Toast.LENGTH_LONG
                ).show()
                // No avoidance so ORS can still reach the destination
                fetchORSRoute(origin, destination, emptyList(), mediumHazards, risk)
            }

            RouteRisk.ORIGIN_AT_RISK -> {
                Toast.makeText(
                    this,
                    "⚠ You are currently in a HIGH-risk area. Finding exit route.",
                    Toast.LENGTH_LONG
                ).show()
                // Still avoid other hazards but not the origin zone
                val otherHazards = highHazards.filter { !isNearHazard(origin, listOf(it)) }
                fetchORSRoute(origin, destination, otherHazards, mediumHazards, risk)
            }

            RouteRisk.ROUTE_AT_RISK -> {
                fetchORSRoute(origin, destination, highHazards, mediumHazards, risk)
            }

            RouteRisk.SAFE -> {
                fetchORSRoute(origin, destination, emptyList(), mediumHazards, risk)
            }
        }
    }

    // ── Step 4: Call ORS ──────────────────────────────────────────────────────
    private fun fetchORSRoute(
        origin: LatLng,
        destination: LatLng,
        highHazards: List<LatLng>,
        mediumHazards: List<LatLng>,
        risk: RouteRisk   // FIX: passed through so drawRouteOnMap can use it
    ) {
        txtRouteResult.text = "Finding safest route…"

        activityScope.launch {
            try {
                val coords = JSONArray().apply {
                    put(JSONArray().apply { put(origin.longitude); put(origin.latitude) })
                    put(JSONArray().apply { put(destination.longitude); put(destination.latitude) })
                }

                val body = JSONObject().apply {
                    put("coordinates", coords)
                    put("instructions", false)
                    put("geometry", true)

                    if (highHazards.isNotEmpty()) {
                        val offset = 0.003
                        val multiPolygonCoords = JSONArray()
                        for (h in highHazards) {
                            val ring = JSONArray().apply {
                                put(JSONArray().apply { put(h.longitude);          put(h.latitude + offset) })
                                put(JSONArray().apply { put(h.longitude + offset); put(h.latitude)          })
                                put(JSONArray().apply { put(h.longitude);          put(h.latitude - offset) })
                                put(JSONArray().apply { put(h.longitude - offset); put(h.latitude)          })
                                put(JSONArray().apply { put(h.longitude);          put(h.latitude + offset) })
                            }
                            multiPolygonCoords.put(JSONArray().apply { put(ring) })
                        }
                        put("options", JSONObject().apply {
                            put("avoid_polygons", JSONObject().apply {
                                put("type", "MultiPolygon")
                                put("coordinates", multiPolygonCoords)
                            })
                        })
                    }
                }.toString()

                val response = withContext(Dispatchers.IO) {
                    val conn = URL("https://api.openrouteservice.org/v2/directions/driving-car/geojson")
                        .openConnection() as java.net.HttpURLConnection
                    conn.requestMethod = "POST"
                    conn.setRequestProperty("Content-Type", "application/json")
                    conn.setRequestProperty("Authorization", ORS_API_KEY)
                    conn.doOutput = true
                    conn.outputStream.write(body.toByteArray())

                    if (conn.responseCode != 200) {
                        val err = conn.errorStream?.bufferedReader()?.readText() ?: "Unknown error"
                        throw Exception("ORS ${conn.responseCode}: $err")
                    }
                    conn.inputStream.bufferedReader().readText()
                }

                withContext(Dispatchers.Main) {
                    drawRouteOnMap(response, origin, destination, highHazards, mediumHazards, risk)
                }

            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    setLoading(false)
                    txtRouteResult.text = "❌ Error: ${e.message}"
                    Toast.makeText(this@SafeRouteActivity,
                        "Route error: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    // ── Step 5: Draw route on map ─────────────────────────────────────────────
    private fun drawRouteOnMap(
        jsonResponse: String,
        origin: LatLng,
        destination: LatLng,
        highHazards: List<LatLng>,
        mediumHazards: List<LatLng>,
        risk: RouteRisk   // FIX: received as param instead of reading mutable state
    ) {
        setLoading(false)

        val json     = JSONObject(jsonResponse)
        val features = json.optJSONArray("features")

        if (features == null || features.length() == 0) {
            txtRouteResult.text = "Could not find a route. Try different locations."
            return
        }

        googleMap.clear()

        // Alternate routes (gray dashed)
        for (i in 1 until features.length()) {
            val pts = orsToLatLng(features.getJSONObject(i)
                .getJSONObject("geometry").getJSONArray("coordinates"))
            googleMap.addPolyline(
                PolylineOptions().addAll(pts)
                    .color(Color.parseColor("#90A4AE")).width(8f)
                    .pattern(listOf(Dash(20f), Gap(10f)))
            )
        }

        // Primary route — color based on risk level
        val primary    = features.getJSONObject(0)
        val pts        = orsToLatLng(primary.getJSONObject("geometry").getJSONArray("coordinates"))
        val routeColor = when (risk) {
            RouteRisk.SAFE               -> Color.parseColor("#2E7D32") // green
            RouteRisk.ROUTE_AT_RISK      -> Color.parseColor("#F9A825") // yellow
            RouteRisk.ORIGIN_AT_RISK     -> Color.parseColor("#E65100") // orange
            RouteRisk.DESTINATION_AT_RISK -> Color.parseColor("#B71C1C") // red
        }
        googleMap.addPolyline(PolylineOptions().addAll(pts).color(routeColor).width(14f))

        // Markers
        googleMap.addMarker(MarkerOptions().position(origin).title("Start")
            .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN)))
        googleMap.addMarker(MarkerOptions().position(destination).title("Destination")
            .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_BLUE)))

        // HIGH risk circles (red)
        for (h in highHazards) {
            googleMap.addCircle(CircleOptions().center(h).radius(HAZARD_RADIUS_M)
                .strokeColor(Color.RED).strokeWidth(2f).fillColor(Color.argb(60, 255, 0, 0)))
            googleMap.addMarker(MarkerOptions().position(h).title("⚠ High Risk Area")
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED)))
        }

        // FIX: MEDIUM risk circles (yellow) — shown for awareness but not avoided
        for (h in mediumHazards) {
            googleMap.addCircle(CircleOptions().center(h).radius(HAZARD_RADIUS_M)
                .strokeColor(Color.parseColor("#F9A825")).strokeWidth(2f)
                .fillColor(Color.argb(40, 249, 168, 37)))
            googleMap.addMarker(MarkerOptions().position(h).title("⚠ Medium Risk Area")
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_ORANGE)))
        }

        // Fit camera to route + all hazards
        val bounds = LatLngBounds.Builder()
        pts.forEach { bounds.include(it) }
        highHazards.forEach { bounds.include(it) }
        mediumHazards.forEach { bounds.include(it) }
        googleMap.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds.build(), 120))

        // Route summary
        val properties = primary.getJSONObject("properties")
        val distM = when {
            properties.has("summary") -> properties.getJSONObject("summary").getDouble("distance")
            properties.has("segments") -> properties.getJSONArray("segments").getJSONObject(0).getDouble("distance")
            else -> 0.0
        }
        val durSec = when {
            properties.has("summary") -> properties.getJSONObject("summary").getDouble("duration")
            properties.has("segments") -> properties.getJSONArray("segments").getJSONObject(0).getDouble("duration")
            else -> 0.0
        }
        val distTxt = if (distM >= 1000) "%.1f km".format(distM / 1000) else "%.0f m".format(distM)
        val durTxt  = if (durSec >= 3600)
            "%.0f hr %.0f min".format(durSec / 3600, (durSec % 3600) / 60)
        else "%.0f min".format(durSec / 60)

        // Route header label
        val riskLabel = when (risk) {
            RouteRisk.SAFE                -> "🟢 Safe Route"
            RouteRisk.ROUTE_AT_RISK       -> "🟡 Caution Route"
            RouteRisk.ORIGIN_AT_RISK      -> "🟠 Starting in a HIGH-risk area"
            RouteRisk.DESTINATION_AT_RISK -> "🔴 Destination is in a HIGH-risk area"
        }

        // Safety score — based on whether hazards exist and how many were avoided
        val safetyScore = when (risk) {
            RouteRisk.SAFE ->
                "🛡 Safety: High — No danger zones on this route"
            RouteRisk.ROUTE_AT_RISK ->
                "🛡 Safety: Medium — Routed around ${highHazards.size} HIGH-risk area(s)"
            RouteRisk.ORIGIN_AT_RISK ->
                "🛡 Safety: Low — Your starting point is in a danger zone"
            RouteRisk.DESTINATION_AT_RISK ->
                "🛡 Safety: Low — Your destination is in a danger zone"
        }

        txtRouteResult.text = "$riskLabel\n📏 $distTxt  •  🕒 $durTxt\n$safetyScore"
    }

    // ── Helpers ───────────────────────────────────────────────────────────────
    private fun isNearHazard(point: LatLng, hazards: List<LatLng>, radius: Double = HAZARD_RADIUS_M): Boolean {
        val results = FloatArray(1)
        return hazards.any { h ->
            android.location.Location.distanceBetween(
                point.latitude, point.longitude,
                h.latitude, h.longitude,
                results
            )
            results[0] <= radius
        }
    }

    private fun orsToLatLng(coords: JSONArray): List<LatLng> =
        (0 until coords.length()).map {
            val c = coords.getJSONArray(it)
            LatLng(c.getDouble(1), c.getDouble(0))
        }

    private fun setLoading(loading: Boolean) {
        progressBar.visibility     = if (loading) View.VISIBLE else View.GONE
        btnFindRoute.isEnabled     = !loading
        btnFindRoute.text          = if (loading) "Finding route…" else "Find Safe Route"
        editOrigin.isEnabled       = !loading
        editDestination.isEnabled  = !loading
        btnUseMyLocation.isEnabled = !loading
    }
}