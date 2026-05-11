package com.example.streetsafe_code

import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.Spinner
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatButton
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import android.app.ProgressDialog

class ReportActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_report)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.reportRoot)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        val incidentTypes = listOf("Theft", "Harassment", "Poor Lighting", "Suspicious Activity", "Road Accident")
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, incidentTypes)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        findViewById<Spinner>(R.id.spinnerIncidentType).adapter = adapter

        val rootView = findViewById<View>(R.id.reportRoot)

        findViewById<AppCompatButton>(R.id.btnSubmitReport).setOnClickListener {
            val incidentType = findViewById<Spinner>(R.id.spinnerIncidentType).selectedItem.toString()
            val location     = findViewById<EditText>(R.id.editLocation).text.toString().trim()
            val description  = findViewById<EditText>(R.id.editDescription).text.toString().trim()

            if (location.isEmpty()) {
                findViewById<EditText>(R.id.editLocation).error = "Location is required"
                findViewById<EditText>(R.id.editLocation).requestFocus()
                return@setOnClickListener
            }
            if (description.isEmpty()) {
                findViewById<EditText>(R.id.editDescription).error = "Description is required"
                findViewById<EditText>(R.id.editDescription).requestFocus()
                return@setOnClickListener
            }

            val uid = FirebaseAuth.getInstance().currentUser?.uid
            if (uid == null) {
                Snackbar.make(rootView, "You must be logged in to submit a report.", Snackbar.LENGTH_LONG).show()
                return@setOnClickListener
            }

            // Confirmation dialog before submitting
            AlertDialog.Builder(this)
                .setTitle("Submit Report")
                .setMessage("Submit a \"$incidentType\" report at $location?")
                .setPositiveButton("Submit") { _, _ ->
                    submitReport(uid, incidentType, location, description, rootView)
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        findViewById<AppCompatButton>(R.id.btnBackFromReport).setOnClickListener { finish() }
    }

    private fun submitReport(
        uid: String, incidentType: String, location: String,
        description: String, rootView: View
    ) {
        val progress = ProgressDialog(this).apply {
            setMessage("Submitting report\u2026")
            setCancelable(false)
            show()
        }

        val (latitude, longitude) = resolveCoordinates(location)

        val severity  = when (incidentType) {
            "Theft", "Harassment"            -> 3
            "Suspicious Activity", "Road Accident" -> 2
            else                             -> 1
        }
        val riskLevel = when {
            severity >= 3 -> "HIGH"
            severity >= 2 -> "MEDIUM"
            else          -> "LOW"
        }

        val report = hashMapOf(
            "userId"       to uid,
            "incidentType" to incidentType,
            "location"     to location,
            "description"  to description,
            "latitude"     to latitude,
            "longitude"    to longitude,
            "riskLevel"    to riskLevel,
            "status"       to "ACTIVE",
            "timestamp"    to System.currentTimeMillis()
        )

        FirebaseFirestore.getInstance()
            .collection("reports")
            .add(report)
            .addOnSuccessListener {
                FirebaseFirestore.getInstance()
                    .collection("users").document(uid)
                    .update("reportsCount", FieldValue.increment(1))

                progress.dismiss()
                Snackbar.make(rootView, "\u2713 Report submitted successfully!", Snackbar.LENGTH_SHORT)
                    .addCallback(object : Snackbar.Callback() {
                        override fun onDismissed(transientBottomBar: Snackbar?, event: Int) {
                            finish()
                        }
                    })
                    .show()
            }
            .addOnFailureListener { e ->
                progress.dismiss()
                Snackbar.make(rootView, "Failed to submit: ${e.message}", Snackbar.LENGTH_LONG).show()
            }
    }

    private fun resolveCoordinates(location: String): Pair<Double, Double> = when {
        location.contains("Cebu IT Park", true) -> 10.3295 to 123.9067
        location.contains("Ayala", true)        -> 10.3176 to 123.9053
        location.contains("Colon", true)        -> 10.2933 to 123.9019
        location.contains("Fuente", true)       -> 10.3100 to 123.8936
        location.contains("SM Cebu", true)      -> 10.3116 to 123.9181
        else                                    -> 10.3167 to 123.8907
    }
}
