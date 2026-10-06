package com.tqfapp.android.ui.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.tqfapp.android.R
import com.tqfapp.android.databinding.FragmentHomeBinding
import com.tqfapp.android.databinding.ItemCategoryBinding
import com.tqfapp.android.databinding.ItemProfessionalBinding

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupCategories()
        setupProfessionals()
    }

    private fun setupCategories() {
        val categories = listOf(
            CategoryItem("Eletricista", R.drawable.ic_lightning),
            CategoryItem("Encanador", R.drawable.ic_faucet),
            CategoryItem("Jardinagem", R.drawable.ic_leaf),
            CategoryItem("TI", R.drawable.ic_laptop),
            CategoryItem("Limpeza", R.drawable.ic_broom),
            CategoryItem("Pintura", R.drawable.ic_paint_roller)
        )

        binding.rvCategories.apply {
            layoutManager = GridLayoutManager(requireContext(), 3)
            adapter = CategoryAdapter(categories) { category ->
                Toast.makeText(requireContext(), "Categoria: ${category.name}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun setupProfessionals() {
        val professionals = listOf(
            ProfessionalItem(
                name = "Carlos Mendes",
                specialty = "Eletricista Residencial",
                rating = "4,9 (86)",
                location = "2,1 km • Centro",
                price = "R$ 80,00",
                status = "⚡ Atende hoje",
                isVerified = true
            ),
            ProfessionalItem(
                name = "Rafael Lima",
                specialty = "Encanador",
                rating = "4,8 (64)",
                location = "3,4 km • Jardim Paulista",
                price = "R$ 90,00",
                status = "⚡ Atende hoje",
                isVerified = true
            ),
            ProfessionalItem(
                name = "Juliana Alves",
                specialty = "Faxina e Limpeza",
                rating = "4,9 (112)",
                location = "1,8 km • Vila Madalena",
                price = "R$ 70,00",
                status = "⚡ Atende hoje",
                isVerified = true
            )
        )

        binding.rvProfessionals.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = ProfessionalAdapter(professionals) { professional ->
                parentFragmentManager.beginTransaction()
                    .replace(R.id.fragmentContainer, ProfessionalProfileFragment.newInstance(isOwner = false))
                    .addToBackStack(null)
                    .commit()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    // Models for UI presentation
    data class CategoryItem(val name: String, val iconRes: Int)

    data class ProfessionalItem(
        val name: String,
        val specialty: String,
        val rating: String,
        val location: String,
        val price: String,
        val status: String,
        val isVerified: Boolean
    )

    // Category Adapter
    private inner class CategoryAdapter(
        private val items: List<CategoryItem>,
        private val onItemClick: (CategoryItem) -> Unit
    ) : RecyclerView.Adapter<CategoryAdapter.ViewHolder>() {

        inner class ViewHolder(val binding: ItemCategoryBinding) : RecyclerView.ViewHolder(binding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val binding = ItemCategoryBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return ViewHolder(binding)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = items[position]
            holder.binding.txtCategoryName.text = item.name
            holder.binding.imgCategoryIcon.setImageResource(item.iconRes)
            holder.binding.root.setOnClickListener { onItemClick(item) }
        }

        override fun getItemCount(): Int = items.size
    }

    // Professional Adapter
    private inner class ProfessionalAdapter(
        private val items: List<ProfessionalItem>,
        private val onItemClick: (ProfessionalItem) -> Unit
    ) : RecyclerView.Adapter<ProfessionalAdapter.ViewHolder>() {

        inner class ViewHolder(val binding: ItemProfessionalBinding) : RecyclerView.ViewHolder(binding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val binding = ItemProfessionalBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return ViewHolder(binding)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = items[position]
            holder.binding.apply {
                txtName.text = item.name
                txtSpecialty.text = item.specialty
                txtRating.text = item.rating
                txtLocation.text = item.location
                txtPrice.text = item.price
                txtStatusBadge.text = item.status
                imgVerified.visibility = if (item.isVerified) View.VISIBLE else View.GONE
                btnViewProfile.setOnClickListener { onItemClick(item) }
                root.setOnClickListener { onItemClick(item) }
            }
        }

        override fun getItemCount(): Int = items.size
    }
}