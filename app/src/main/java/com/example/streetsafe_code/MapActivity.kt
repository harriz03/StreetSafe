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
import android.widget.ArrayAdapter
import android.widget.ListView
import com.google.android.material.bottomsheet.BottomSheetDialog


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

        FirebaseFirestore.getInstance()
            .collection("reports")
            .whereEqualTo("status", "ACTIVE")
            .get()
            .addOnSuccessListener { docs ->

                val groupedReports =
                    mutableMapOf<String, MutableList<Report>>()

                for (doc in docs) {

                    val report =
                        doc.toObject(Report::class.java)

                    val location = report.location

                    if (groupedReports.containsKey(location)) {

                        groupedReports[location]?.add(report)

                    } else {

                        groupedReports[location] =
                            mutableListOf(report)
                    }
                }

                for ((location, reports) in groupedReports) {

                    val firstReport = reports[0]

                    val point = LatLng(
                        firstReport.latitude,
                        firstReport.longitude
                    )

                    var highestRisk = "LOW"

                    for (report in reports) {

                        if (report.riskLevel == "HIGH") {
                            highestRisk = "HIGH"
                            break
                        }

                        if (report.riskLevel == "MEDIUM"
                            && highestRisk != "HIGH"
                        ) {

                            highestRisk = "MEDIUM"
                        }
                    }

                    val markerColor = when (highestRisk) {

                        "HIGH" ->
                            BitmapDescriptorFactory.HUE_RED

                        "MEDIUM" ->
                            BitmapDescriptorFactory.HUE_ORANGE

                        else ->
                            BitmapDescriptorFactory.HUE_GREEN
                    }

                    val marker = googleMap.addMarker(

                        MarkerOptions()
                            .position(point)
                            .title(location)
                            .icon(
                                BitmapDescriptorFactory
                                    .defaultMarker(markerColor)
                            )
                    )

                    marker?.tag = reports


                    googleMap.setOnMarkerClickListener { marker ->

                        val reports =
                            marker.tag as MutableList<Report>

                        val dialog =
                            BottomSheetDialog(this)

                        val view =
                            layoutInflater.inflate(
                                R.layout.dialog_incidents,
                                null
                            )

                        val txtLocation =
                            view.findViewById<TextView>(
                                R.id.txtDialogLocation
                            )

                        val listIncidents =
                            view.findViewById<ListView>(
                                R.id.listIncidents
                            )

                        txtLocation.text = marker.title

                        val adapter = object : ArrayAdapter<Report>(
                            this,
                            R.layout.item_incident,
                            reports
                        ){

                            override fun getView(
                                position: Int,
                                convertView: android.view.View?,
                                parent: android.view.ViewGroup
                            ): android.view.View {

                                val view = layoutInflater.inflate(
                                    R.layout.item_incident,
                                    parent,
                                    false
                                )

                                val txtIncident =
                                    view.findViewById<TextView>(
                                        R.id.txtIncident
                                    )

                                val riskColor =
                                    view.findViewById<android.view.View>(
                                        R.id.viewRiskColor
                                    )

                                val report = reports[position]

                                txtIncident.text =
                                    "${report.incidentType} - ${report.riskLevel}"

                                when(report.riskLevel){

                                    "HIGH" -> {
                                        riskColor.setBackgroundColor(
                                            android.graphics.Color.RED
                                        )
                                    }

                                    "MEDIUM" -> {
                                        riskColor.setBackgroundColor(
                                            android.graphics.Color.parseColor("#FF9800")
                                        )
                                    }

                                    else -> {
                                        riskColor.setBackgroundColor(
                                            android.graphics.Color.GREEN
                                        )
                                    }
                                }

                                return view
                            }
                        }

                        listIncidents.adapter = adapter

                        dialog.setContentView(view)
                        dialog.show()

                        true
                    }
                }
            }
    }
}

