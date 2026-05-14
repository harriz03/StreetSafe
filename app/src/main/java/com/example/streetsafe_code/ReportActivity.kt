package com.example.streetsafe_code

import android.Manifest
import android.content.pm.PackageManager
import android.location.Geocoder
import android.net.Uri
import android.os.Bundle
import android.app.AlertDialog
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatButton
import androidx.core.app.ActivityCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.model.LatLng
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.*
import org.json.JSONArray
import java.net.URL
import java.net.URLEncoder
import java.util.Locale
import com.cloudinary.Cloudinary
import com.cloudinary.utils.ObjectUtils
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import java.io.IOException

class ReportActivity : AppCompatActivity() {

    // ── State ────────────────────────────────────────────────────────────────
    private var pickedLatLng: LatLng?       = null
    private var pickedLocationName: String  = ""
    private var selectedPhotoUri: Uri?      = null
    private var locationSearchJob: Job?     = null

    // ── Views ────────────────────────────────────────────────────────────────
    private lateinit var editLocation: EditText
    private lateinit var btnUseMyLocation: AppCompatButton
    private lateinit var btnAddPhoto: AppCompatButton
    private lateinit var imagePreview: ImageView
    private lateinit var txtPhotoHint: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var btnSubmitReport: AppCompatButton

    // ── Location ─────────────────────────────────────────────────────────────
    private lateinit var fusedLocationClient: FusedLocationProviderClient

    // ── Scope ─────────────────────────────────────────────────────────────────
    private val activityScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    companion object {
        private const val LOCATION_PERMISSION_RC = 101
        private const val SEARCH_DELAY_MS        = 600L
    }

