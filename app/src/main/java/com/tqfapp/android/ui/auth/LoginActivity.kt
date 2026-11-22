package com.tqfapp.android.ui.auth

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.tqfapp.android.R
import com.tqfapp.android.data.network.SupabaseClient
import com.tqfapp.android.data.repository.AccountTypeMismatchException
import com.tqfapp.android.data.repository.UserRepository
import com.tqfapp.android.databinding.ActivityLoginBinding
import com.tqfapp.android.ui.home.HomeActivity
import io.github.jan.supabase.auth.handleDeeplinks
import kotlinx.coroutines.runBlocking

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private lateinit var userRepository: UserRepository
    private var isProviderMode: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            binding = ActivityLoginBinding.inflate(layoutInflater)
            setContentView(binding.root)

            userRepository = UserRepository(this)

            handleIncomingIntent(intent)

            setupUserTypeToggle()
            setupClickListeners()
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Erro ao iniciar Login: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent != null) {
            handleIncomingIntent(intent)
        }
    }

    private fun handleIncomingIntent(intent: Intent) {
        val data = intent.data
        if (data != null && data.scheme == "com.tqfapp.android" && data.host == "login-callback") {
            try {
                runBlocking {
                    SupabaseClient.client.handleDeeplinks(intent)
                }
                Toast.makeText(this, "E-mail confirmado com sucesso! Faça login para continuar.", Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun setupUserTypeToggle() {
        binding.toggleUserType.check(R.id.btnTypeContractor)
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

            // Autentica no Supabase Auth + PostgREST via UserRepository
            val authResult = userRepository.loginUser(email, password, expectedIsProvider = isProviderMode)

            if (authResult.isSuccess) {
                val dbUser = authResult.getOrNull()!!
                val finalIsProvider = dbUser.isProvider
                val userName = dbUser.name

                val userTypeLabel = if (finalIsProvider) "Prestador de Serviço" else "Contratante"
                Toast.makeText(this, "Bem-vindo, $userName! Login como $userTypeLabel", Toast.LENGTH_SHORT).show()

                val intent = Intent(this, HomeActivity::class.java).apply {
                    putExtra(EXTRA_IS_PROVIDER, finalIsProvider)
                }
                startActivity(intent)
                finish()
            } else {
                val exception = authResult.exceptionOrNull()
                val errorMsg = exception?.message ?: "Falha na autenticação. Verifique seu e-mail e senha."
                // Conta de outro tipo não é erro de e-mail: não marca o campo.
                if (exception !is AccountTypeMismatchException) binding.etEmail.error = errorMsg
                Toast.makeText(this, errorMsg, Toast.LENGTH_LONG).show()
            }
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
