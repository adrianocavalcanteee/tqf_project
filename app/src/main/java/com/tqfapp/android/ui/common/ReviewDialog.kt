package com.tqfapp.android.ui.common

import android.content.DialogInterface
import android.view.LayoutInflater
import android.widget.RatingBar
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.tqfapp.android.R
import com.tqfapp.android.business.ReviewRules
import com.tqfapp.android.data.repository.ReviewRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Avaliação de um serviço CONCLUIDO: nota (1–5) + comentário opcional.
 * Contratante avalia o prestador aceito; prestador aceito avalia o cliente (regras do RLS de avaliacoes).
 * Grava com reviewed_id = [reviewedId] e service_request_id = [serviceId].
 * O diálogo só fecha após o envio dar certo; [onReviewed] é chamado para a tela se atualizar.
 */
fun Fragment.showReviewDialog(
    serviceId: String,
    reviewedId: String,
    serviceTitle: String,
    reviewedName: String,
    onReviewed: () -> Unit
) {
    if (serviceId.isBlank() || reviewedId.isBlank()) {
        Toast.makeText(requireContext(), "Não foi possível identificar este serviço para avaliação.", Toast.LENGTH_SHORT).show()
        return
    }

    val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_review, null)
    val ratingBar = dialogView.findViewById<RatingBar>(R.id.ratingBar)
    val etComment = dialogView.findViewById<TextInputEditText>(R.id.etReviewComment)
    dialogView.findViewById<TextView>(R.id.txtReviewDialogTitle)?.text = "Avaliar $reviewedName"
    dialogView.findViewById<TextView>(R.id.txtReviewDialogSubtitle)?.text =
        "Como foi o serviço \"$serviceTitle\"? Toque nas estrelas para dar sua nota."

    val dialog = MaterialAlertDialogBuilder(requireContext())
        .setView(dialogView)
        .setPositiveButton("Enviar avaliação", null)
        .setNegativeButton("Cancelar", null)
        .create()

    // Botão positivo sem fechar automaticamente: nota inválida ou erro mantêm o que foi digitado.
    dialog.setOnShowListener {
        val sendButton = dialog.getButton(DialogInterface.BUTTON_POSITIVE)
        sendButton.setOnClickListener {
            val rating = ratingBar.rating.toInt()
            if (!ReviewRules.isValidRating(rating)) {
                Toast.makeText(requireContext(), "Escolha de 1 a 5 estrelas.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val comment = etComment.text?.toString()?.trim().orEmpty()
            val appContext = requireContext().applicationContext
            sendButton.isEnabled = false
            viewLifecycleOwner.lifecycleScope.launch {
                val result = withContext(Dispatchers.IO) {
                    ReviewRepository(appContext).addReview(serviceId, reviewedId, rating, comment)
                }
                sendButton.isEnabled = true
                result.onSuccess {
                    dialog.dismiss()
                    Toast.makeText(appContext, "Avaliação registrada para $reviewedName!", Toast.LENGTH_LONG).show()
                    onReviewed()
                }.onFailure {
                    Toast.makeText(appContext, it.message ?: "Não foi possível registrar a avaliação.", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
    dialog.show()
}
