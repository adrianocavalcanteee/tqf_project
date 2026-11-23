package com.tqfapp.android.ui.common

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.annotation.DrawableRes
import androidx.recyclerview.widget.RecyclerView
import com.tqfapp.android.R
import com.tqfapp.android.databinding.ItemServiceTileBinding

/** Tiles (ícone + rótulo) dos serviços oferecidos pelo prestador, em grade. */
class ServiceTileAdapter(
    private val labels: List<String>,
    @DrawableRes private val icon: Int = R.drawable.ic_check_circle
) : RecyclerView.Adapter<ServiceTileAdapter.ViewHolder>() {

    class ViewHolder(val binding: ItemServiceTileBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder =
        ViewHolder(ItemServiceTileBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.binding.imgTileIcon.setImageResource(icon)
        holder.binding.txtTileLabel.text = labels[position]
    }

    override fun getItemCount(): Int = labels.size
}
