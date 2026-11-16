package com.tqfapp.android.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.util.Log
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MapStyleOptions
import com.google.android.gms.maps.model.MarkerOptions
import com.tqfapp.android.R

/**
 * Utilitário responsável pelo gerenciamento, estilização e configuração do Google Maps no app.
 */
object MapUtils {

    /**
     * Converte um Vector Drawable (ex: R.drawable.ic_user_location_dot) em um BitmapDescriptor
     * para uso como ícone de Marcador personalizado no Google Maps.
     */
    fun getBitmapDescriptorFromVector(
        context: Context,
        @DrawableRes vectorResId: Int
    ): BitmapDescriptor? {
        val drawable = ContextCompat.getDrawable(context, vectorResId) ?: return null
        drawable.setBounds(0, 0, drawable.intrinsicWidth, drawable.intrinsicHeight)
        val bitmap = Bitmap.createBitmap(
            drawable.intrinsicWidth,
            drawable.intrinsicHeight,
            Bitmap.Config.ARGB_8888
        )
        val canvas = Canvas(bitmap)
        drawable.draw(canvas)
        return BitmapDescriptorFactory.fromBitmap(bitmap)
    }

    /**
     * Aplica um estilo visual personalizado em formato JSON ao Google Maps a partir de um recurso raw.
     */
    fun applyCustomMapStyle(
        context: Context,
        googleMap: GoogleMap,
        rawResId: Int = R.raw.map_style
    ): Boolean {
        return try {
            val style = MapStyleOptions.loadRawResourceStyle(context, rawResId)
            val success = googleMap.setMapStyle(style)
            if (!success) {
                Log.e("MapUtils", "Falha ao aplicar a formatação do estilo no Google Maps.")
            }
            success
        } catch (e: Exception) {
            Log.e("MapUtils", "Erro ao carregar o arquivo de estilo do mapa: ", e)
            false
        }
    }

    /**
     * Aplica um estilo visual personalizado ao Google Maps a partir de uma String JSON.
     */
    fun applyCustomMapStyleFromJson(
        googleMap: GoogleMap,
        jsonStyle: String
    ): Boolean {
        return try {
            val style = MapStyleOptions(jsonStyle)
            googleMap.setMapStyle(style)
        } catch (e: Exception) {
            Log.e("MapUtils", "Erro ao carregar o estilo JSON do mapa: ", e)
            false
        }
    }

    /**
     * Configura e centraliza o mapa na localização do escritório do prestador.
     */
    fun setupOfficeLocationOnMap(
        googleMap: GoogleMap,
        latitude: Double,
        longitude: Double,
        title: String = "Escritório do Prestador",
        snippet: String? = "Local de Atendimento",
        zoomLevel: Float = 15f
    ) {
        val officeLatLng = LatLng(latitude, longitude)

        googleMap.apply {
            // Limpa marcadores anteriores para evitar duplicidades ao atualizar
            clear()

            // Configurações de interface do mapa
            uiSettings.isZoomControlsEnabled = true
            uiSettings.isMapToolbarEnabled = true
            uiSettings.isCompassEnabled = true

            // Adiciona o marcador na posição do escritório
            val markerOptions = MarkerOptions()
                .position(officeLatLng)
                .title(title)

            snippet?.let { markerOptions.snippet(it) }

            val marker = addMarker(markerOptions)
            marker?.showInfoWindow() // Exibe o balão de informação automaticamente

            // Move a câmera com animação até o local do escritório
            animateCamera(CameraUpdateFactory.newLatLngZoom(officeLatLng, zoomLevel))
        }
    }

    /**
     * Retorna a cor de matiz (Hue) do marcador correspondente para cada categoria de serviço.
     */
    fun getCategoryHue(category: String?): Float {
        if (category.isNullOrBlank()) return BitmapDescriptorFactory.HUE_RED

        return when (category.lowercase().trim()) {
            "assistência técnica" -> BitmapDescriptorFactory.HUE_GREEN
            "reformas e reparos" -> BitmapDescriptorFactory.HUE_ROSE
            "serviços domésticos" -> BitmapDescriptorFactory.HUE_CYAN
            "design e tecnologia" -> BitmapDescriptorFactory.HUE_ORANGE
            "eventos" -> BitmapDescriptorFactory.HUE_YELLOW
            "aulas e treinamentos" -> BitmapDescriptorFactory.HUE_VIOLET
            "saúde e bem-estar" -> BitmapDescriptorFactory.HUE_RED
            "autos e transporte" -> BitmapDescriptorFactory.HUE_MAGENTA
            else -> BitmapDescriptorFactory.HUE_RED
        }
    }
}
