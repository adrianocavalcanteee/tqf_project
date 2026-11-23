package com.tqfapp.android.ui.request

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.tqfapp.android.ui.common.serializableExtra
import androidx.lifecycle.lifecycleScope
import com.google.android.material.chip.Chip
import com.tqfapp.android.business.ServiceCategories
import com.tqfapp.android.business.Urgency
import com.tqfapp.android.data.model.User
import com.tqfapp.android.data.repository.ServiceRepository
import com.tqfapp.android.data.repository.UserRepository
import com.tqfapp.android.databinding.ActivityDescricaoServicoBinding
import com.tqfapp.android.ui.home.HomeActivity
import com.tqfapp.android.utils.LocationUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.tqfapp.android.ui.common.applySystemBarsPadding
import androidx.core.widget.doAfterTextChanged

/**
 * Formulário de descrição do serviço. Ao enviar, cria a solicitação com status PENDENTE e exibe o
 * estado "Serviço enviado / aguardando resposta".
 * - Com prestador (extra): solicitação direcionada a ele.
 * - Sem prestador: pedido aberto; o cliente escolhe a categoria e todos os prestadores dela recebem.
 */
class DescricaoServicoActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDescricaoServicoBinding
    private var provider: User? = null
    private var currentUser: User? = null

    private val isOpenRequest get() = provider == null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDescricaoServicoBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.root.applySystemBarsPadding()

        provider = intent.serializableExtra<User>(EXTRA_PROVIDER)
        if (provider == null && !intent.getBooleanExtra(EXTRA_OPEN_REQUEST, false)) {
            finish()
            return
        }

        provider?.let {
            binding.txtProviderInfo.text = listOfNotNull("Para: ${it.name}", it.category).joinToString(" • ")
        } ?: setupOpenRequest()

        setupUrgencyChips()
        binding.btnBack.setOnClickListener { finish() }
        binding.btnSendRequest.setOnClickListener { submit() }
        binding.btnBackHome.setOnClickListener { backToHome() }

        prefillFromCurrentUser()
    }

    private fun setupOpenRequest() {
        binding.txtScreenTitle.text = "Abrir solicitação"
        binding.txtProviderInfo.text =
            "Descreva o que precisa. Todos os prestadores da categoria escolhida poderão aceitar."
        binding.tilCategory.visibility = View.VISIBLE
        binding.actvCategory.setAdapter(
            ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, ServiceCategories.ALL)
        )
        intent.getStringExtra(EXTRA_CATEGORY)?.takeIf { it in ServiceCategories.ALL }?.let {
            binding.actvCategory.setText(it, false)
        }
    }

    private fun setupUrgencyChips() {
        Urgency.entries.forEach { urgency ->
            binding.chipGroupUrgency.addView(Chip(this).apply {
                id = View.generateViewId()
                text = urgency.label
                tag = urgency
                isCheckable = true
            })
        }
    }

    private fun selectedUrgency(): Urgency? =
        binding.chipGroupUrgency.findViewById<Chip>(binding.chipGroupUrgency.checkedChipId)?.tag as? Urgency

    private fun prefillFromCurrentUser() {
        lifecycleScope.launch {
            currentUser = withContext(Dispatchers.IO) { UserRepository(applicationContext).getCurrentUser() }
            val phone = currentUser?.telephone
            if (!phone.isNullOrBlank() && binding.etClientPhone.text.isNullOrBlank()) {
                binding.etClientPhone.setText(phone)
            }
            // O número digitado também vira o telefone do cadastro: avisa quando ele é diferente.
            binding.etClientPhone.doAfterTextChanged { updatePhoneHint() }
            updatePhoneHint()
            val address = currentUser?.address
            if (!address.isNullOrBlank() && binding.etLocation.text.isNullOrBlank()) {
                binding.etLocation.setText(address)
            }
        }
    }

    private fun updatePhoneHint() {
        val saved = currentUser?.telephone?.filter { it.isDigit() }.orEmpty()
        val typed = binding.etClientPhone.text?.toString()?.filter { it.isDigit() }.orEmpty()
        binding.tilClientPhone.helperText = if (saved.isNotEmpty() && typed.isNotEmpty() && typed != saved) {
            "Este número substituirá o telefone do seu cadastro."
        } else {
            "O prestador entrará em contato por aqui se aceitar."
        }
    }

    private fun submit() {
        val title = binding.etServiceTitle.text?.toString()?.trim().orEmpty()
        val description = binding.etServiceDescription.text?.toString()?.trim().orEmpty()
        val phone = binding.etClientPhone.text?.toString()?.trim().orEmpty()
        val location = binding.etLocation.text?.toString()?.trim().orEmpty()
        val category = provider?.category.orEmpty().ifBlank { binding.actvCategory.text?.toString()?.trim().orEmpty() }
        val urgency = selectedUrgency()

        binding.tilCategory.error =
            if (isOpenRequest && category !in ServiceCategories.ALL) "Escolha a categoria do serviço" else null
        binding.tilServiceTitle.error = if (title.length < 3) "Informe um título (mín. 3 caracteres)" else null
        binding.tilServiceDescription.error =
            if (description.length < 10) "Descreva o serviço (mín. 10 caracteres)" else null
        binding.tilClientPhone.error =
            if (phone.count { it.isDigit() } < 10) "Informe um WhatsApp válido com DDD" else null
        binding.tilLocation.error =
            if (isOpenRequest && location.length < 3) "Informe o local para os prestadores próximos" else null
        if (binding.tilCategory.error != null ||
            binding.tilServiceTitle.error != null ||
            binding.tilServiceDescription.error != null ||
            binding.tilClientPhone.error != null ||
            binding.tilLocation.error != null
        ) return

        setLoading(true)
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                val repository = ServiceRepository(applicationContext)
                // O número digitado é o que o prestador consultará após o aceite (dados_privados_usuario).
                val phoneSaved = repository.saveCurrentUserPhone(phone)
                if (phoneSaved.isFailure) {
                    return@withContext Result.failure<Unit>(
                        IllegalStateException("Não foi possível salvar seu WhatsApp. Tente novamente.")
                    )
                }
                val coordinates = location.takeIf { it.isNotEmpty() }
                    ?.let { LocationUtils.getCoordinatesFromAddress(applicationContext, it) }
                repository.createRequest(
                    title = title,
                    description = description,
                    category = category,
                    targetProviderId = provider?.id,
                    providerName = provider?.name.orEmpty(),
                    clientName = currentUser?.name.orEmpty(),
                    clientPhone = phone,
                    urgency = urgency,
                    locationText = location,
                    latitude = coordinates?.latitude,
                    longitude = coordinates?.longitude
                ).map { }
            }
            setLoading(false)

            result.onSuccess { showSentState(category) }
                .onFailure { e ->
                    Toast.makeText(
                        this@DescricaoServicoActivity,
                        e.message ?: "Não foi possível enviar a solicitação. Tente novamente.",
                        Toast.LENGTH_LONG
                    ).show()
                }
        }
    }

    private fun showSentState(category: String) {
        binding.layoutForm.visibility = View.GONE
        binding.layoutSuccess.visibility = View.VISIBLE
        binding.txtSuccessMessage.text = provider?.let {
            "Aguarde a resposta de ${it.name}. Se aceitar, o contato será feito pelo seu WhatsApp."
        } ?: "Seu pedido está disponível para os prestadores de $category. O primeiro que aceitar entrará em contato pelo seu WhatsApp."
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
        private const val EXTRA_OPEN_REQUEST = "extra_open_request"
        private const val EXTRA_CATEGORY = "extra_category"

        fun newIntent(context: Context, provider: User): Intent =
            Intent(context, DescricaoServicoActivity::class.java).putExtra(EXTRA_PROVIDER, provider)

        /** Pedido aberto (sem prestador específico), com a categoria opcionalmente pré-selecionada. */
        fun newOpenRequestIntent(context: Context, category: String? = null): Intent =
            Intent(context, DescricaoServicoActivity::class.java)
                .putExtra(EXTRA_OPEN_REQUEST, true)
                .putExtra(EXTRA_CATEGORY, category)
    }
}
