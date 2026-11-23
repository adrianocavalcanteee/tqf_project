package com.tqfapp.android.ui.common

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.res.ColorStateList
import android.view.View
import android.widget.TextView
import com.google.android.material.button.MaterialButton
import androidx.core.content.ContextCompat
import com.tqfapp.android.R
import com.tqfapp.android.business.ServiceStatus
import com.tqfapp.android.data.model.label

/**
 * Badge de status da solicitação no padrão do design system:
 * pendente (âmbar), em andamento (azul), concluída (verde), recusada (vermelho), cancelada (cinza).
 */
fun TextView.applyStatusBadge(status: ServiceStatus) {
    val (bg, fg) = when (status) {
        ServiceStatus.PENDENTE -> R.color.tqf_status_pending_bg to R.color.tqf_status_pending_text
        ServiceStatus.EM_ANDAMENTO -> R.color.tqf_status_progress_bg to R.color.tqf_status_progress_text
        ServiceStatus.CONCLUIDO -> R.color.tqf_status_done_bg to R.color.tqf_status_done_text
        ServiceStatus.RECUSADO -> R.color.tqf_status_refused_bg to R.color.tqf_status_refused_text
        ServiceStatus.CANCELADO -> R.color.tqf_status_cancelled_bg to R.color.tqf_status_cancelled_text
    }
    text = status.label()
    setBackgroundResource(R.drawable.bg_badge)
    backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(context, bg))
    setTextColor(ContextCompat.getColor(context, fg))
}

/**
 * Skeleton de carregamento: mostra a view com uma pulsação suave de opacidade enquanto
 * [loading] for true; esconde e para a animação ao terminar.
 */
fun View.showSkeleton(loading: Boolean) {
    val running = getTag(R.id.tag_skeleton_animator) as? ValueAnimator
    if (loading) {
        visibility = View.VISIBLE
        if (running == null) {
            val animator = ObjectAnimator.ofFloat(this, View.ALPHA, 1f, 0.45f).apply {
                duration = 750
                repeatMode = ValueAnimator.REVERSE
                repeatCount = ValueAnimator.INFINITE
                start()
            }
            setTag(R.id.tag_skeleton_animator, animator)
        }
    } else {
        running?.cancel()
        setTag(R.id.tag_skeleton_animator, null)
        alpha = 1f
        visibility = View.GONE
    }
}

/**
 * Ênfase da ação principal de um card: true = botão primário azul (ex.: "Avaliar"),
 * false = estilo suave azul claro (Widget.TQF.Button.Soft).
 */
fun MaterialButton.setPrimaryEmphasis(primary: Boolean) {
    val bg = if (primary) R.color.tqf_primary else R.color.tqf_primary_soft
    val fg = if (primary) R.color.tqf_text_on_primary else R.color.tqf_primary
    backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(context, bg))
    setTextColor(ContextCompat.getColor(context, fg))
}
