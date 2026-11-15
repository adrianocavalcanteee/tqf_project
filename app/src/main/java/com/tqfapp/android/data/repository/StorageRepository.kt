package com.tqfapp.android.data.repository

import android.content.Context
import android.net.Uri
import com.tqfapp.android.data.network.SupabaseClient
import io.github.jan.supabase.storage.*
import kotlinx.coroutines.runBlocking
import java.io.InputStream

/**
 * Repositório responsável pelo upload e gestão de arquivos de mídia (foto de perfil e portfólio)
 * no Supabase Storage no bucket 'tqf-media'.
 */
class StorageRepository(private val context: Context) {

    companion object {
        private const val BUCKET_NAME = "tqf-media"
    }

    /**
     * Realiza o upload da foto de perfil para profile/{auth.uid()}/profile_timestamp.jpg
     * e retorna a URL pública/acessível gerada.
     */
    fun uploadProfilePicture(imageUri: Uri): Result<String> = runBlocking {
        try {
            val authUser = SupabaseClient.auth.currentUserOrNull()
                ?: return@runBlocking Result.failure(IllegalStateException("Usuário não autenticado para upload."))

            val authUid = authUser.id
            val path = "profile/$authUid/profile_${System.currentTimeMillis()}.jpg"

            val inputStream: InputStream = context.contentResolver.openInputStream(imageUri)
                ?: return@runBlocking Result.failure(IllegalArgumentException("Não foi possível ler a imagem selecionada."))

            val bytes = inputStream.use { it.readBytes() }

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
                    val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
                    if (inputStream != null) {
                        val bytes = inputStream.use { it.readBytes() }
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
