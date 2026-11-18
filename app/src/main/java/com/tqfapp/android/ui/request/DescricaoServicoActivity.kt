package com.tqfapp.android.ui.request

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.tqfapp.android.ui.common.serializableExtra
import androidx.lifecycle.lifecycleScope
import com.tqfapp.android.data.model.User
import com.tqfapp.android.data.repository.ServiceRepository
import com.tqfapp.android.data.repository.UserRepository
import com.tqfapp.android.databinding.ActivityDescricaoServicoBinding
import com.tqfapp.android.ui.home.HomeActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Formulário de descrição do serviço. Ao enviar, cria a solicitação com status PENDENTE
 * direcionada ao prestador e exibe o estado "Serviço enviado / aguardando resposta".
 */
class DescricaoServicoActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDescricaoServicoBinding
    private lateinit var provider: User
    private var currentUser: User? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDescricaoServicoBinding.inflate(layoutInflater)
        setContentView(binding.root)

        provider = intent.serializableExtra<User>(EXTRA_PROVIDER) ?: run {
            finish()
            return
        }

        binding.txtProviderInfo.text = listOfNotNull("Para: ${provider.name}", provider.category)
            .joinToString(" • ")
        binding.btnBack.setOnClickListener { finish() }
        binding.btnSendRequest.setOnClickListener { submit() }
        binding.btnBackHome.setOnClickListener { backToHome() }

        prefillClientPhone()
    }

    private fun prefillClientPhone() {
        lifecycleScope.launch {
            currentUser = withContext(Dispatchers.IO) { UserRepository(applicationContext).getCurrentUser() }
            val phone = currentUser?.telephone
            if (!phone.isNullOrBlank() && binding.etClientPhone.text.isNullOrBlank()) {
                binding.etClientPhone.setText(phone)
            }
        }
    }

    private fun submit() {
        val title = binding.etServiceTitle.text?.toString()?.trim().orEmpty()
        val description = binding.etServiceDescription.text?.toString()?.trim().orEmpty()
        val phone = binding.etClientPhone.text?.toString()?.trim().orEmpty()

        binding.tilServiceTitle.error = if (title.length < 3) "Informe um título (mín. 3 caracteres)" else null
        binding.tilServiceDescription.error =
            if (description.length < 10) "Descreva o serviço (mín. 10 caracteres)" else null
        binding.tilClientPhone.error =
            if (phone.count { it.isDigit() } < 10) "Informe um WhatsApp válido com DDD" else null
        if (binding.tilServiceTitle.error != null ||
            binding.tilServiceDescription.error != null ||
            binding.tilClientPhone.error != null
        ) return

        setLoading(true)
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                ServiceRepository(applicationContext).createDirectRequest(
                    title = title,
                    description = description,
                    category = provider.category.orEmpty(),
                    targetProviderId = provider.id,
                    providerName = provider.name,
                    clientName = currentUser?.name.orEmpty(),
                    clientPhone = phone
                )
            }
            setLoading(false)

            result.onSuccess { showSentState() }
                .onFailure { e ->
                    Toast.makeText(
                        this@DescricaoServicoActivity,
                        e.message ?: "Não foi possível enviar a solicitação. Tente novamente.",
                        Toast.LENGTH_LONG
                    ).show()
                }
        }
    }

    private fun showSentState() {
        binding.layoutForm.visibility = View.GONE
        binding.layoutSuccess.visibility = View.VISIBLE
        binding.txtSuccessMessage.text =
            "Aguarde a resposta de ${provider.name}. Se aceitar, o contato será feito pelo seu WhatsApp."
    }

    private fun setLoading(loading: Boolean) {
        binding.progressSend.visibility = if (loading) View.VISIBLE else View.GONE
        binding.btnSendRequest.isEnabled = !loading
    }

    private fun backToHome() {
        startActivity(
            Intent(this, HomeActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        )
        finish()
    }

    companion object {
        private const val EXTRA_PROVIDER = "extra_provider"

        fun newIntent(context: Context, provider: User): Intent =
            Intent(context, DescricaoServicoActivity::class.java).putExtra(EXTRA_PROVIDER, provider)
    }
}
