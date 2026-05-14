package com.example.streetsafe_code

import android.content.Intent
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatButton
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.firebase.firestore.Query
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.util.Calendar

class HomeActivity : AppCompatActivity(), OnMapReadyCallback {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)

        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.homeMapFragment) as SupportMapFragment
        mapFragment.getMapAsync(this)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.homeRoot)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // Greeting
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val greeting = when {
            hour < 12 -> "Good morning,"
            hour < 17 -> "Good afternoon,"
            else      -> "Good evening,"
        }
        findViewById<TextView>(R.id.txtGreeting).text = greeting

        // Load username from Firestore
        val uid = FirebaseAuth.getInstance().currentUser?.uid
        if (uid != null) {
            FirebaseFirestore.getInstance()
                .collection("users").document(uid)
                .get()
                .addOnSuccessListener { doc ->
                    val username = doc.getString("username") ?: "User"
                    findViewById<TextView>(R.id.txtUsername).text = username
                }
        }

        // Navigation
        findViewById<AppCompatButton>(R.id.btnReportIncident).setOnClickListener {
            startActivity(Intent(this, ReportActivity::class.java))
        }
        findViewById<TextView>(R.id.txtViewFullMap).setOnClickListener {
            startActivity(Intent(this, MapActivity::class.java))
            finish()
        }
        findViewById<TextView>(R.id.txtViewAll).setOnClickListener {
            startActivity(Intent(this, AllReportsActivity::class.java))
            finish()
        }
        findViewById<LinearLayout>(R.id.cardSafeRoute).setOnClickListener {
            startActivity(Intent(this, SafeRouteActivity::class.java))
        }
        findViewById<TextView>(R.id.txtViewProfile).setOnClickListener {
            startActivity(Intent(this, ProfileActivity::class.java))
        }

        // Load recent reports immediately on open (was missing before)
        loadRecentReports()
    }

    override fun onResume() {
        super.onResume()
        loadRecentReports()
    }

    // ── Recent reports — only ACTIVE, ordered by newest first ─────────────────
    private fun loadRecentReports() {
        val type1 = findViewById<TextView>(R.id.txtRecentType1)
        val loc1  = findViewById<TextView>(R.id.txtRecentLocation1)
        val time1 = findViewById<TextView>(R.id.txtRecentTime1)
        val type2 = findViewById<TextView>(R.id.txtRecentType2)
        val loc2  = findViewById<TextView>(R.id.txtRecentLocation2)
        val time2 = findViewById<TextView>(R.id.txtRecentTime2)

        FirebaseFirestore.getInstance()
            .collection("reports")
            .whereEqualTo("status", "ACTIVE")   // only show approved reports
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(2)
            .get()
            .addOnSuccessListener { docs ->
                val reports = docs.documents

                if (reports.isNotEmpty()) {
                    type1.text = reports[0].getString("incidentType") ?: "-"
                    loc1.text  = reports[0].getString("location") ?: "-"
                    time1.text = formatDate(reports[0].getLong("timestamp") ?: 0L)
                } else {
                    type1.text = "No reports yet"
                    loc1.text  = "-"
                    time1.text = ""
                }

                if (reports.size >= 2) {
                    type2.text = reports[1].getString("incidentType") ?: "-"
                    loc2.text  = reports[1].getString("location") ?: "-"
                    time2.text = formatDate(reports[1].getLong("timestamp") ?: 0L)
                } else {
                    type2.text = "-"
                    loc2.text  = ""
                    time2.text = ""
                }
            }
            .addOnFailureListener {
                type1.text = "Failed to load"
                type2.text = "-"
            }
    }

    private fun formatDate(ms: Long): String {
        if (ms == 0L) return "-"
        return SimpleDateFormat("MMM dd hh:mm a", Locale.getDefault()).format(Date(ms))
    }

    // ── Mini map — shows ACTIVE incident markers ───────────────────────────────
    override fun onMapReady(googleMap: GoogleMap) {
        val cebu = LatLng(10.3167, 123.8907)
        googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(cebu, 11f))

        FirebaseFirestore.getInstance()
            .collection("reports")
            .whereEqualTo("status", "ACTIVE")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .get()
            .addOnSuccessListener { documents ->
                for (doc in documents) {
                    val lat      = doc.getDouble("latitude")  ?: continue
                    val lng      = doc.getDouble("longitude") ?: continue
                    val risk     = doc.getString("riskLevel") ?: "LOW"
                    val incident = doc.getString("incidentType") ?: "Incident"

                    val color = when (risk) {
                        "HIGH"   -> BitmapDescriptorFactory.HUE_RED
                        "MEDIUM" -> BitmapDescriptorFactory.HUE_ORANGE
                        else     -> BitmapDescriptorFactory.HUE_GREEN
                    }

                    googleMap.addMarker(
                        MarkerOptions()
                            .position(LatLng(lat, lng))
                            .title(incident)
                            .icon(BitmapDescriptorFactory.defaultMarker(color))
                    )
                }
            }
    }
}