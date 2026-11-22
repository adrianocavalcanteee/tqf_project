package com.tqfapp.android.ui.home

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.tqfapp.android.R
import com.tqfapp.android.data.model.Service
import com.tqfapp.android.data.model.resumoParaPrestador

/** Card de solicitação no feed do prestador (Home e "Ver todos"). */
class ServiceRequestAdapter(
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
