package com.tqfapp.android.ui.request

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.tqfapp.android.business.RecommendationEngine
import com.tqfapp.android.data.model.Solicitacao
import com.tqfapp.android.data.repository.UserRepository
import com.tqfapp.android.databinding.ActivitySugestaoPrestadoresBinding
import com.tqfapp.android.ui.home.ProfessionalAdapter
import com.tqfapp.android.utils.UserSessionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Motor de recomendação (lado do Contratante): após uma recusa, lista outros prestadores
 * disponíveis da MESMA categoria, excluindo quem recusou.
 */
class SugestaoPrestadoresActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySugestaoPrestadoresBinding
    private val adapter = ProfessionalAdapter { provider ->
        startActivity(ProviderProfileActivity.newIntent(this, provider))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySugestaoPrestadoresBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val category = intent.getStringExtra(EXTRA_CATEGORY).orEmpty()
        val excludedProviderId = intent.getStringExtra(EXTRA_EXCLUDED_PROVIDER_ID)
        val requestTitle = intent.getStringExtra(EXTRA_REQUEST_TITLE)
        intent.getStringExtra(EXTRA_REQUEST_ID)?.let { UserSessionManager.markRefusedRequestSeen(this, it) }

        binding.txtSubtitle.text = if (requestTitle.isNullOrBlank()) {
            "Outros profissionais de $category:"
        } else {
            "O prestador não está disponível para \"$requestTitle\". Veja outros profissionais de $category:"
        }
        binding.btnBack.setOnClickListener { finish() }
        binding.rvSuggestions.layoutManager = LinearLayoutManager(this)
        binding.rvSuggestions.adapter = adapter

        loadSuggestions(category, excludedProviderId)
    }

    private fun loadSuggestions(category: String, excludedProviderId: String?) {
        binding.progressSuggestions.visibility = View.VISIBLE
        lifecycleScope.launch {
            val suggestions = withContext(Dispatchers.IO) {
                RecommendationEngine.suggest(
                    providers = UserRepository(applicationContext).getProviders(),
                    category = category,
                    excludedProviderId = excludedProviderId
                )
            }
            binding.progressSuggestions.visibility = View.GONE
            adapter.updateData(suggestions)
            binding.txtEmpty.visibility = if (suggestions.isEmpty()) View.VISIBLE else View.GONE
        }
    }

    companion object {
        private const val EXTRA_CATEGORY = "extra_category"
        private const val EXTRA_EXCLUDED_PROVIDER_ID = "extra_excluded_provider_id"
        private const val EXTRA_REQUEST_ID = "extra_request_id"
        private const val EXTRA_REQUEST_TITLE = "extra_request_title"

        /** Abre as sugestões a partir de uma solicitação recusada. */
        fun newIntent(context: Context, refused: Solicitacao): Intent =
            Intent(context, SugestaoPrestadoresActivity::class.java)
                .putExtra(EXTRA_CATEGORY, refused.category)
                .putExtra(EXTRA_EXCLUDED_PROVIDER_ID, refused.providerId)
                .putExtra(EXTRA_REQUEST_ID, refused.id)
                .putExtra(EXTRA_REQUEST_TITLE, refused.title)
    }
}
