package com.ceibotech.converso

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.Toast
import com.ceibotech.converso.databinding.ActivityAuthBinding

class AuthActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAuthBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAuthBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.signInButton.setOnClickListener {
            val email = binding.emailEditText.text.toString()
            val password = binding.passwordEditText.text.toString()

            if (isEmailValid(email) && isPasswordValid(password)) {
                binding.signInButton.isEnabled = false
                signIn(email, password)
            }
        }

        binding.signUpButton.setOnClickListener {
            val email = binding.emailEditText.text.toString()
            val password = binding.passwordEditText.text.toString()

            if (isEmailValid(email) && isPasswordValid(password)) {
                binding.signUpButton.isEnabled = false
                signUp(email, password)
            }
        }
    }

    private fun isEmailValid(email: String): Boolean {
        if (email.isEmpty()) {
            binding.emailTextInputLayout.error = "Email is required"
            return false
        }
        binding.emailTextInputLayout.error = null
        return true
    }

    private fun isPasswordValid(password: String): Boolean {
        val passwordRegex = Regex("^(?=.*[A-Z])(?=.*[0-9]).{8,}$")
        if (!passwordRegex.matches(password)) {
            binding.passwordTextInputLayout.error = "Password must contain:\n- 8 characters\n- 1 uppercase letter (A-Z)\n- 1 number (0-9)"
            return false
        }
        binding.passwordTextInputLayout.error = null
        return true
    }

    private fun signIn(email: String, password: String) {
        MainActivity.auth.signInWithEmailAndPassword(email, password)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    goToMainActivity()
                } else {
                    Toast.makeText(this, task.exception?.message, Toast.LENGTH_LONG).show()
                    binding.signInButton.isEnabled = true
                }
            }
    }

    private fun signUp(email: String, password: String) {
        MainActivity.auth.createUserWithEmailAndPassword(email, password)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    goToMainActivity()
                } else {
                    Toast.makeText(this, task.exception?.message, Toast.LENGTH_LONG).show()
                    binding.signUpButton.isEnabled = true
                }
            }
    }

    private fun goToMainActivity() {
        val intent = Intent(this, MainActivity::class.java)
        startActivity(intent)
        finish()
    }
}