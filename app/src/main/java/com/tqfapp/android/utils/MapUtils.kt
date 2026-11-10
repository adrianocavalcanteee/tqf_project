package com.tqfapp.android.utils

import android.content.Context
import android.util.Log
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MapStyleOptions
import com.google.android.gms.maps.model.MarkerOptions
import com.tqfapp.android.R

/**
 * Utilitário responsável pelo gerenciamento, estilização e configuração do Google Maps no app.
 */
object MapUtils {

    /**
     * Aplica um estilo visual personalizado em formato JSON ao Google Maps a partir de um recurso raw.
     *
     * @param context Contexto da aplicação ou fragmento.
     * @param googleMap Instância do GoogleMap.
     * @param rawResId ID do recurso raw do arquivo JSON de estilo (padrão R.raw.map_style).
     * @return Boolean indicando se o estilo foi aplicado com sucesso.
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
     *
     * @param googleMap Instância do GoogleMap inicializada.
     * @param latitude Coordenada de latitude do escritório.
     * @param longitude Coordenada de longitude do escritório.
     * @param title Título exibido no marcador (ex: "Escritório do Prestador").
     * @param snippet Subtítulo ou detalhes adicionais no marcador.
     * @param zoomLevel Nível de zoom inicial do mapa (padrão 15f).
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
}
