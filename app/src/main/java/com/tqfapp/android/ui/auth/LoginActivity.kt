package com.tqfapp.android.ui.auth

import android.content.Context
import android.content.Intent
import android.content.DialogInterface
import android.os.Bundle
import android.util.Patterns
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.tqfapp.android.R
import com.tqfapp.android.data.model.User
import com.tqfapp.android.data.network.SupabaseClient
import com.tqfapp.android.data.repository.AccountTypeMismatchException
import com.tqfapp.android.data.repository.UserRepository
import com.tqfapp.android.databinding.ActivityLoginBinding
import com.tqfapp.android.ui.home.HomeActivity
import io.github.jan.supabase.auth.handleDeeplinks
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

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
            prefillFromRegistration()
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
            // Volta do cadastro reaproveita esta instância (CLEAR_TOP | SINGLE_TOP).
            prefillFromRegistration()
        }
    }

    /**
     * Deep link login-callback do Supabase Auth: confirmação de cadastro ou recuperação de senha.
     * Recuperação (type=recovery no link, ou pedido feito neste aparelho) abre a tela de nova senha
     * depois que a sessão de recuperação é importada.
     */
    private fun handleIncomingIntent(intent: Intent) {
        val data = intent.data
        if (data == null || data.scheme != SupabaseClient.AUTH_CALLBACK_SCHEME || data.host != SupabaseClient.AUTH_CALLBACK_HOST) return

        val isRecovery = data.toString().contains("type=recovery") ||
                UserRepository(applicationContext).consumePasswordRecoveryRequest()
        try {
            runBlocking {
                SupabaseClient.client.handleDeeplinks(intent) {
                    if (isRecovery) runOnUiThread { startActivity(ResetPasswordActivity.newIntent(this@LoginActivity)) }
                }
            }
            if (!isRecovery) {
                Toast.makeText(this, "E-mail confirmado com sucesso! Faça login para continuar.", Toast.LENGTH_LONG).show()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            if (isRecovery) {
                Toast.makeText(this, "Link de recuperação inválido ou expirado. Solicite um novo.", Toast.LENGTH_LONG).show()
            }
        }
    }

    /** "Esqueceu a senha?": pede o e-mail e envia o link de recuperação. */
    private fun showForgotPasswordDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_forgot_password, null)
        val tilEmail = dialogView.findViewById<TextInputLayout>(R.id.tilForgotEmail)
        val etEmail = dialogView.findViewById<TextInputEditText>(R.id.etForgotEmail)
        etEmail.setText(binding.etEmail.text?.toString()?.trim())

        val dialog = MaterialAlertDialogBuilder(this)
            .setView(dialogView)
            .setPositiveButton("Enviar link", null)
            .setNegativeButton("Cancelar", null)
            .create()

        dialog.setOnShowListener {
            val sendButton = dialog.getButton(DialogInterface.BUTTON_POSITIVE)
            sendButton.setOnClickListener {
                val email = etEmail.text?.toString()?.trim().orEmpty()
                if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                    tilEmail.error = "Informe um e-mail válido"
                    return@setOnClickListener
                }
                tilEmail.error = null
                sendButton.isEnabled = false
                lifecycleScope.launch {
                    val result = withContext(Dispatchers.IO) { userRepository.requestPasswordReset(email) }
                    sendButton.isEnabled = true
                    result.onSuccess {
                        dialog.dismiss()
                        Toast.makeText(
                            this@LoginActivity,
                            "Enviamos o link para $email. Abra-o neste celular para criar a nova senha.",
                            Toast.LENGTH_LONG
                        ).show()
                    }.onFailure {
                        tilEmail.error = it.message ?: "Não foi possível enviar o link."
                    }
                }
            }
        }
        dialog.show()
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

    /** Vindo do cadastro: preenche o e-mail e seleciona a aba do tipo de conta criado. */
    private fun prefillFromRegistration() {
        val email = intent.getStringExtra(EXTRA_EMAIL) ?: return
        binding.etEmail.setText(email)
        binding.etPassword.text?.clear()
        binding.toggleUserType.check(
            if (intent.getBooleanExtra(EXTRA_IS_PROVIDER, false)) R.id.btnTypeProvider else R.id.btnTypeContractor
        )
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

            // Autentica no Supabase Auth + PostgREST via UserRepository (rede: fora da thread principal)
            setLoginLoading(true)
            lifecycleScope.launch {
                val authResult = withContext(Dispatchers.IO) {
                    userRepository.loginUser(email, password, expectedIsProvider = isProviderMode)
                }
                setLoginLoading(false)
                handleLoginResult(authResult)
            }
        }

        binding.txtForgotPassword.setOnClickListener { showForgotPasswordDialog() }

        binding.txtRegister.setOnClickListener {
            val intent = Intent(this, RegisterActivity::class.java)
            startActivity(intent)
        }
    }

    private fun setLoginLoading(loading: Boolean) {
        binding.btnLogin.isEnabled = !loading
        binding.btnLogin.text = when {
            loading -> "Entrando..."
            isProviderMode -> "Entrar como Prestador"
            else -> "Entrar como Contratante"
        }
    }

    private fun handleLoginResult(authResult: Result<User>) {
        if (authResult.isSuccess) {
            // Login certo: vai direto para a Home do perfil, sem aviso.
            val dbUser = authResult.getOrNull()!!
            val intent = Intent(this, HomeActivity::class.java).apply {
                putExtra(EXTRA_IS_PROVIDER, dbUser.isProvider)
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

    companion object {
        const val EXTRA_IS_PROVIDER = "extra_is_provider"
        private const val EXTRA_EMAIL = "extra_email"

        fun newIntent(context: Context, email: String, isProvider: Boolean): Intent =
            Intent(context, LoginActivity::class.java)
                .putExtra(EXTRA_EMAIL, email)
                .putExtra(EXTRA_IS_PROVIDER, isProvider)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
    }
}
