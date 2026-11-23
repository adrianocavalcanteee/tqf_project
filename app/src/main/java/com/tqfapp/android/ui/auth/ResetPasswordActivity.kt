package com.tqfapp.android.ui.auth

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.tqfapp.android.data.repository.UserRepository
import com.tqfapp.android.databinding.ActivityResetPasswordBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.tqfapp.android.ui.common.applySystemBarsPadding

/**
 * Nova senha. Aberta pela LoginActivity quando o link de recuperação do e-mail volta ao app
 * (o deep link já importou a sessão de recuperação do Supabase Auth). Ao salvar, encerra a
 * sessão e volta ao login para entrar com a senha nova.
 */
class ResetPasswordActivity : AppCompatActivity() {

    private lateinit var binding: ActivityResetPasswordBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityResetPasswordBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.root.applySystemBarsPadding()

        binding.btnBack.setOnClickListener { backToLogin() }
        binding.btnSavePassword.setOnClickListener { savePassword() }
    }

    private fun savePassword() {
        val password = binding.etNewPassword.text?.toString().orEmpty()
        val confirmation = binding.etConfirmNewPassword.text?.toString().orEmpty()

        binding.tilNewPassword.error =
            if (password.length < 6) "A senha deve conter no mínimo 6 caracteres" else null
        binding.tilConfirmNewPassword.error =
            if (confirmation != password) "As senhas não coincidem" else null
        if (binding.tilNewPassword.error != null || binding.tilConfirmNewPassword.error != null) return

        setLoading(true)
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) { UserRepository(applicationContext).updatePassword(password) }
            setLoading(false)
            result.onSuccess {
                Toast.makeText(this@ResetPasswordActivity, "Senha alterada! Entre com a nova senha.", Toast.LENGTH_LONG).show()
                backToLogin()
            }.onFailure {
                Toast.makeText(
                    this@ResetPasswordActivity,
                    it.message ?: "Não foi possível alterar a senha. Solicite um novo link.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun setLoading(loading: Boolean) {
        binding.btnSavePassword.isEnabled = !loading
        binding.btnSavePassword.text = if (loading) "Salvando..." else "Salvar nova senha"
    }

    private fun backToLogin() {
        startActivity(
            Intent(this, LoginActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        )
        finish()
    }

    companion object {
        fun newIntent(context: Context): Intent = Intent(context, ResetPasswordActivity::class.java)
    }
}
