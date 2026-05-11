package com.example.streetsafe_code

import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.*
import com.google.android.material.snackbar.Snackbar

class SafeRouteActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var googleMap: GoogleMap

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_safe_route)

        val places = listOf("Ayala", "Cebu IT Park", "Colon", "Fuente", "SM Cebu")
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, places)

        val spinnerStart       = findViewById<Spinner>(R.id.spinnerStart)
        val spinnerDestination = findViewById<Spinner>(R.id.spinnerDestination)
        spinnerStart.adapter       = adapter
        spinnerDestination.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, places)

        val mapFragment = supportFragmentManager.findFragmentById(R.id.safeRouteMap) as SupportMapFragment
        mapFragment.getMapAsync(this)

        val rootView = findViewById<View>(android.R.id.content)

        findViewById<Button>(R.id.btnFindRoute).setOnClickListener {
            val start       = spinnerStart.selectedItem.toString()
            val destination = spinnerDestination.selectedItem.toString()

            if (start == destination) {
                Snackbar.make(rootView, "Start and destination cannot be the same.", Snackbar.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            showSafeRoute(start, destination, rootView)
        }
    }

    override fun onMapReady(map: GoogleMap) {
        googleMap = map
        googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(10.3167, 123.8907), 12f))
    }

    private fun showSafeRoute(start: String, destination: String, rootView: View) {
        googleMap.clear()

        val locations = mapOf(
            "Ayala"        to LatLng(10.3176, 123.9053),
            "Cebu IT Park" to LatLng(10.3295, 123.9067),
            "Colon"        to LatLng(10.2933, 123.9019),
            "Fuente"       to LatLng(10.3100, 123.8936),
            "SM Cebu"      to LatLng(10.3116, 123.9181)
        )

        val startPoint = locations[start]       ?: return
        val endPoint   = locations[destination] ?: return

        googleMap.addMarker(MarkerOptions().position(startPoint).title("Start: $start")
            .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN)))
        googleMap.addMarker(MarkerOptions().position(endPoint).title("Destination: $destination")
            .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_BLUE)))

        googleMap.addPolyline(PolylineOptions().addAll(listOf(startPoint, endPoint))
            .color(android.graphics.Color.GREEN).width(12f))

        val bounds = com.google.android.gms.maps.model.LatLngBounds.builder()
            .include(startPoint).include(endPoint).build()
        googleMap.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, 120))

        findViewById<TextView>(R.id.txtRouteResult).text =
            "Recommended Safe Route:\n$start \u2192 $destination"

        Snackbar.make(rootView, "\u2713 Safe route found: $start \u2192 $destination", Snackbar.LENGTH_SHORT).show()
    }
}
