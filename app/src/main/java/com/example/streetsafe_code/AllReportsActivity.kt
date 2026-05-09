package com.example.streetsafe_code

import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import android.widget.ArrayAdapter
import android.widget.ListView

class AllReportsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_all_reports)

        val listView =
            findViewById<ListView>(
                R.id.listReports
            )

        FirebaseFirestore.getInstance()
            .collection("reports")
            .whereEqualTo("status", "ACTIVE")
            .orderBy(
                "timestamp",
                Query.Direction.DESCENDING
            )
            .get()
            .addOnSuccessListener { docs ->

                val reports =
                    mutableListOf<Report>()

                for(doc in docs){

                    val report =
                        doc.toObject(
                            Report::class.java
                        )

                    reports.add(report)
                }

                val adapter =
                    object : ArrayAdapter<Report>(
                        this,
                        R.layout.item_report,
                        reports
                    ){

                        override fun getView(
                            position: Int,
                            convertView: android.view.View?,
                            parent: android.view.ViewGroup
                        ): android.view.View {

                            val view =
                                layoutInflater.inflate(
                                    R.layout.item_report,
                                    parent,
                                    false
                                )

                            val report =
                                reports[position]

                            val txtIncident =
                                view.findViewById<TextView>(
                                    R.id.txtIncidentType
                                )

                            val txtLocation =
                                view.findViewById<TextView>(
                                    R.id.txtLocation
                                )

                            val txtRisk =
                                view.findViewById<TextView>(
                                    R.id.txtRisk
                                )

                            val riskBar =
                                view.findViewById<android.view.View>(
                                    R.id.viewRisk
                                )

                            txtIncident.text =
                                report.incidentType

                            txtLocation.text =
                                report.location

                            txtRisk.text =
                                report.riskLevel

                            when(report.riskLevel){

                                "HIGH" -> {

                                    txtRisk.setTextColor(
                                        android.graphics.Color.RED
                                    )

                                    riskBar.setBackgroundColor(
                                        android.graphics.Color.RED
                                    )
                                }

                                "MEDIUM" -> {

                                    txtRisk.setTextColor(
                                        android.graphics.Color.parseColor("#FF9800")
                                    )

                                    riskBar.setBackgroundColor(
                                        android.graphics.Color.parseColor("#FF9800")
                                    )
                                }

                                else -> {

                                    txtRisk.setTextColor(
                                        android.graphics.Color.GREEN
                                    )

                                    riskBar.setBackgroundColor(
                                        android.graphics.Color.GREEN
                                    )
                                }
                            }

                            return view
                        }
                    }

                listView.adapter = adapter
            }

        findViewById<TextView>(R.id.btnBackFromReports).setOnClickListener{
            val intent = Intent(this, HomeActivity::class.java)
            startActivity(intent)
            finish()
        }
    }
}