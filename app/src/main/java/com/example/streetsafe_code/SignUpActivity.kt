package com.example.streetsafe_code

import android.content.Intent
import android.os.Bundle
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatButton
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class SignUpActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_signup)

    auth = FirebaseAuth.getInstance()
    db = FirebaseFirestore.getInstance()

    val usernameField = findViewById<EditText>(R.id.editUsername)
    val emailField = findViewById<EditText>(R.id.editEmail)
    val passwordField = findViewById<EditText>(R.id.editPassword)
    val confirmPasswordField = findViewById<EditText>(R.id.editConfirmPassword)
    val btnSignUp = findViewById<AppCompatButton>(R.id.btnSignUp)
    val btnBack = findViewById<AppCompatButton>(R.id.btnBackToLogin)

    btnSignUp.setOnClickListener {
        val username = usernameField.text.toString().trim()
        val email = emailField.text.toString().trim()
        val password = passwordField.text.toString().trim()
        val confirmPassword = confirmPasswordField.text.toString().trim()

        if (username.isEmpty() || email.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show()
            return@setOnClickListener
        }

        if (password != confirmPassword) {
            Toast.makeText(this, "Passwords do not match", Toast.LENGTH_SHORT).show()
            return@setOnClickListener
        }

        auth.createUserWithEmailAndPassword(email, password)
            .addOnSuccessListener {
                val uid = auth.currentUser?.uid ?: return@addOnSuccessListener

                val user = hashMapOf(
                    "username" to username,
                    "email" to email,
                    "created_at" to System.currentTimeMillis()
                )

                db.collection("users").document(uid)
                    .set(user)
                    .addOnSuccessListener {
                        Toast.makeText(this, "Signup successful", Toast.LENGTH_SHORT).show()
                        finish()
                    }
                    .addOnFailureListener {
                        Toast.makeText(this, "Failed to save user data", Toast.LENGTH_SHORT).show()
                    }
            }
            .addOnFailureListener {
                Toast.makeText(this, it.message, Toast.LENGTH_SHORT).show()
            }
    }

    btnBack.setOnClickListener {
        finish()
    }
}
}