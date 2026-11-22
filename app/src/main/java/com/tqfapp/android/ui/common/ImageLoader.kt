package com.tqfapp.android.ui.common

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.LruCache
import android.widget.ImageView
import androidx.annotation.DrawableRes
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
fun ImageView.loadImage(source: String?, @DrawableRes placeholder: Int = R.drawable.ic_profile) {
    setTag(R.id.tag_image_source, source)
    if (source.isNullOrBlank()) {
        setImageResource(placeholder)
        return
    }
    if (!source.startsWith("http")) {
        try { setImageURI(Uri.parse(source)) } catch (_: Exception) { setImageResource(placeholder) }
        return
    }
    imageCache.get(source)?.let {
        setImageBitmap(it)
        return
    }

    setImageResource(placeholder)
    imageScope.launch {
        val bitmap = withContext(Dispatchers.IO) { downloadBitmap(source) } ?: return@launch
        imageCache.put(source, bitmap)
        if (getTag(R.id.tag_image_source) == source) setImageBitmap(bitmap)
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
