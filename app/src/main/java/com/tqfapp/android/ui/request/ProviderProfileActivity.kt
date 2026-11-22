package com.tqfapp.android.ui.request

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.tqfapp.android.ui.common.serializableExtra
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.tqfapp.android.data.model.User
import com.tqfapp.android.data.repository.ReviewRepository
import com.tqfapp.android.ui.common.ReviewListAdapter
import com.tqfapp.android.databinding.ActivityProviderProfileBinding
import com.tqfapp.android.ui.common.bindServiceChips
import com.tqfapp.android.ui.home.ProfessionalAdapter
import com.tqfapp.android.utils.UserSessionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.tqfapp.android.ui.common.PortfolioPhotoAdapter
import com.tqfapp.android.ui.common.loadImage

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
        binding.rvReviews.layoutManager = LinearLayoutManager(this)
    }

    override fun onResume() {
        super.onResume()
        intent.serializableExtra<User>(EXTRA_PROVIDER)?.let { loadReviews(it) }
    }

    /** Nota, total e comentários lidos do Supabase a cada abertura (refletem avaliações recém-feitas). */
    private fun loadReviews(provider: User) {
        lifecycleScope.launch {
            val reviews = withContext(Dispatchers.IO) {
                ReviewRepository(applicationContext).getReviewsByProvider(provider.id)
            }
            val fresh = provider.copy(
                rating = if (reviews.isEmpty()) 0.0 else reviews.map { it.rating }.average(),
                reviewCount = reviews.size
            )
            binding.txtMetrics.text = ProfessionalAdapter.metricsLine(fresh)
            binding.txtReviewsTitle.text = "Avaliações (${reviews.size})"
            binding.txtNoReviews.visibility = if (reviews.isEmpty()) View.VISIBLE else View.GONE
            binding.rvReviews.adapter = ReviewListAdapter(reviews)
        }
    }

    private fun bindProvider(provider: User) = with(binding) {
        txtName.text = provider.name
        imgAvatar.loadImage(provider.profilePictureUri)
        txtPortfolioTitle.visibility = if (provider.portfolioUris.isEmpty()) View.GONE else View.VISIBLE
        rvPortfolio.visibility = txtPortfolioTitle.visibility
        rvPortfolio.adapter = PortfolioPhotoAdapter(provider.portfolioUris)
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
        // Indisponível (perfis_prestador.is_available = false): não recebe novas solicitações.
        btnRequestService.isEnabled = provider.isAvailable
        if (!provider.isAvailable) btnRequestService.text = "Indisponível no momento"
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
