package com.example.streetsafe_code

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.EditText
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatButton
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import android.app.ProgressDialog

class SignUpActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_signup)

        auth = FirebaseAuth.getInstance()
        db   = FirebaseFirestore.getInstance()

        val usernameField        = findViewById<EditText>(R.id.editUsername)
        val emailField           = findViewById<EditText>(R.id.editEmail)
        val passwordField        = findViewById<EditText>(R.id.editPassword)
        val confirmPasswordField = findViewById<EditText>(R.id.editConfirmPassword)
        val rootView             = findViewById<View>(android.R.id.content)

        findViewById<AppCompatButton>(R.id.btnSignUp).setOnClickListener {
            val username        = usernameField.text.toString().trim()
            val email           = emailField.text.toString().trim()
            val password        = passwordField.text.toString().trim()
            val confirmPassword = confirmPasswordField.text.toString().trim()

            // Field-level validation
            if (username.isEmpty()) {
                usernameField.error = "Username is required"
                usernameField.requestFocus(); return@setOnClickListener
            }
            if (email.isEmpty()) {
                emailField.error = "Email is required"
                emailField.requestFocus(); return@setOnClickListener
            }
            if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                emailField.error = "Enter a valid email address"
                emailField.requestFocus(); return@setOnClickListener
            }
            if (password.length < 6) {
                passwordField.error = "Password must be at least 6 characters"
                passwordField.requestFocus(); return@setOnClickListener
            }
            if (password != confirmPassword) {
                confirmPasswordField.error = "Passwords do not match"
                confirmPasswordField.requestFocus(); return@setOnClickListener
            }

            val progress = ProgressDialog(this).apply {
                setMessage("Creating your account\u2026")
                setCancelable(false)
                show()
            }

            auth.createUserWithEmailAndPassword(email, password)
                .addOnSuccessListener {
                    val uid  = auth.currentUser?.uid ?: run { progress.dismiss(); return@addOnSuccessListener }
                    val user = hashMapOf(
                        "username"     to username,
                        "email"        to email,
                        "created_at"   to System.currentTimeMillis(),
                        "reportsCount" to 0
                    )
                    db.collection("users").document(uid).set(user)
                        .addOnSuccessListener {
                            progress.dismiss()
                            Snackbar.make(rootView, "Account created! Please log in.", Snackbar.LENGTH_SHORT).show()
                            finish()
                        }
                        .addOnFailureListener { e ->
                            progress.dismiss()
                            Snackbar.make(rootView, "Failed to save profile: ${e.message}", Snackbar.LENGTH_LONG).show()
                        }
                }
                .addOnFailureListener { e ->
                    progress.dismiss()
                    val msg = when {
                        e.message?.contains("email", true) == true -> "This email is already registered."
                        else -> e.message ?: "Sign up failed."
                    }
                    Snackbar.make(rootView, msg, Snackbar.LENGTH_LONG).show()
                }
        }

        findViewById<AppCompatButton>(R.id.btnBackToLogin).setOnClickListener { finish() }
    }
}
