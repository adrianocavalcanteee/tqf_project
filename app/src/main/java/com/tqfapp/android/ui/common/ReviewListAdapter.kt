package com.tqfapp.android.ui.common

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.tqfapp.android.R
import com.tqfapp.android.data.model.Review
import com.tqfapp.android.databinding.ItemReviewBinding

/** Lista de avaliações reais (item_review): perfil do prestador e tela "Todas as avaliações". */
class ReviewListAdapter(private val items: List<Review>) : RecyclerView.Adapter<ReviewListAdapter.ViewHolder>() {

    class ViewHolder(val binding: ItemReviewBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder =
        ViewHolder(ItemReviewBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val review = items[position]
        holder.binding.txtReviewerName.text = "Cliente"
        holder.binding.txtReviewDate.text = review.date
        holder.binding.txtReviewComment.text =
            listOf("⭐ ${review.rating}/5", review.comment).filter { it.isNotBlank() }.joinToString(" - ")
        holder.binding.imgReviewerAvatar.setImageResource(R.drawable.ic_profile)
    }

    override fun getItemCount(): Int = items.size
}
