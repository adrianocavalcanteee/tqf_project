package com.tqfapp.android.ui.auth

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.tqfapp.android.R
import com.tqfapp.android.databinding.ActivityLoginBinding
import com.tqfapp.android.ui.home.HomeActivity

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private var isProviderMode: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupUserTypeToggle()
        setupClickListeners()
    }

    private fun setupUserTypeToggle() {
        binding.toggleUserType.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                when (checkedId) {
                    R.id.btnTypeContractor -> {
                        isProviderMode = false
                        binding.btnLogin.text = "Entrar como Contratante"
                        binding.btnTypeContractor.setTextColor(getColor(R.color.brand_blue))
                        binding.btnTypeProvider.setTextColor(getColor(R.color.text_secondary))
                    }
                    R.id.btnTypeProvider -> {
                        isProviderMode = true
                        binding.btnLogin.text = "Entrar como Prestador"
                        binding.btnTypeProvider.setTextColor(getColor(R.color.brand_blue))
                        binding.btnTypeContractor.setTextColor(getColor(R.color.text_secondary))
                    }
                }
            }
        }
    }

    private fun setupClickListeners() {
        binding.btnLogin.setOnClickListener {
            val email = binding.etEmail.text.toString().trim()
            val password = binding.etPassword.text.toString().trim()

            if (email.isEmpty()) {
                binding.etEmail.error = "Digite seu e-mail"
                return@setOnClickListener
            }

            if (password.isEmpty()) {
                binding.etPassword.error = "Digite sua senha"
                return@setOnClickListener
            }

            val userTypeLabel = if (isProviderMode) "Prestador de Serviço" else "Contratante"
            Toast.makeText(this, "Login realizado como $userTypeLabel!", Toast.LENGTH_SHORT).show()

            val intent = Intent(this, HomeActivity::class.java).apply {
                putExtra(EXTRA_IS_PROVIDER, isProviderMode)
            }
            startActivity(intent)
            finish()
        }

        binding.txtForgotPassword.setOnClickListener {
            Toast.makeText(this, "Função de recuperar senha", Toast.LENGTH_SHORT).show()
        }

        binding.txtRegister.setOnClickListener {
            Toast.makeText(this, "Abrir tela de cadastro", Toast.LENGTH_SHORT).show()
        }
    }

    companion object {
        const val EXTRA_IS_PROVIDER = "extra_is_provider"
    }
}