package com.ceibotech.converso

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Patterns
import android.widget.Toast
import com.ceibotech.converso.databinding.ActivityAuthBinding
import com.google.android.gms.tasks.Task
import com.google.firebase.auth.AuthResult

class AuthActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAuthBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAuthBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.signInButton.setOnClickListener { performAuth(true) }
        binding.signUpButton.setOnClickListener { performAuth(false) }
    }

    private fun performAuth(signIn: Boolean) {
        val email = binding.emailEditText.text.toString()
        val password = binding.passwordEditText.text.toString()

        if (isEmailValid(email) && isPasswordValid(password)) {
            toggleButtons(false) // disable buttons to avoid multiple requests

            if (signIn) signIn(email, password)
            else signUp(email, password)
        }
    }

    private fun isEmailValid(email: String): Boolean {
        if (!email.contains("@") || !email.contains(".")) {
            binding.emailTextInputLayout.error = "Invalid email"
            return false
        }
        binding.emailTextInputLayout.error = null
        return true
    }

    private fun isPasswordValid(password: String): Boolean {
        if (!Regex("^(?=.*[A-Z])(?=.*[0-9]).{8,}$").matches(password)) {
            binding.passwordTextInputLayout.error = "Password must contain:\n" +
                    "- 8 characters\n" +
                    "- 1 uppercase letter (A-Z)\n" +
                    "- 1 number (0-9)"
            return false
        }
        binding.passwordTextInputLayout.error = null
        return true
    }

    private fun signIn(email: String, password: String) {
        MainActivity.auth.signInWithEmailAndPassword(email, password)
            .addOnCompleteListener(this) { handleAuthResult(it) }
    }

    private fun signUp(email: String, password: String) {
        MainActivity.auth.createUserWithEmailAndPassword(email, password)
            .addOnCompleteListener(this) { handleAuthResult(it) }
    }

    private fun handleAuthResult(task: Task<AuthResult>) {
        if (task.isSuccessful) {
            goToMainActivity()
        } else {
            Toast.makeText(this, task.exception?.message, Toast.LENGTH_LONG).show()
            toggleButtons(true)
        }
    }

    private fun goToMainActivity() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }

    private fun toggleButtons(enabled: Boolean) {
        binding.signInButton.isEnabled = enabled
        binding.signUpButton.isEnabled = enabled
    }
}