package com.tqfapp.android.ui.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.tqfapp.android.R
import com.tqfapp.android.business.RatingFormat
import com.tqfapp.android.data.repository.ReviewRepository
import com.tqfapp.android.ui.common.ReviewListAdapter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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

        // Leitura em IO: getReviewsByProvider consulta o Supabase.
        val appContext = requireContext().applicationContext
        viewLifecycleOwner.lifecycleScope.launch {
            val reviews = withContext(Dispatchers.IO) {
                if (providerId.isNotBlank()) ReviewRepository(appContext).getReviewsByProvider(providerId) else emptyList()
            }
            if (getView() == null) return@launch

            val average = RatingFormat.average(reviews)
            if (average == null) {
                txtHeaderSubtitle.text = "Nenhuma avaliação encontrada para $providerName"
                rvAllReviews.visibility = View.GONE
                layoutEmptyState.visibility = View.VISIBLE
            } else {
                txtHeaderSubtitle.text =
                    "${RatingFormat.value(average)} ★ • ${RatingFormat.count(reviews.size)} para $providerName"
                rvAllReviews.visibility = View.VISIBLE
                layoutEmptyState.visibility = View.GONE

                rvAllReviews.layoutManager = LinearLayoutManager(requireContext())
                rvAllReviews.adapter = ReviewListAdapter(reviews)
            }
        }
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
