package com.example.streetsafe_code

import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.streetsafe_code.HomeActivity
import com.example.streetsafe_code.R
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.google.firebase.firestore.FirebaseFirestore


class MapActivity : AppCompatActivity(), OnMapReadyCallback {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_map)

        val mapFragment =
            supportFragmentManager.findFragmentById(R.id.mapFragment) as SupportMapFragment

        mapFragment.getMapAsync(this)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.mapRoot)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        val backBtn = findViewById<TextView>(R.id.btnBackFromMap)

        backBtn.setOnClickListener {

            val intent = Intent(this, HomeActivity::class.java)
            startActivity(intent)

            finish()
        }
    }

    override fun onMapReady(googleMap: GoogleMap) {

        val cebu = LatLng(10.3167, 123.8907)

        googleMap.moveCamera(
            CameraUpdateFactory.newLatLngZoom(cebu, 12f)
        )

        FirebaseFirestore.getInstance().collection("reports").get()
            .addOnSuccessListener { documents ->

                for (doc in documents) {

                    val lat = doc.getDouble("latitude") ?: continue
                    val lng = doc.getDouble("longitude") ?: continue

                    val incident = doc.getString("incidentType") ?: "Incident"

                    val risk = doc.getString("riskLevel") ?: "LOW"

                    val point = LatLng(lat, lng)

                    val markerColor = when (risk) {

                        "HIGH" -> BitmapDescriptorFactory.HUE_RED

                        "MEDIUM" -> BitmapDescriptorFactory.HUE_ORANGE

                        else -> BitmapDescriptorFactory.HUE_GREEN
                    }

                    googleMap.addMarker(
                        MarkerOptions().position(point).title(incident).icon(
                                BitmapDescriptorFactory.defaultMarker(markerColor)
                            )
                    )
                }
            }
    }
}
