package com.tqfapp.android.utils

import android.content.Context
import android.location.Address
import android.location.Geocoder
import com.google.android.gms.maps.model.LatLng
import java.util.Locale

/**
 * Utilitário responsável pela conversão amigável de endereços em coordenadas (Geocoding)
 * e obter o endereço formatado a partir de latitude e longitude (Reverse Geocoding).
 */
object LocationUtils {

    /**
     * Converte um texto de endereço (ex: "Av. Paulista, 1000, São Paulo") em coordenadas LatLng.
     *
     * @param context Contexto da aplicação.
     * @param addressText Texto do endereço digitado pelo prestador.
     * @return Objeto LatLng com as coordenadas ou null caso não seja localizado.
     */
    fun getCoordinatesFromAddress(context: Context, addressText: String): LatLng? {
        if (addressText.trim().isEmpty()) return null

        return try {
            val geocoder = Geocoder(context, Locale.getDefault())
            @Suppress("DEPRECATION")
            val addresses: List<Address>? = geocoder.getFromLocationName(addressText, 1)

            if (!addresses.isNullOrEmpty()) {
                val address = addresses[0]
                LatLng(address.latitude, address.longitude)
            } else {
                null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Obtém um texto de endereço amigável a partir das coordenadas de Latitude e Longitude (Reverse Geocoding).
     */
    fun getAddressFromCoordinates(context: Context, latitude: Double, longitude: Double): String? {
        return try {
            val geocoder = Geocoder(context, Locale.getDefault())
            @Suppress("DEPRECATION")
            val addresses: List<Address>? = geocoder.getFromLocation(latitude, longitude, 1)

            if (!addresses.isNullOrEmpty()) {
                val address = addresses[0]
                val thoroughfare = address.thoroughfare ?: ""
                val subThoroughfare = address.subThoroughfare ?: ""
                val subLocality = address.subLocality ?: address.locality ?: ""
                val city = address.subAdminArea ?: address.adminArea ?: ""

                val parts = listOf(
                    if (thoroughfare.isNotEmpty()) "$thoroughfare $subThoroughfare".trim() else "",
                    subLocality,
                    city
                ).filter { it.isNotEmpty() }

                if (parts.isNotEmpty()) parts.joinToString(" - ") else address.getAddressLine(0)
            } else {
                null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
