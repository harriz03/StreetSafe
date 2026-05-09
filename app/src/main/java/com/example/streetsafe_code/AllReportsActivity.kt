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

        val reportItems =
            mutableListOf<String>()

        FirebaseFirestore.getInstance()
            .collection("reports")
            .get()
            .addOnSuccessListener { docs ->

                for(doc in docs){

                    val incident =
                        doc.getString(
                            "incidentType"
                        ) ?: "-"

                    val location =
                        doc.getString(
                            "location"
                        ) ?: "-"

                    val risk =
                        doc.getString(
                            "riskLevel"
                        ) ?: "-"

                    reportItems.add(
                        "$incident - $location ($risk)"
                    )
                }

                val adapter =
                    ArrayAdapter(
                        this,
                        android.R.layout.simple_list_item_1,
                        reportItems
                    )

                listView.adapter = adapter
            }

        findViewById<TextView>(R.id.btnBackFromReports).setOnClickListener{
            val intent = Intent(this, HomeActivity::class.java)
            startActivity(intent)
            finish()
        }
    }
}