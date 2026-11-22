package com.tqfapp.android.ui.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.tqfapp.android.R
import com.tqfapp.android.data.repository.ReviewRepository
import com.tqfapp.android.databinding.ItemReviewBinding
import java.util.Locale

class AllReviewsFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_all_reviews, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val providerId = arguments?.getString(ARG_PROVIDER_ID) ?: ""
        val providerName = arguments?.getString(ARG_PROVIDER_NAME) ?: "Prestador"

        val btnBack = view.findViewById<ImageView>(R.id.btnBack)
        val txtHeaderSubtitle = view.findViewById<TextView>(R.id.txtHeaderSubtitle)
        val rvAllReviews = view.findViewById<RecyclerView>(R.id.rvAllReviews)
        val layoutEmptyState = view.findViewById<View>(R.id.layoutEmptyState)

        btnBack.setOnClickListener {
            parentFragmentManager.popBackStack()
        }

        val reviews = if (providerId.isNotBlank()) {
            ReviewRepository(requireContext()).getReviewsByProvider(providerId)
        } else {
            emptyList()
        }

        if (reviews.isEmpty()) {
            txtHeaderSubtitle.text = "Nenhuma avaliação encontrada para $providerName"
            rvAllReviews.visibility = View.GONE
            layoutEmptyState.visibility = View.VISIBLE
        } else {
            val average = reviews.map { it.rating }.average()
            val count = reviews.size
            txtHeaderSubtitle.text = String.format(
                Locale.forLanguageTag("pt-BR"),
                "%.1f ★ • %d %s para %s",
                average,
                count,
                if (count == 1) "avaliação" else "avaliações",
                providerName
            )
            rvAllReviews.visibility = View.VISIBLE
            layoutEmptyState.visibility = View.GONE

            rvAllReviews.layoutManager = LinearLayoutManager(requireContext())
            rvAllReviews.adapter = AllReviewsAdapter(reviews)
        }
    }

    private inner class AllReviewsAdapter(
        private val items: List<com.tqfapp.android.data.model.Review>
    ) : RecyclerView.Adapter<AllReviewsAdapter.ViewHolder>() {

        inner class ViewHolder(val binding: ItemReviewBinding) : RecyclerView.ViewHolder(binding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val binding = ItemReviewBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return ViewHolder(binding)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val review = items[position]
            holder.binding.txtReviewerName.text = "Cliente"
            holder.binding.txtReviewDate.text = review.date
            holder.binding.txtReviewComment.text = "⭐ ${review.rating}/5 - ${review.comment}"
            holder.binding.imgReviewerAvatar.setImageResource(R.drawable.ic_profile)
        }

        override fun getItemCount(): Int = items.size
    }

    companion object {
        private const val ARG_PROVIDER_ID = "arg_provider_id"
        private const val ARG_PROVIDER_NAME = "arg_provider_name"

        fun newInstance(providerId: String, providerName: String): AllReviewsFragment {
            val fragment = AllReviewsFragment()
            val args = Bundle().apply {
                putString(ARG_PROVIDER_ID, providerId)
                putString(ARG_PROVIDER_NAME, providerName)
            }
            fragment.arguments = args
            return fragment
        }
    }
}
