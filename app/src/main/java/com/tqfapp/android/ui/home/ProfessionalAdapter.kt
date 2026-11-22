package com.tqfapp.android.ui.home

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.tqfapp.android.business.RatingFormat
import com.tqfapp.android.data.model.User
import com.tqfapp.android.databinding.ItemProfessionalBinding
import java.util.Locale
import com.tqfapp.android.ui.common.loadImage

/**
 * Lista de prestadores (Home e Sugestões). Começa vazia e é alimentada via [updateData].
 */
class ProfessionalAdapter(
    private val onProfileClick: (User) -> Unit
) : ListAdapter<User, ProfessionalAdapter.ViewHolder>(DIFF) {

    fun updateData(providers: List<User>) {
        submitList(providers.toList())
    }

    inner class ViewHolder(private val binding: ItemProfessionalBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(provider: User) = with(binding) {
            txtName.text = provider.name
            imgAvatar.loadImage(provider.profilePictureUri)
            txtCategory.text = listOfNotNull(provider.category, provider.specialty)
                .filter { it.isNotBlank() }
                .distinct()
                .joinToString(" • ")
                .ifEmpty { "Prestador de serviços" }
            txtStatusBadge.visibility = if (provider.isAvailable) View.VISIBLE else View.GONE
            txtMetrics.text = metricsLine(provider)
            txtPrice.text = provider.priceLabel()

            root.setOnClickListener { onProfileClick(provider) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder =
        ViewHolder(ItemProfessionalBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: ViewHolder, position: Int) = holder.bind(getItem(position))

    companion object {
        private val PT_BR = Locale("pt", "BR")

        /** "4,9 • 86 avaliações • 2,1 km" (partes ausentes são omitidas). */
        fun metricsLine(provider: User): String {
            val parts = mutableListOf<String>()
            if (provider.reviewCount > 0) {
                parts += RatingFormat.value(provider.rating)
                parts += RatingFormat.count(provider.reviewCount)
            } else {
                parts += "Novo no TQF"
            }
            provider.distanceKm?.let { parts += String.format(PT_BR, "%.1f km", it) }
            return parts.joinToString(" • ")
        }

        private val DIFF = object : DiffUtil.ItemCallback<User>() {
            override fun areItemsTheSame(oldItem: User, newItem: User) = oldItem.id == newItem.id
            override fun areContentsTheSame(oldItem: User, newItem: User) = oldItem == newItem
        }
    }
}
