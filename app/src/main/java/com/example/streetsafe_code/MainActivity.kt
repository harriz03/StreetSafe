package com.example.streetsafe_code

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.EditText
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatButton
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.auth.FirebaseAuth
import android.app.ProgressDialog

class MainActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        auth = FirebaseAuth.getInstance()

        // Auto-redirect if already logged in
        if (auth.currentUser != null) {
            navigateToHome()
            return
        }

        val emailField    = findViewById<EditText>(R.id.editUsername)
        val passwordField = findViewById<EditText>(R.id.editPassword)
        val rootView      = findViewById<View>(android.R.id.content)

        findViewById<AppCompatButton>(R.id.btnLogin).setOnClickListener {
            val email    = emailField.text.toString().trim()
            val password = passwordField.text.toString().trim()

            if (email.isEmpty()) {
                emailField.error = "Email is required"
                emailField.requestFocus()
                return@setOnClickListener
            }
            if (password.isEmpty()) {
                passwordField.error = "Password is required"
                passwordField.requestFocus()
                return@setOnClickListener
            }

            val progress = ProgressDialog(this).apply {
                setMessage("Signing in\u2026")
                setCancelable(false)
                show()
            }

            auth.signInWithEmailAndPassword(email, password)
                .addOnSuccessListener {
                    progress.dismiss()
                    navigateToHome()
                }
                .addOnFailureListener { e ->
                    progress.dismiss()
                    val msg = when {
                        e.message?.contains("password", true) == true ->
                            "Incorrect password. Please try again."
                        e.message?.contains("no user", true) == true ->
                            "No account found with this email."
                        else -> e.message ?: "Login failed. Please try again."
                    }
                    Snackbar.make(rootView, msg, Snackbar.LENGTH_LONG).show()
                }
        }

        findViewById<AppCompatButton>(R.id.btnSignUp).setOnClickListener {
            startActivity(Intent(this, SignUpActivity::class.java))
        }
    }

    private fun navigateToHome() {
        startActivity(Intent(this, HomeActivity::class.java))
        finish()
    }
}
