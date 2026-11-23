package com.tqfapp.android.ui.common

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.ViewCompat
import android.view.View
import android.widget.Toast
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.tqfapp.android.R
import java.io.Serializable

/** Preenche o ChipGroup dinamicamente com os serviços do prestador (lista vazia = nenhum chip). */
fun ChipGroup.bindServiceChips(services: List<String>) {
    removeAllViews()
    services.map { it.trim() }.filter { it.isNotEmpty() }.forEach { serviceName ->
        // Chip informativo no padrão do design system: fundo azul claro, texto azul, sem borda.
        val chip = Chip(context).apply {
            text = serviceName
            isCheckable = false
            isClickable = false
            setChipIconResource(R.drawable.ic_check_circle)
            setChipIconTintResource(R.color.tqf_primary)
            setChipBackgroundColorResource(R.color.tqf_primary_soft)
            setTextColor(resources.getColor(R.color.tqf_primary, null))
            chipStrokeWidth = 0f
        }
        addView(chip)
    }
}

/** Lê um extra Serializable de forma compatível com API 24 até 33+. */
inline fun <reified T : Serializable> Intent.serializableExtra(key: String): T? =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        getSerializableExtra(key, T::class.java)
    } else {
        @Suppress("DEPRECATION")
        getSerializableExtra(key) as? T
    }

/** Abre a conversa no WhatsApp (comunicação externa após o aceite do serviço). */
fun Context.openWhatsApp(url: String) {
    try {
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    } catch (e: Exception) {
        e.printStackTrace()
        Toast.makeText(this, "Não foi possível abrir o WhatsApp no dispositivo.", Toast.LENGTH_SHORT).show()
    }
}

/**
 * targetSdk 35+: o Android desenha o app atrás da barra de status e da barra de navegação
 * (edge-to-edge obrigatório). Soma essas áreas ao padding original da raiz da tela, deixando o
 * fundo atrás das barras e o conteúdo fora delas. Com o teclado aberto, a base sobe junto
 * para não cobrir os campos.
 */
fun View.applySystemBarsPadding() {
    val start = paddingLeft
    val top = paddingTop
    val end = paddingRight
    val bottom = paddingBottom
    ViewCompat.setOnApplyWindowInsetsListener(this) { view, insets ->
        val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
        val keyboard = insets.getInsets(WindowInsetsCompat.Type.ime())
        view.setPadding(
            start + bars.left,
            top + bars.top,
            end + bars.right,
            bottom + maxOf(bars.bottom, keyboard.bottom)
        )
        insets
    }
}
