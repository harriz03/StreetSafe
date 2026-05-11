package com.example.streetsafe_code

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatButton
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class ProfileActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.profileRoot)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        val rootView = findViewById<View>(R.id.profileRoot)
        val uid      = FirebaseAuth.getInstance().currentUser?.uid

        if (uid != null) {
            FirebaseFirestore.getInstance()
                .collection("users").document(uid)
                .get()
                .addOnSuccessListener { document ->
                    if (document.exists()) {
                        val username      = document.getString("username") ?: "User"
                        val email         = document.getString("email") ?: "No email"
                        val reportsCount  = document.getLong("reportsCount") ?: 0

                        findViewById<TextView>(R.id.txtProfileName).text         = username
                        findViewById<TextView>(R.id.txtProfileUsername).text     = username
                        findViewById<TextView>(R.id.txtProfileEmail).text        = email
                        findViewById<TextView>(R.id.txtProfileEmailField).text   = email
                        findViewById<TextView>(R.id.txtReportsCount).text        = reportsCount.toString()
                    }
                }
                .addOnFailureListener {
                    Snackbar.make(rootView, "Failed to load profile.", Snackbar.LENGTH_SHORT).show()
                }
        }

        // Logout with confirmation
        findViewById<AppCompatButton>(R.id.btnLogOut).setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Log Out")
                .setMessage("Are you sure you want to log out?")
                .setPositiveButton("Log Out") { _, _ ->
                    FirebaseAuth.getInstance().signOut()
                    val intent = Intent(this, MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    }
                    startActivity(intent)
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        findViewById<AppCompatButton>(R.id.btnBackFromHome).setOnClickListener {
            startActivity(Intent(this, HomeActivity::class.java))
            finish()
        }
    }
}
