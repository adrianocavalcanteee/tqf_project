package com.tqfapp.android.ui.auth

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.tqfapp.android.R
import com.tqfapp.android.data.repository.UserRepository
import com.tqfapp.android.databinding.ActivityLoginBinding
import com.tqfapp.android.ui.home.HomeActivity
import com.tqfapp.android.utils.UserSessionManager

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private lateinit var userRepository: UserRepository
    private var isProviderMode: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        userRepository = UserRepository(this)

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

            // Tenta autenticar pelo banco de dados local em primeiro lugar
            val authResult = userRepository.loginUser(email, password)

            val finalIsProvider: Boolean
            val userName: String?

            if (authResult.isSuccess) {
                val dbUser = authResult.getOrNull()!!
                finalIsProvider = dbUser.isProvider
                userName = dbUser.name
            } else {
                // Fallback para o modo simulado se o usuário ainda não foi cadastrado no banco local
                finalIsProvider = isProviderMode
                userName = email.substringBefore("@")
            }

            val userTypeLabel = if (finalIsProvider) "Prestador de Serviço" else "Contratante"
            Toast.makeText(this, "Bem-vindo, $userName! Login como $userTypeLabel", Toast.LENGTH_SHORT).show()

            // Salva o tipo e dados de usuário na sessão local
            UserSessionManager.saveUserSession(this, finalIsProvider, userName, email)

            val intent = Intent(this, HomeActivity::class.java).apply {
                putExtra(EXTRA_IS_PROVIDER, finalIsProvider)
            }
            startActivity(intent)
            finish()
        }

        binding.txtForgotPassword.setOnClickListener {
            Toast.makeText(this, "Função de recuperar senha", Toast.LENGTH_SHORT).show()
        }

        binding.txtRegister.setOnClickListener {
            val intent = Intent(this, RegisterActivity::class.java)
            startActivity(intent)
        }
    }

    companion object {
        const val EXTRA_IS_PROVIDER = "extra_is_provider"
    }
}
