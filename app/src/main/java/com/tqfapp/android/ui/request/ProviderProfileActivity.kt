package com.tqfapp.android.ui.request

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.tqfapp.android.ui.common.serializableExtra
import com.tqfapp.android.data.model.User
import com.tqfapp.android.databinding.ActivityProviderProfileBinding
import com.tqfapp.android.ui.common.bindServiceChips
import com.tqfapp.android.ui.home.ProfessionalAdapter
import com.tqfapp.android.utils.UserSessionManager

/**
 * Perfil público do Prestador (visão do Contratante).
 * Fluxo: Home ➔ Perfil do Prestador ➔ "Solicitar Serviço" ➔ DescricaoServicoActivity.
 */
class ProviderProfileActivity : AppCompatActivity() {

    private lateinit var binding: ActivityProviderProfileBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProviderProfileBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val provider = intent.serializableExtra<User>(EXTRA_PROVIDER)
        if (provider == null) {
            finish()
            return
        }

        bindProvider(provider)
        binding.btnBack.setOnClickListener { finish() }
    }

    private fun bindProvider(provider: User) = with(binding) {
        txtName.text = provider.name
        txtCategory.text = listOfNotNull(provider.category, provider.specialty)
            .filter { it.isNotBlank() }
            .distinct()
            .joinToString(" • ")
            .ifEmpty { "Prestador de serviços" }
        txtMetrics.text = ProfessionalAdapter.metricsLine(provider)
        txtStatusBadge.visibility = if (provider.isAvailable) View.VISIBLE else View.GONE
        txtBio.text = provider.bio?.takeIf { it.isNotBlank() } ?: "Este prestador ainda não escreveu uma descrição."
        txtPrice.text = provider.priceLabel()

        chipGroupServices.bindServiceChips(provider.services)
        txtNoServices.visibility = if (provider.services.isEmpty()) View.VISIBLE else View.GONE

        provider.address?.takeIf { it.isNotBlank() }?.let {
            txtAddress.text = it
            txtAddress.visibility = View.VISIBLE
        }

        // Somente o Contratante pode solicitar serviços (matriz de permissões).
        val isClient = !UserSessionManager.isProviderUser(this@ProviderProfileActivity)
        btnRequestService.visibility = if (isClient) View.VISIBLE else View.GONE
        btnRequestService.setOnClickListener {
            startActivity(DescricaoServicoActivity.newIntent(this@ProviderProfileActivity, provider))
        }
    }

    companion object {
        private const val EXTRA_PROVIDER = "extra_provider"

        fun newIntent(context: Context, provider: User): Intent =
            Intent(context, ProviderProfileActivity::class.java).putExtra(EXTRA_PROVIDER, provider)
    }
}
