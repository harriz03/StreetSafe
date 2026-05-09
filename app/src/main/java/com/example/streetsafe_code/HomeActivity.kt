package com.example.streetsafe_code

import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatButton
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.firebase.firestore.Query
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.example.streetsafe_code.R
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.material.bottomnavigation.BottomNavigationView
import java.util.Calendar
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class HomeActivity : AppCompatActivity(), OnMapReadyCallback {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)

        val mapFragment =
            supportFragmentManager.findFragmentById(
                R.id.homeMapFragment
            ) as SupportMapFragment

        mapFragment.getMapAsync(this)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.homeRoot)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val greeting = when {
            hour < 12 -> "Good morning,"
            hour < 17 -> "Good afternoon,"
            else      -> "Good evening,"
        }
        findViewById<TextView>(R.id.txtGreeting).text = greeting

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

        findViewById<android.view.View>(R.id.cardSafeRoute).setOnClickListener {
            startActivity(Intent(this, MapActivity::class.java))
            finish()
        }

        findViewById<TextView>(R.id.txtViewProfile).setOnClickListener {

            startActivity(Intent(this, ProfileActivity::class.java))

        }
    }

    private fun loadRecentReports() {

        val type1 = findViewById<TextView>(R.id.txtRecentType1)
        val loc1  = findViewById<TextView>(R.id.txtRecentLocation1)
        val time1 = findViewById<TextView>(R.id.txtRecentTime1)

        val type2 = findViewById<TextView>(R.id.txtRecentType2)
        val loc2  = findViewById<TextView>(R.id.txtRecentLocation2)
        val time2 = findViewById<TextView>(R.id.txtRecentTime2)

        val uid = FirebaseAuth.getInstance().currentUser?.uid

        FirebaseFirestore.getInstance()
            .collection("reports")
            .limit(3)
            .orderBy(
                "timestamp",
                Query.Direction.DESCENDING
            )
            .get()
            .addOnSuccessListener { docs ->

                val reports = docs.documents

                if(reports.size >=1){

                    type1.text =
                        reports[0].getString("incidentType")
                            ?: "-"

                    loc1.text =
                        reports[0].getString("location")
                            ?: "-"

                    val t =
                        reports[0]
                            .getLong("timestamp")
                            ?: 0

                    time1.text =
                        formatDate(t)
                }

                if(reports.size >=2){

                    type2.text =
                        reports[1].getString("incidentType")
                            ?: "-"

                    loc2.text =
                        reports[1].getString("location")
                            ?: "-"

                    val t2 =
                        reports[1]
                            .getLong("timestamp")
                            ?: 0

                    time2.text =
                        formatDate(t2)
                }
            }
    }

    private fun formatDate(ms: Long): String {

        val formatter =
            SimpleDateFormat(
                "MMM dd hh:mm a",
                Locale.getDefault()
            )

        return formatter.format(
            Date(ms)
        )
    }

    override fun onResume() {
        super.onResume()

        loadRecentReports()
    }

    override fun onMapReady(googleMap: GoogleMap) {

        val cebu = LatLng(10.3167,123.8907)

        googleMap.moveCamera(
            CameraUpdateFactory.newLatLngZoom(
                cebu,
                11f
            )
        )

        FirebaseFirestore.getInstance()
            .collection("reports")
            .whereEqualTo("status", "ACTIVE")
            .orderBy(
                "timestamp",
                Query.Direction.DESCENDING
            )
            .get()
            .addOnSuccessListener { documents ->

                for(doc in documents){

                    val lat =
                        doc.getDouble("latitude")
                            ?: continue

                    val lng =
                        doc.getDouble("longitude")
                            ?: continue

                    val risk =
                        doc.getString("riskLevel")
                            ?: "LOW"

                    val incident =
                        doc.getString("incidentType")
                            ?: "Incident"

                    val color =
                        when(risk){
                            "HIGH" ->
                                BitmapDescriptorFactory.HUE_RED

                            "MEDIUM" ->
                                BitmapDescriptorFactory.HUE_ORANGE

                            else ->
                                BitmapDescriptorFactory.HUE_GREEN
                        }

                    googleMap.addMarker(
                        MarkerOptions()
                            .position(LatLng(lat,lng))
                            .title(incident)
                            .icon(
                                BitmapDescriptorFactory
                                    .defaultMarker(color)
                            )
                    )
                }
            }
    }
}