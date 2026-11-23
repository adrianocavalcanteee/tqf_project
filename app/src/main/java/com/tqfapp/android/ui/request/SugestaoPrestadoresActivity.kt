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
import com.tqfapp.android.ui.common.applySystemBarsPadding
import com.tqfapp.android.data.repository.ServiceRepository
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import android.widget.Toast

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
        binding.root.applySystemBarsPadding()

        val category = intent.getStringExtra(EXTRA_CATEGORY).orEmpty()
        val excludedProviderId = intent.getStringExtra(EXTRA_EXCLUDED_PROVIDER_ID)
        val requestTitle = intent.getStringExtra(EXTRA_REQUEST_TITLE)
        val requestId = intent.getStringExtra(EXTRA_REQUEST_ID)
        requestId?.let { UserSessionManager.markRefusedRequestSeen(this, it) }
        if (requestId != null) {
            binding.btnCancelService.visibility = View.VISIBLE
            binding.btnCancelService.setOnClickListener { confirmCancel(requestId, requestTitle) }
        }

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

    /** Encerra o pedido recusado (RECUSADO -> CANCELADO) em vez de procurar outro prestador. */
    private fun confirmCancel(requestId: String, title: String?) {
        MaterialAlertDialogBuilder(this)
            .setTitle("Cancelar serviço?")
            .setMessage(
                if (title.isNullOrBlank()) "O pedido será encerrado. Você pode abrir um novo quando quiser."
                else "\"$title\" será encerrado. Você pode abrir um novo pedido quando quiser."
            )
            .setPositiveButton("Cancelar serviço") { _, _ ->
                binding.btnCancelService.isEnabled = false
                lifecycleScope.launch {
                    val result = withContext(Dispatchers.IO) {
                        ServiceRepository(applicationContext).cancelRequestById(requestId)
                    }
                    result.onSuccess {
                        Toast.makeText(applicationContext, "Serviço cancelado.", Toast.LENGTH_SHORT).show()
                        finish()
                    }.onFailure {
                        binding.btnCancelService.isEnabled = true
                        Toast.makeText(applicationContext, it.message ?: "Não foi possível cancelar.", Toast.LENGTH_LONG).show()
                    }
                }
            }
            .setNegativeButton("Voltar", null)
            .show()
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
