package com.tqfapp.android.utils

import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions

/**
 * Utilitário responsável pelo gerenciamento e configuração do Google Maps no app.
 */
object MapUtils {

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