    // ── Photo picker ─────────────────────────────────────────────────────────
    private val pickImageLauncher =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            if (uri != null) {
                selectedPhotoUri = uri
                imagePreview.setImageURI(uri)
                imagePreview.visibility = View.VISIBLE
                txtPhotoHint.text = "Photo added ✓"
                txtPhotoHint.setTextColor(0xFF2E7D32.toInt())
            }
        }

    // ── Lifecycle ────────────────────────────────────────────────────────────
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_report)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.reportRoot)) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        bindViews()
        setupIncidentTypeSpinner()
        setupLocationSearch()
        setupUseMyLocation()
        setupPhotoButton()
        setupSubmitButton()
        setupBackButton()
    }

    override fun onDestroy() {
        super.onDestroy()
        activityScope.cancel()
    }

    // ── View binding ─────────────────────────────────────────────────────────
    private fun bindViews() {
        editLocation     = findViewById(R.id.editLocation)
        btnUseMyLocation = findViewById(R.id.btnUseMyLocation)
        btnAddPhoto      = findViewById(R.id.btnAddPhoto)
        imagePreview     = findViewById(R.id.imagePreview)
        txtPhotoHint     = findViewById(R.id.txtPhotoHint)
        progressBar      = findViewById(R.id.progressBar)
        btnSubmitReport  = findViewById(R.id.btnSubmitReport)
    }

    // ── Incident type spinner ─────────────────────────────────────────────────
    private fun setupIncidentTypeSpinner() {
        val types = listOf("Theft", "Harassment", "Poor Lighting", "Suspicious Activity", "Road Accident")
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, types)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        findViewById<Spinner>(R.id.spinnerIncidentType).adapter = adapter
    }

    // ── Nominatim location search (replaces broken Places API) ───────────────
    private fun setupLocationSearch() {
        // Make field editable (was set to non-focusable in layout)
        editLocation.isFocusableInTouchMode = true
        editLocation.isFocusable = true
        editLocation.inputType = android.text.InputType.TYPE_CLASS_TEXT

        editLocation.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun afterTextChanged(s: Editable?) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val query = s?.toString()?.trim() ?: return
                // Don't re-search if user just selected from list
                if (query == pickedLocationName && pickedLatLng != null) return
                pickedLatLng = null

                locationSearchJob?.cancel()
                if (query.length < 3) return
                locationSearchJob = activityScope.launch {
                    delay(SEARCH_DELAY_MS)
                    if (editLocation.hasFocus()) searchLocation(query)
                }
            }
        })
    }

    private fun searchLocation(query: String) {
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
                if (!editLocation.hasFocus()) return@launch

                val names = Array(results.length()) { i ->
                    results.getJSONObject(i).getString("display_name")
                        .split(",").take(3).joinToString(", ")
                }

                AlertDialog.Builder(this@ReportActivity)
                    .setTitle("Select Location")
                    .setItems(names) { _, index ->
                        val place = results.getJSONObject(index)
                        val lat   = place.getString("lat").toDouble()
                        val lng   = place.getString("lon").toDouble()
                        pickedLatLng       = LatLng(lat, lng)
                        pickedLocationName = names[index]
                        editLocation.setText(pickedLocationName)
                        editLocation.clearFocus()
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
                    this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), LOCATION_PERMISSION_RC
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
        if (requestCode == LOCATION_PERMISSION_RC &&
            grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED
        ) fetchCurrentLocation()
        else Toast.makeText(this, "Location permission is required", Toast.LENGTH_SHORT).show()
    }

    private fun fetchCurrentLocation() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) return

        btnUseMyLocation.isEnabled = false
        btnUseMyLocation.text      = "Getting location…"

        fusedLocationClient.lastLocation.addOnSuccessListener { location ->
            btnUseMyLocation.isEnabled = true
            btnUseMyLocation.text      = "📍 Use My Location"

            if (location == null) {
                Toast.makeText(this, "Could not get location. Try again.", Toast.LENGTH_SHORT).show()
                return@addOnSuccessListener
            }

            pickedLatLng = LatLng(location.latitude, location.longitude)
            try {
                @Suppress("DEPRECATION")
                val results = Geocoder(this, Locale.getDefault())
                    .getFromLocation(location.latitude, location.longitude, 1)
                pickedLocationName = results?.firstOrNull()?.getAddressLine(0)
                    ?: "%.5f, %.5f".format(location.latitude, location.longitude)
            } catch (e: Exception) {
                pickedLocationName = "%.5f, %.5f".format(location.latitude, location.longitude)
            }

            editLocation.setText(pickedLocationName)
            editLocation.clearFocus()
            Toast.makeText(this, "Location set to your position", Toast.LENGTH_SHORT).show()
        }.addOnFailureListener {
            btnUseMyLocation.isEnabled = true
            btnUseMyLocation.text      = "📍 Use My Location"
            Toast.makeText(this, "Failed to get location", Toast.LENGTH_SHORT).show()
        }
    }

    // ── Photo picker ──────────────────────────────────────────────────────────
    private fun setupPhotoButton() {
        btnAddPhoto.setOnClickListener { pickImageLauncher.launch("image/*") }
    }

    // ── Submit ────────────────────────────────────────────────────────────────
    private fun setupSubmitButton() {
        btnSubmitReport.setOnClickListener { validateAndSubmit() }
    }

    private fun validateAndSubmit() {
        val incidentType = findViewById<Spinner>(R.id.spinnerIncidentType).selectedItem.toString()
        val description  = findViewById<EditText>(R.id.editDescription).text.toString().trim()

        if (pickedLatLng == null || pickedLocationName.isEmpty()) {
            Toast.makeText(this, "Please select a location from the suggestions or use your current location", Toast.LENGTH_LONG).show()
            return
        }
        if (description.isEmpty()) {
            Toast.makeText(this, "Please enter a description", Toast.LENGTH_SHORT).show()
            return
        }
        if (selectedPhotoUri == null) {
            Toast.makeText(this, "Please add at least one photo as proof", Toast.LENGTH_SHORT).show()
            return
        }

        val uid = FirebaseAuth.getInstance().currentUser?.uid
        if (uid == null) {
            Toast.makeText(this, "You must be logged in to report", Toast.LENGTH_SHORT).show()
            return
        }

        setLoading(true)
        uploadPhotoThenSaveReport(uid, incidentType, description)
    }

    private fun uploadPhotoThenSaveReport(
        uid: String,
        incidentType: String,
        description: String
    ) {
        activityScope.launch(Dispatchers.IO) {
            try {

                val inputStream = contentResolver.openInputStream(selectedPhotoUri!!)
                val bytes = inputStream!!.readBytes()

                val requestBody = MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart("file", "image.jpg",
                        RequestBody.create("image/*".toMediaTypeOrNull(), bytes)
                    )
                    .addFormDataPart("upload_preset", "StreetSafe")
                    .build()

                val request = Request.Builder()
                    .url("https://api.cloudinary.com/v1_1/dn7ou4nob/image/upload")
                    .post(requestBody)
                    .build()

                val client = OkHttpClient()

                val response = client.newCall(request).execute()
                val responseBody = response.body?.string()

                val json = org.json.JSONObject(responseBody!!)
                val imageUrl = json.getString("secure_url")

                withContext(Dispatchers.Main) {
                    saveReportToFirestore(uid, incidentType, description, imageUrl)
                }

            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    setLoading(false)
                    Toast.makeText(
                        this@ReportActivity,
                        "Upload failed: ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    // ── Writes to Firestore "reports" collection ──────────────────────────────
    // Fields written here match exactly what SafeRouteActivity reads:
    //   latitude, longitude, riskLevel, status
    private fun saveReportToFirestore(
        uid: String, incidentType: String, description: String, photoUrl: String
    ) {
        val severity  = when (incidentType) {
            "Theft", "Harassment"                      -> 3
            "Suspicious Activity", "Road Accident"     -> 2
            else                                       -> 1  // Poor Lighting
        }
        val riskLevel = when {
            severity >= 3 -> "HIGH"
            severity >= 2 -> "MEDIUM"
            else          -> "LOW"
        }

        val report = hashMapOf(
            "userId"       to uid,
            "incidentType" to incidentType,
            "location"     to pickedLocationName,
            "description"  to description,
            "latitude"     to pickedLatLng!!.latitude,
            "longitude"    to pickedLatLng!!.longitude,
            "riskLevel"    to riskLevel,
            "photoUrl"     to photoUrl,
            "status"       to "PENDING",   // admin sets to ACTIVE to show on map
            "timestamp"    to System.currentTimeMillis()
        )

        FirebaseFirestore.getInstance().collection("reports").add(report)
            .addOnSuccessListener {
                FirebaseFirestore.getInstance().collection("users").document(uid)
                    .update("reportsCount", FieldValue.increment(1))

                setLoading(false)
                Toast.makeText(
                    this,
                    "Report submitted! It will appear on the map after admin review.",
                    Toast.LENGTH_LONG
                ).show()
                finish()
            }
            .addOnFailureListener { e ->
                setLoading(false)
                Toast.makeText(this, "Failed to submit: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────
    private fun setLoading(loading: Boolean) {
        progressBar.visibility     = if (loading) View.VISIBLE else View.GONE
        btnSubmitReport.isEnabled  = !loading
        btnSubmitReport.text       = if (loading) "Submitting…" else getString(R.string.btn_submit_report)
        btnUseMyLocation.isEnabled = !loading
        btnAddPhoto.isEnabled      = !loading
    }

    private val cloudinary = Cloudinary(
        ObjectUtils.asMap(
            "cloud_name", "dn7ou4nob",
            "secure", true
        )
    )

    private fun setupBackButton() {
        findViewById<AppCompatButton>(R.id.btnBackFromReport).setOnClickListener { finish() }
    }
}