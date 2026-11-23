package com.tqfapp.android.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.tqfapp.android.data.network.SupabaseClient
import io.github.jan.supabase.storage.*
import kotlinx.coroutines.runBlocking
import java.io.ByteArrayOutputStream

/**
 * Repositório responsável pelo upload e gestão de arquivos de mídia (foto de perfil e portfólio)
 * no Supabase Storage no bucket 'tqf-media'.
 */
class StorageRepository(private val context: Context) {

    companion object {
        private const val BUCKET_NAME = "tqf-media"
        /** Lado máximo da foto enviada: fotos da câmera (10+ MB) viram ~200–400 KB. */
        private const val MAX_UPLOAD_SIZE = 1280
        private const val JPEG_QUALITY = 85

        fun isRemote(uri: String) = uri.startsWith("http")
    }

    /**
     * Garante que a foto de perfil e o portfólio sejam URLs públicas do Storage (visíveis em qualquer
     * aparelho). URIs locais (content://) são enviadas; as que falharem são descartadas para nunca
     * gravar no banco um endereço que só existe neste celular.
     */
    fun uploadLocalMedia(profilePicture: String?, portfolio: List<String>): Pair<String?, List<String>> {
        val avatar = when {
            profilePicture.isNullOrBlank() -> null
            isRemote(profilePicture) -> profilePicture
            else -> uploadProfilePicture(Uri.parse(profilePicture)).getOrNull()
        }
        val local = portfolio.filterNot(::isRemote)
        val uploaded = if (local.isEmpty()) emptyList() else
            uploadPortfolioImages(local.map(Uri::parse)).getOrDefault(emptyList())
        return avatar to (portfolio.filter(::isRemote) + uploaded)
    }

    /** Lê a imagem escolhida, reduz para no máximo [MAX_UPLOAD_SIZE] px e recomprime em JPEG. */
    private fun readCompressedJpeg(uri: Uri): ByteArray? = try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= MAX_UPLOAD_SIZE || bounds.outHeight / (sample * 2) >= MAX_UPLOAD_SIZE) {
            sample *= 2
        }
        val bitmap = context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        }
        bitmap?.let {
            ByteArrayOutputStream().use { out ->
                it.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
                out.toByteArray()
            }
        }
    } catch (_: Exception) {
        null
    }

    /**
     * Realiza o upload da foto de perfil para profile/{auth.uid()}/profile_timestamp.jpg
     * e retorna a URL pública/acessível gerada.
     */
    /** Foto de capa do prestador: mesma pasta profile/{uid}/ (permitida pela regra do bucket). */
    fun uploadCoverPicture(imageUri: Uri): Result<String> = uploadProfilePicture(imageUri, filePrefix = "cover")

    fun uploadProfilePicture(imageUri: Uri, filePrefix: String = "profile"): Result<String> = runBlocking {
        try {
            val authUser = SupabaseClient.auth.currentUserOrNull()
                ?: return@runBlocking Result.failure(IllegalStateException("Usuário não autenticado para upload."))

            val authUid = authUser.id
            val path = "profile/$authUid/${filePrefix}_${System.currentTimeMillis()}.jpg"

            val bytes = readCompressedJpeg(imageUri)
                ?: return@runBlocking Result.failure(IllegalArgumentException("Não foi possível ler a imagem selecionada."))

            val bucket = SupabaseClient.storage.from(BUCKET_NAME)
            bucket.upload(path, bytes)

            val publicUrl = bucket.publicUrl(path)
            Result.success(publicUrl)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Realiza o upload de imagens de portfólio para portfolio/{auth.uid()}/portfolio_timestamp_index.jpg
     * e retorna a lista de URLs públicas resultantes.
     */
    fun uploadPortfolioImages(imageUris: List<Uri>): Result<List<String>> = runBlocking {
        try {
            val authUser = SupabaseClient.auth.currentUserOrNull()
                ?: return@runBlocking Result.failure(IllegalStateException("Usuário não autenticado para upload."))

            val authUid = authUser.id
            val uploadedUrls = mutableListOf<String>()
            val bucket = SupabaseClient.storage[BUCKET_NAME]

            imageUris.forEachIndexed { index, uri ->
                try {
                    val path = "portfolio/$authUid/portfolio_${System.currentTimeMillis()}_$index.jpg"
                    val bytes = readCompressedJpeg(uri)
                    if (bytes != null) {
                        bucket.upload(path, bytes)
                        uploadedUrls.add(bucket.publicUrl(path))
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            Result.success(uploadedUrls)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
