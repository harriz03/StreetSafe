package com.example.streetsafe_code

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Geocoder
import android.net.Uri
import android.os.Bundle
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
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.api.model.RectangularBounds
import com.google.android.libraries.places.widget.Autocomplete
import com.google.android.libraries.places.widget.AutocompleteActivity
import com.google.android.libraries.places.widget.model.AutocompleteActivityMode
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import java.util.Locale

class ReportActivity : AppCompatActivity() {

    // ── State ────────────────────────────────────────────────────────────────
    private var pickedLatLng: LatLng? = null
    private var pickedLocationName: String = ""
    private var selectedPhotoUri: Uri? = null

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

    companion object {
        private const val AUTOCOMPLETE_REQUEST_CODE = 100
        private const val LOCATION_PERMISSION_REQUEST_CODE = 101
        // Cebu bounding box — restricts autocomplete results to Cebu island
        private val CEBU_BOUNDS = RectangularBounds.newInstance(
            LatLng(9.8,  123.6),  // SW corner
            LatLng(10.8, 124.3)   // NE corner
        )
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

        // Initialize Places SDK (safe to call multiple times)
        if (!Places.isInitialized()) {
            Places.initialize(applicationContext, getString(R.string.google_maps_key))
        }

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
        val types = listOf(
            "Theft", "Harassment", "Poor Lighting",
            "Suspicious Activity", "Road Accident"
        )
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, types)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        findViewById<Spinner>(R.id.spinnerIncidentType).adapter = adapter
    }

    // ── Places Autocomplete ───────────────────────────────────────────────────
    private fun setupLocationSearch() {
        // Open the Google Places overlay when the user taps the location field
        editLocation.setOnClickListener { launchAutocomplete() }
        editLocation.isFocusable = false  // prevent keyboard on tap
    }

    private fun launchAutocomplete() {
        val fields = listOf(Place.Field.NAME, Place.Field.LAT_LNG, Place.Field.ADDRESS)
        val intent = Autocomplete
            .IntentBuilder(AutocompleteActivityMode.OVERLAY, fields)
            .setLocationRestriction(CEBU_BOUNDS)
            .build(this)
        @Suppress("DEPRECATION")
        startActivityForResult(intent, AUTOCOMPLETE_REQUEST_CODE)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == AUTOCOMPLETE_REQUEST_CODE) {
            when (resultCode) {
                Activity.RESULT_OK -> {
                    val place = Autocomplete.getPlaceFromIntent(data!!)
                    pickedLatLng        = place.latLng
                    pickedLocationName  = place.name ?: place.address ?: ""
                    editLocation.setText(pickedLocationName)
                }
                AutocompleteActivity.RESULT_ERROR -> {
                    val status = Autocomplete.getStatusFromIntent(data!!)
                    Toast.makeText(this, "Error: ${status.statusMessage}", Toast.LENGTH_SHORT).show()
                }
                Activity.RESULT_CANCELED -> { /* user dismissed */ }
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
                    LOCATION_PERMISSION_REQUEST_CODE
                )
            } else {
                fetchCurrentLocation()
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE &&
            grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED
        ) {
            fetchCurrentLocation()
        } else {
            Toast.makeText(this, "Location permission is required", Toast.LENGTH_SHORT).show()
        }
    }

    private fun fetchCurrentLocation() {
        if (ActivityCompat.checkSelfPermission(
                this, Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) return

        btnUseMyLocation.isEnabled = false
        btnUseMyLocation.text = "Getting location…"

        fusedLocationClient.lastLocation.addOnSuccessListener { location ->
            btnUseMyLocation.isEnabled = true
            btnUseMyLocation.text = "Use My Location"

            if (location == null) {
                Toast.makeText(this, "Could not get location. Try again.", Toast.LENGTH_SHORT).show()
                return@addOnSuccessListener
            }

            pickedLatLng = LatLng(location.latitude, location.longitude)

            // Reverse-geocode to a human-readable address
            try {
                val geocoder = Geocoder(this, Locale.getDefault())
                @Suppress("DEPRECATION")
                val results = geocoder.getFromLocation(location.latitude, location.longitude, 1)
                pickedLocationName = results?.firstOrNull()?.getAddressLine(0)
                    ?: "%.5f, %.5f".format(location.latitude, location.longitude)
            } catch (e: Exception) {
                pickedLocationName = "%.5f, %.5f".format(location.latitude, location.longitude)
            }

            editLocation.setText(pickedLocationName)
            Toast.makeText(this, "Location set to your position", Toast.LENGTH_SHORT).show()
        }.addOnFailureListener {
            btnUseMyLocation.isEnabled = true
            btnUseMyLocation.text = "Use My Location"
            Toast.makeText(this, "Failed to get location", Toast.LENGTH_SHORT).show()
        }
    }

    // ── Photo picker ──────────────────────────────────────────────────────────
    private fun setupPhotoButton() {
        btnAddPhoto.setOnClickListener {
            pickImageLauncher.launch("image/*")
        }
    }

    // ── Submit ────────────────────────────────────────────────────────────────
    private fun setupSubmitButton() {
        btnSubmitReport.setOnClickListener { validateAndSubmit() }
    }

    private fun validateAndSubmit() {
        val incidentType = findViewById<Spinner>(R.id.spinnerIncidentType).selectedItem.toString()
        val description  = findViewById<EditText>(R.id.editDescription).text.toString().trim()

        // ── Validation ───────────────────────────────────────────────────────
        if (pickedLatLng == null || pickedLocationName.isEmpty()) {
            Toast.makeText(this, "Please select a location", Toast.LENGTH_SHORT).show()
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

    // ── Upload photo → save report ────────────────────────────────────────────
    private fun uploadPhotoThenSaveReport(
        uid: String,
        incidentType: String,
        description: String
    ) {
        val fileName = "reports/${uid}_${System.currentTimeMillis()}.jpg"
        val storageRef = FirebaseStorage.getInstance().reference.child(fileName)

        storageRef.putFile(selectedPhotoUri!!)
            .addOnProgressListener { snapshot ->
                val progress = (100.0 * snapshot.bytesTransferred / snapshot.totalByteCount).toInt()
                progressBar.progress = progress
            }
            .continueWithTask { task ->
                if (!task.isSuccessful) throw task.exception!!
                storageRef.downloadUrl
            }
            .addOnSuccessListener { downloadUrl ->
                saveReportToFirestore(uid, incidentType, description, downloadUrl.toString())
            }
            .addOnFailureListener { e ->
                setLoading(false)
                Toast.makeText(this, "Photo upload failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }

    // ── Save report document ──────────────────────────────────────────────────
    private fun saveReportToFirestore(
        uid: String,
        incidentType: String,
        description: String,
        photoUrl: String
    ) {
        val lat = pickedLatLng!!.latitude
        val lng = pickedLatLng!!.longitude

        val severity = when (incidentType) {
            "Theft", "Harassment"              -> 3
            "Suspicious Activity", "Road Accident" -> 2
            else                               -> 1   // Poor Lighting
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
            "latitude"     to lat,
            "longitude"    to lng,
            "riskLevel"    to riskLevel,
            "photoUrl"     to photoUrl,
            // PENDING — admin must approve before it appears on the map
            "status"       to "PENDING",
            "timestamp"    to System.currentTimeMillis()
        )

        FirebaseFirestore.getInstance()
            .collection("reports")
            .add(report)
            .addOnSuccessListener {
                // Increment the user's report counter
                FirebaseFirestore.getInstance()
                    .collection("users")
                    .document(uid)
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
        progressBar.visibility  = if (loading) View.VISIBLE else View.GONE
        btnSubmitReport.isEnabled = !loading
        btnSubmitReport.text    = if (loading) "Submitting…" else getString(R.string.btn_submit_report)
        btnUseMyLocation.isEnabled = !loading
        btnAddPhoto.isEnabled      = !loading
    }

    private fun setupBackButton() {
        findViewById<AppCompatButton>(R.id.btnBackFromReport).setOnClickListener { finish() }
    }
}