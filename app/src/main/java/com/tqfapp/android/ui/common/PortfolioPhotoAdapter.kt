package com.tqfapp.android.ui.common

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import com.tqfapp.android.R

/** Fotos reais do portfólio do prestador (URLs do Supabase Storage), em lista horizontal. */
class PortfolioPhotoAdapter(private val photos: List<String>) :
    RecyclerView.Adapter<PortfolioPhotoAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val image: ImageView = view.findViewById(R.id.imgPortfolio)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder =
        ViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.item_portfolio, parent, false))

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.image.loadImage(photos[position])
    }

    override fun getItemCount(): Int = photos.size
}
