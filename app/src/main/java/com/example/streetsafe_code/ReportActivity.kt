package com.example.streetsafe_code

import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.Spinner
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatButton
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore

class ReportActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_report)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.reportRoot)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        val incidentTypes = listOf(
            "Theft", "Harassment", "Poor Lighting", "Suspicious Activity", "Road Accident"
        )
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, incidentTypes)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        findViewById<Spinner>(R.id.spinnerIncidentType).adapter = adapter

        findViewById<AppCompatButton>(R.id.btnSubmitReport).setOnClickListener {
            val incidentType =
                findViewById<Spinner>(R.id.spinnerIncidentType).selectedItem.toString()

            val location = findViewById<EditText>(R.id.editLocation).text.toString().trim()

            val description = findViewById<EditText>(R.id.editDescription).text.toString().trim()

            if (location.isEmpty() || description.isEmpty()) {
                Toast.makeText(
                    this, "Fill all fields", Toast.LENGTH_SHORT
                ).show()
                return@setOnClickListener
            }

            val uid = FirebaseAuth.getInstance().currentUser?.uid

            if (uid == null) {
                Toast.makeText(
                    this, "User not logged in", Toast.LENGTH_SHORT
                ).show()
                return@setOnClickListener
            }

            val latitude: Double
            val longitude: Double

            if (location.contains("Cebu IT Park", true)) {
                latitude = 10.3295
                longitude = 123.9067
            } else if (location.contains("Ayala", true)) {
                latitude = 10.3176
                longitude = 123.9053
            } else if (location.contains("Colon", true)) {
                latitude = 10.2933
                longitude = 123.9019
            } else if (location.contains("Fuente", true)) {
                latitude = 10.3100
                longitude = 123.8936
            } else if (location.contains("SM Cebu", true)) {
                latitude = 10.3116
                longitude = 123.9181
            } else {
                latitude = 10.3167
                longitude = 123.8907
            }

            val severity = when(incidentType){

                "Theft" -> 3
                "Harassment" -> 3
                "Suspicious Activity" -> 2
                "Road Accident" -> 2
                "Poor Lighting" -> 1

                else -> 1
            }

            val riskLevel =
                when{
                    severity >=3 -> "HIGH"
                    severity >=2 -> "MEDIUM"
                    else -> "LOW"
                }

            val report = hashMapOf(
                "userId" to uid,
                "incidentType" to incidentType,
                "location" to location,
                "description" to description,
                "latitude" to latitude,
                "longitude" to longitude,
                "riskLevel" to riskLevel,
                "timestamp" to System.currentTimeMillis()
            )

            FirebaseFirestore.getInstance()
                .collection("reports")
                .add(report)
                .addOnSuccessListener {
                    FirebaseFirestore.getInstance()
                        .collection("users")
                        .document(uid)
                        .update(
                            "reportsCount",
                            FieldValue.increment(1)
                        )

                    Toast.makeText(
                        this,
                        "Report submitted",
                        Toast.LENGTH_SHORT
                    ).show()

                    finish()
                }
                .addOnFailureListener { e ->

                    Toast.makeText(
                        this,
                        e.message.toString(),
                        Toast.LENGTH_LONG
                    ).show()
                }
        }
            findViewById<AppCompatButton>(R.id.btnBackFromReport).setOnClickListener {
                finish()
            }
    }
}