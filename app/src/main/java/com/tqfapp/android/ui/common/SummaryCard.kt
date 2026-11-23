package com.tqfapp.android.ui.common

import android.view.View
import com.tqfapp.android.databinding.ViewSummaryCardBinding

/**
 * Preenche o card-resumo azul das Homes. O anel mostra [done] de [total] (0 quando não há itens).
 * [onAction] null esconde o botão.
 */
fun ViewSummaryCardBinding.bind(
    title: String,
    text: String,
    done: Int,
    total: Int,
    actionLabel: String,
    onAction: (() -> Unit)?
) {
    val percent = if (total > 0) (done * 100) / total else 0
    txtSummaryTitle.text = title
    txtSummaryText.text = text
    progressSummary.setProgressCompat(percent, true)
    txtSummaryPercent.text = "$percent%"
    btnSummaryAction.text = actionLabel
    btnSummaryAction.visibility = if (onAction == null) View.GONE else View.VISIBLE
    btnSummaryAction.setOnClickListener { onAction?.invoke() }
}
