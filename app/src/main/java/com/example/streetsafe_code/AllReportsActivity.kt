package com.example.streetsafe_code

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.ListView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query

class AllReportsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_all_reports)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(android.R.id.content)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        val listView = findViewById<ListView>(R.id.listReports)
        val rootView = findViewById<View>(android.R.id.content)

        // Show loading state
        Snackbar.make(rootView, "Loading reports\u2026", Snackbar.LENGTH_SHORT).show()

        FirebaseFirestore.getInstance()
            .collection("reports")
            .whereEqualTo("status", "ACTIVE")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .get()
            .addOnSuccessListener { docs ->
                val reports = mutableListOf<Report>()
                for (doc in docs) {
                    reports.add(doc.toObject(Report::class.java))
                }

                if (reports.isEmpty()) {
                    Snackbar.make(rootView, "No active incidents reported.", Snackbar.LENGTH_LONG).show()
                }

                val adapter = object : ArrayAdapter<Report>(this, R.layout.item_report, reports) {
                    override fun getView(position: Int, convertView: View?, parent: android.view.ViewGroup): View {
                        val view   = layoutInflater.inflate(R.layout.item_report, parent, false)
                        val report = reports[position]

                        view.findViewById<TextView>(R.id.txtIncidentType).text = report.incidentType
                        view.findViewById<TextView>(R.id.txtLocation).text     = report.location
                        view.findViewById<TextView>(R.id.txtRisk).text         = report.riskLevel

                        val riskBar = view.findViewById<View>(R.id.viewRisk)
                        val color = when (report.riskLevel) {
                            "HIGH"   -> android.graphics.Color.RED
                            "MEDIUM" -> android.graphics.Color.parseColor("#FF9800")
                            else     -> android.graphics.Color.parseColor("#4CAF50")
                        }
                        view.findViewById<TextView>(R.id.txtRisk).setTextColor(color)
                        riskBar.setBackgroundColor(color)

                        return view
                    }
                }
                listView.adapter = adapter
            }
            .addOnFailureListener { e ->
                Snackbar.make(rootView, "Failed to load reports: ${e.message}", Snackbar.LENGTH_LONG)
                    .setAction("Retry") {
                        recreate()
                    }
                    .show()
            }

        findViewById<TextView>(R.id.btnBackFromReports).setOnClickListener {
            startActivity(Intent(this, HomeActivity::class.java))
            finish()
        }
    }
}
