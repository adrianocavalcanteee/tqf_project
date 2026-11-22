package com.tqfapp.android.ui.request

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.tqfapp.android.ui.common.serializableExtra
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.tqfapp.android.data.model.Solicitacao
import com.tqfapp.android.data.model.StatusSolicitacao
import com.tqfapp.android.data.model.label
import com.tqfapp.android.data.model.resumoParaPrestador
import com.tqfapp.android.data.model.whatsAppUrl
import com.tqfapp.android.data.repository.ServiceRepository
import com.tqfapp.android.databinding.ActivityAvaliarSolicitacaoBinding
import com.tqfapp.android.ui.common.openWhatsApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Tela do Prestador: avalia a descrição e decide.
 * - Aceitar: PENDENTE ➔ EM_ANDAMENTO, card vai para "Contratações" e o WhatsApp é liberado.
 * - Recusar: PENDENTE ➔ RECUSADO; o cliente é avisado e recebe sugestões da mesma categoria.
 * - Concluir (já aceito): EM_ANDAMENTO ➔ CONCLUIDO, vai para o Histórico do cliente.
 */
class AvaliarSolicitacaoActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAvaliarSolicitacaoBinding
    private lateinit var solicitacao: Solicitacao

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAvaliarSolicitacaoBinding.inflate(layoutInflater)
        setContentView(binding.root)

        solicitacao = intent.serializableExtra<Solicitacao>(EXTRA_SOLICITACAO) ?: run {
            finish()
            return
        }

        binding.btnBack.setOnClickListener { finish() }
        binding.btnAccept.setOnClickListener { accept() }
        binding.btnRefuse.setOnClickListener { confirmRefuse() }
        binding.btnFinish.setOnClickListener { confirmFinish() }
        render()
    }

    private fun render() = with(binding) {
        txtCategory.text = solicitacao.category
        txtStatus.text = solicitacao.status.label()
        txtTitle.text = solicitacao.title
        txtClient.text = listOf(
            "Cliente: ${solicitacao.clientName.ifBlank { "Cliente" }}",
            solicitacao.date
        ).filter { it.isNotBlank() }.joinToString(" • ")
        txtDescription.text = listOf(
            solicitacao.description,
            solicitacao.resumoParaPrestador(distanceKm = null)
        ).filter { it.isNotBlank() }.joinToString("\n\n")

        val isPending = solicitacao.status == StatusSolicitacao.PENDENTE
        val inProgress = solicitacao.status == StatusSolicitacao.EM_ANDAMENTO
        // Pedido aberto (sem prestador alvo) não pode ser recusado individualmente: só aceito.
        val isDirected = solicitacao.providerId.isNotBlank()
        layoutDecision.visibility = if (isPending) View.VISIBLE else View.GONE
        btnRefuse.visibility = if (isDirected) View.VISIBLE else View.GONE
        btnFinish.visibility = if (inProgress) View.VISIBLE else View.GONE

        val whatsAppUrl = solicitacao.whatsAppUrl(
            "Olá ${solicitacao.clientName}! Aceitei sua solicitação no TQFapp: \"${solicitacao.title}\". Vamos alinhar os detalhes?"
        )
        btnWhatsApp.visibility = if (whatsAppUrl != null) View.VISIBLE else View.GONE
        btnWhatsApp.setOnClickListener { whatsAppUrl?.let { openWhatsApp(it) } }

        txtStateHint.text = when {
            isPending && !isDirected -> "Pedido aberto da sua categoria. Aceite para liberar o contato com o cliente pelo WhatsApp."
            isPending -> "Aceite para liberar o contato com o cliente pelo WhatsApp. Se recusar, o cliente receberá sugestões de outros prestadores."
            inProgress && whatsAppUrl == null -> "Serviço aceito, mas o cliente não informou um telefone válido."
            inProgress -> "Serviço aceito. Combine os detalhes pelo WhatsApp e conclua ao finalizar."
            else -> "Esta solicitação está com status ${solicitacao.status.label().lowercase()}."
        }
    }

    private fun accept() {
        runAction(
            action = { ServiceRepository(applicationContext).acceptRequest(solicitacao) },
            successMessage = "Solicitação aceita! Ela agora está em Contratações."
        )
    }

    private fun confirmRefuse() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Recusar solicitação?")
            .setMessage("O cliente será avisado da indisponibilidade e receberá sugestões de outros prestadores de ${solicitacao.category}.")
            .setPositiveButton("Recusar") { _, _ ->
                runAction(
                    action = { ServiceRepository(applicationContext).refuseRequest(solicitacao) },
                    successMessage = "Solicitação recusada. O cliente receberá sugestões.",
                    finishAfter = true
                )
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun confirmFinish() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Concluir serviço?")
            .setMessage("O serviço irá para o histórico do cliente, que poderá avaliá-lo.")
            .setPositiveButton("Concluir") { _, _ ->
                runAction(
                    action = { ServiceRepository(applicationContext).finalizeRequest(solicitacao) },
                    successMessage = "Serviço concluído!",
                    finishAfter = true
                )
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun runAction(
        action: () -> Result<Solicitacao>,
        successMessage: String,
        finishAfter: Boolean = false
    ) {
        setLoading(true)
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) { action() }
            setLoading(false)

            val updated = result.getOrElse { e ->
                Toast.makeText(
                    this@AvaliarSolicitacaoActivity,
                    e.message ?: "Não foi possível atualizar a solicitação. Tente novamente.",
                    Toast.LENGTH_LONG
                ).show()
                return@launch
            }

            solicitacao = updated
            setResult(Activity.RESULT_OK)
            Toast.makeText(this@AvaliarSolicitacaoActivity, successMessage, Toast.LENGTH_SHORT).show()
            if (finishAfter) finish() else render()
        }
    }

    private fun setLoading(loading: Boolean) = with(binding) {
        progressAction.visibility = if (loading) View.VISIBLE else View.GONE
        btnAccept.isEnabled = !loading
        btnRefuse.isEnabled = !loading
        btnFinish.isEnabled = !loading
    }

    companion object {
        private const val EXTRA_SOLICITACAO = "extra_solicitacao"

        fun newIntent(context: Context, solicitacao: Solicitacao): Intent =
            Intent(context, AvaliarSolicitacaoActivity::class.java).putExtra(EXTRA_SOLICITACAO, solicitacao)
    }
}
