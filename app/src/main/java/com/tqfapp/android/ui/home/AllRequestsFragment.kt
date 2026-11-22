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
import com.google.android.gms.maps.model.LatLng
import com.google.android.material.button.MaterialButton
import com.tqfapp.android.R
import com.tqfapp.android.data.model.Service
import com.tqfapp.android.data.model.resumoParaPrestador
import com.tqfapp.android.data.repository.ServiceRepository
import com.tqfapp.android.data.repository.UserRepository
import com.tqfapp.android.ui.request.AvaliarSolicitacaoActivity
import com.tqfapp.android.utils.LocationUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AllRequestsFragment : Fragment() {

    private var providerLocation: LatLng? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_all_requests, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val providerId = arguments?.getString(ARG_PROVIDER_ID) ?: ""
        val providerCategory = arguments?.getString(ARG_PROVIDER_CATEGORY)

        val btnBack = view.findViewById<ImageView>(R.id.btnBack)
        val txtHeaderSubtitle = view.findViewById<TextView>(R.id.txtHeaderSubtitle)
        val rvAllRequests = view.findViewById<RecyclerView>(R.id.rvAllRequests)
        val layoutEmptyState = view.findViewById<View>(R.id.layoutEmptyState)

        btnBack.setOnClickListener {
            parentFragmentManager.popBackStack()
        }

        viewLifecycleOwner.lifecycleScope.launch {
            val (requests, provider) = withContext(Dispatchers.IO) {
                try {
                    val user = UserRepository(requireContext()).getCurrentUser()
                    val lat = user?.latitude
                    val lng = user?.longitude
                    val loc = if (lat != null && lng != null) LatLng(lat, lng) else null
                    val reqs = ServiceRepository(requireContext()).getProviderRequests(providerId, providerCategory)
                    Pair(reqs, loc)
                } catch (e: Exception) {
                    e.printStackTrace()
                    Pair(emptyList(), null)
                }
            }

            if (view == null) return@launch
            providerLocation = provider

            if (requests.isEmpty()) {
                txtHeaderSubtitle.text = "Nenhum pedido aberto no momento"
                rvAllRequests.visibility = View.GONE
                layoutEmptyState.visibility = View.VISIBLE
            } else {
                txtHeaderSubtitle.text = "${requests.size} pedidos abertos na sua região"
                rvAllRequests.visibility = View.VISIBLE
                layoutEmptyState.visibility = View.GONE

                rvAllRequests.layoutManager = LinearLayoutManager(requireContext())
                rvAllRequests.adapter = ServiceRequestAdapter({ request -> distanceTo(request) }) { request ->
                    startActivity(AvaliarSolicitacaoActivity.newIntent(requireContext(), request))
                }.apply {
                    updateData(requests)
                }
            }
        }
    }

    private fun distanceTo(request: Service): Double? {
        val from = providerLocation ?: return null
        val lat = request.latitude ?: return null
        val lng = request.longitude ?: return null
        return LocationUtils.distanceKm(from, LatLng(lat, lng))
    }

    private class ServiceRequestAdapter(
        private val distanceOf: (Service) -> Double?,
        private val onEvaluateClick: (Service) -> Unit
    ) : RecyclerView.Adapter<ServiceRequestAdapter.ViewHolder>() {

        private var items: List<Service> = emptyList()

        fun updateData(newList: List<Service>) {
            items = newList
            notifyDataSetChanged()
        }

        class ViewHolder(val view: View) : RecyclerView.ViewHolder(view) {
            val txtClientName: TextView = view.findViewById(R.id.txtClientName)
            val txtCategoryBadge: TextView = view.findViewById(R.id.txtCategoryBadge)
            val txtPostedTime: TextView = view.findViewById(R.id.txtPostedTime)
            val txtRequestTitle: TextView = view.findViewById(R.id.txtRequestTitle)
            val txtRequestDescription: TextView = view.findViewById(R.id.txtRequestDescription)
            val txtLocation: TextView = view.findViewById(R.id.txtLocation)
            val btnEvaluateService: MaterialButton = view.findViewById(R.id.btnEvaluateService)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_service_request, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = items[position]
            holder.txtClientName.text = item.clientName.ifBlank { "Cliente" }
            holder.txtCategoryBadge.text = item.category
            holder.txtPostedTime.text = item.date
            holder.txtRequestTitle.text = item.title
            holder.txtRequestDescription.text = item.description
            holder.txtLocation.text = item.resumoParaPrestador(distanceOf(item))

            holder.btnEvaluateService.setOnClickListener { onEvaluateClick(item) }
            holder.view.setOnClickListener { onEvaluateClick(item) }
        }

        override fun getItemCount(): Int = items.size
    }

    companion object {
        private const val ARG_PROVIDER_ID = "arg_provider_id"
        private const val ARG_PROVIDER_CATEGORY = "arg_provider_category"

        fun newInstance(providerId: String, providerCategory: String?): AllRequestsFragment {
            val fragment = AllRequestsFragment()
            val args = Bundle().apply {
                putString(ARG_PROVIDER_ID, providerId)
                putString(ARG_PROVIDER_CATEGORY, providerCategory)
            }
            fragment.arguments = args
            return fragment
        }
    }
}
