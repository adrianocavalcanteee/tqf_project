package com.tqfapp.android.ui.common

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.Toast
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.tqfapp.android.R
import java.io.Serializable

/** Preenche o ChipGroup dinamicamente com os serviços do prestador (lista vazia = nenhum chip). */
fun ChipGroup.bindServiceChips(services: List<String>) {
    removeAllViews()
    services.map { it.trim() }.filter { it.isNotEmpty() }.forEach { serviceName ->
        val chip = Chip(context).apply {
            text = serviceName
            setTextColor(resources.getColor(R.color.brand_blue, null))
            textSize = 13f
            isCheckable = false
            isClickable = false
            setChipIconResource(R.drawable.ic_check_circle)
            setChipIconTintResource(R.color.brand_blue)
            setChipBackgroundColorResource(android.R.color.transparent)
            chipCornerRadius = 12f
            setChipStrokeColorResource(R.color.card_stroke)
            chipStrokeWidth = 1f
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
