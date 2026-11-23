package com.tqfapp.android.ui.common

import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.LruCache
import android.widget.ImageView
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat
import androidx.core.view.doOnLayout
import com.tqfapp.android.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URL

/** Lado máximo (px) das imagens decodificadas para exibição: evita bitmaps enormes de fotos da câmera. */
private const val MAX_DISPLAY_SIZE = 1080

private val imageCache = object : LruCache<String, Bitmap>(16 * 1024 * 1024) {
    override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
}

private val imageScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

/**
 * Exibe uma foto de perfil/portfólio. URLs públicas do Supabase Storage são baixadas em IO e
 * guardadas em memória; URIs locais (foto recém-escolhida) usam setImageURI. Sem foto, mostra
 * [placeholder]. Seguro em listas: a imagem só é aplicada se a view ainda pede a mesma fonte.
 */
fun ImageView.loadImage(
    source: String?,
    @DrawableRes placeholder: Int = R.drawable.ic_profile,
    onLoaded: (() -> Unit)? = null
) {
    setTag(R.id.tag_image_source, source)
    if (source.isNullOrBlank()) {
        setImageResource(placeholder)
        return
    }
    if (!source.startsWith("http")) {
        try {
            setImageURI(Uri.parse(source))
            onLoaded?.invoke()
        } catch (_: Exception) {
            setImageResource(placeholder)
        }
        return
    }
    imageCache.get(source)?.let {
        setImageBitmap(it)
        onLoaded?.invoke()
        return
    }

    setImageResource(placeholder)
    imageScope.launch {
        val bitmap = withContext(Dispatchers.IO) { downloadBitmap(source) } ?: return@launch
        imageCache.put(source, bitmap)
        if (getTag(R.id.tag_image_source) == source) {
            setImageBitmap(bitmap)
            onLoaded?.invoke()
        }
    }
}

private fun downloadBitmap(url: String): Bitmap? = try {
    val bytes = URL(url).openStream().use { it.readBytes() }
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    var sample = 1
    while (bounds.outWidth / (sample * 2) >= MAX_DISPLAY_SIZE || bounds.outHeight / (sample * 2) >= MAX_DISPLAY_SIZE) {
        sample *= 2
    }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
} catch (_: Exception) {
    null
}

/**
 * Foto do prestador nos cards e no perfil. Sem foto (ou enquanto carrega): ícone da categoria
 * centralizado sobre o degradê azul claro. Com foto: imagem em tela cheia (centerCrop).
 */
fun ImageView.loadProviderPhoto(source: String?, category: String?) {
    setBackgroundResource(R.drawable.bg_photo_placeholder)
    scaleType = ImageView.ScaleType.FIT_CENTER
    imageTintList = ColorStateList.valueOf(ContextCompat.getColor(context, R.color.tqf_primary))
    setTag(R.id.tag_photo_loaded, false)
    // O ícone ocupa ~40% do lado menor da imagem: proporcional tanto no tile de 32dp quanto no card grande.
    doOnLayout {
        if (getTag(R.id.tag_photo_loaded) == true) return@doOnLayout
        val inset = (minOf(width, height) * PLACEHOLDER_INSET_RATIO).toInt()
        setPadding(inset, inset, inset, inset)
    }
    loadImage(source, CategoryIcons.forCategory(category)) {
        setTag(R.id.tag_photo_loaded, true)
        imageTintList = null
        setPadding(0, 0, 0, 0)
        scaleType = ImageView.ScaleType.CENTER_CROP
    }
}

/** Recuo do ícone de categoria em relação ao lado menor (0.3 de cada lado = ícone com 40%). */
private const val PLACEHOLDER_INSET_RATIO = 0.3f
