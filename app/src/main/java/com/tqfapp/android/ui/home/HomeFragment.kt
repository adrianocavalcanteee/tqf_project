package com.tqfapp.android.ui.home

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.tqfapp.android.R
import com.tqfapp.android.databinding.FragmentHomeBinding
import com.tqfapp.android.databinding.ItemCategoryBinding
import com.tqfapp.android.databinding.ItemProfessionalBinding

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    // Full list of professionals
    private val allProfessionals = listOf(
        ProfessionalItem(
            name = "Carlos Mendes",
            specialty = "Eletricista Residencial e Predial",
            category = "Reformas e Reparos",
            subcategory = "Eletricista Residencial e Predial",
            rating = "4,9 (86)",
            ratingValue = 4.9,
            location = "2,1 km • Centro",
            distanceKm = 2.1,
            price = "R$ 80,00",
            status = "⚡ Atende hoje",
            isVerified = true
        ),
        ProfessionalItem(
            name = "Rafael Lima",
            specialty = "Encanador / Bombeiro Hidráulico",
            category = "Reformas e Reparos",
            subcategory = "Encanador / Bombeiro Hidráulico",
            rating = "4,8 (64)",
            ratingValue = 4.8,
            location = "3,4 km • Jardim Paulista",
            distanceKm = 3.4,
            price = "R$ 90,00",
            status = "⚡ Atende hoje",
            isVerified = true
        ),
        ProfessionalItem(
            name = "Juliana Alves",
            specialty = "Diarista e Faxineira",
            category = "Serviços Domésticos",
            subcategory = "Diarista e Faxineira",
            rating = "4,9 (112)",
            ratingValue = 4.9,
            location = "1,8 km • Vila Madalena",
            distanceKm = 1.8,
            price = "R$ 70,00",
            status = "⚡ Atende hoje",
            isVerified = true
        ),
        ProfessionalItem(
            name = "João Ferreira",
            specialty = "Técnico em Manutenção de Notebooks e Computadores",
            category = "Assistência Técnica",
            subcategory = "Técnico em Manutenção de Notebooks e Computadores",
            rating = "5,0 (289)",
            ratingValue = 5.0,
            location = "2,1 km • Centro",
            distanceKm = 2.1,
            price = "R$ 100,00",
            status = "⚡ Atende hoje",
            isVerified = true
        ),
        ProfessionalItem(
            name = "Roberto Silva",
            specialty = "Mecânico Automotivo / Eletricista Auto",
            category = "Autos e Transporte",
            subcategory = "Mecânico Automotivo / Eletricista Auto",
            rating = "4,8 (93)",
            ratingValue = 4.8,
            location = "2,4 km • Pinheiros",
            distanceKm = 2.4,
            price = "R$ 120,00",
            status = "⚡ Atende hoje",
            isVerified = true
        ),
        ProfessionalItem(
            name = "Lucas Prado",
            specialty = "Desenvolvedor de Aplicativos Mobile",
            category = "Design e Tecnologia",
            subcategory = "Desenvolvedor de Aplicativos Mobile",
            rating = "4,9 (45)",
            ratingValue = 4.9,
            location = "1,5 km • Vila Olímpia",
            distanceKm = 1.5,
            price = "R$ 150,00",
            status = "⚡ Atende hoje",
            isVerified = true
        ),
        ProfessionalItem(
            name = "Mariana Costa",
            specialty = "Fotógrafa e Cinegrafista",
            category = "Eventos",
            subcategory = "Fotógrafo e Cinegrafista",
            rating = "5,0 (110)",
            ratingValue = 5.0,
            location = "3,0 km • Moema",
            distanceKm = 3.0,
            price = "R$ 200,00",
            status = "⚡ Atende hoje",
            isVerified = true
        )
    )

    // Filter states
    private var selectedCategoryFilter: String? = null
    private var selectedSubcategoryFilter: String? = null
    private var minRatingFilter: Double = 0.0
    private var maxDistanceFilter: Double = 999.0
    private var currentSearchQuery: String = ""

    private lateinit var professionalAdapter: ProfessionalAdapter

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
        setupSearchAndFilters()
        setupNotificationAndActions()
    }

    private fun setupCategories() {
        val categories = listOf(
            CategoryItem(
                name = "Assistência Técnica",
                iconRes = R.drawable.ic_tech_repair,
                subcategories = listOf(
                    "Técnico em Manutenção de Celulares e Smartphones",
                    "Técnico em Manutenção de Notebooks e Computadores",
                    "Técnico de Manutenção de Eletrodomésticos",
                    "Técnico em Eletrônica e Conserto de TVs"
                )
            ),
            CategoryItem(
                name = "Reformas e Reparos",
                iconRes = R.drawable.ic_reforms,
                subcategories = listOf(
                    "Eletricista Residencial e Predial",
                    "Encanador / Bombeiro Hidráulico",
                    "Pintor e Gesseiro",
                    "Pedreiro e Empreiteiro",
                    "Marceneiro",
                    "Profissional de Pequenos Reparos (\"Marido de Aluguel\")"
                )
            ),
            CategoryItem(
                name = "Serviços Domésticos",
                iconRes = R.drawable.ic_domestic,
                subcategories = listOf(
                    "Diarista e Faxineira",
                    "Passadeira",
                    "Cozinheiro(a) Autônomo(a) / Personal Chef",
                    "Babá e Cuidador(a) de Idosos",
                    "Personal Organizer"
                )
            ),
            CategoryItem(
                name = "Design e Tecnologia",
                iconRes = R.drawable.ic_laptop,
                subcategories = listOf(
                    "Desenvolvedor Web / Programador Front-End e Back-End",
                    "Desenvolvedor de Aplicativos Mobile",
                    "Designer Gráfico e Designer de Identidade Visual",
                    "Designer UI/UX",
                    "Editor de Vídeo e Motion Designer"
                )
            ),
            CategoryItem(
                name = "Eventos",
                iconRes = R.drawable.ic_events,
                subcategories = listOf(
                    "Fotógrafo e Cinegrafista",
                    "DJ e Músico para Eventos",
                    "Garçom e Bartender",
                    "Decorador de Festas e Cerimonialista",
                    "Confeiteiro(a) e Salgadeiro(a)"
                )
            ),
            CategoryItem(
                name = "Aulas e Treinamentos",
                iconRes = R.drawable.ic_education,
                subcategories = listOf(
                    "Professor Particular de Idiomas (Inglês, Espanhol, etc.)",
                    "Professor de Reforço Escolar (Matemática, Física, Química)",
                    "Personal Trainer e Instrução Física",
                    "Professor de Música (Violão, Piano, Canto)"
                )
            ),
            CategoryItem(
                name = "Saúde e Bem-Estar",
                iconRes = R.drawable.ic_health,
                subcategories = listOf(
                    "Maquiador(a) e Cabeleireiro(a) a Domicílio",
                    "Manicure e Pedicure",
                    "Massoterapeuta e Esteticista"
                )
            ),
            CategoryItem(
                name = "Autos e Transporte",
                iconRes = R.drawable.ic_auto_transport,
                subcategories = listOf(
                    "Mecânico Automotivo / Eletricista Auto",
                    "Profissional de Estética Automotiva / Polimento"
                )
            )
        )

        binding.rvCategories.apply {
            layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
            adapter = CategoryAdapter(categories) { category ->
                showSubcategoryDialog(category)
            }
        }
    }

    private fun showSubcategoryDialog(category: CategoryItem) {
        val options = mutableListOf("Ver todos de ${category.name}")
        options.addAll(category.subcategories)

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(category.name)
            .setItems(options.toTypedArray()) { _, which ->
                selectedCategoryFilter = category.name
                selectedSubcategoryFilter = if (which == 0) null else category.subcategories[which - 1]

                val filterLabel = selectedSubcategoryFilter ?: category.name
                Toast.makeText(requireContext(), "Filtrando: $filterLabel", Toast.LENGTH_SHORT).show()
                applyFilters()
            }
            .show()
    }

    private fun setupProfessionals() {
        professionalAdapter = ProfessionalAdapter(allProfessionals) { professional ->
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragmentContainer, ProfessionalProfileFragment.newInstance(isOwner = false))
                .addToBackStack(null)
                .commit()
        }

        binding.rvProfessionals.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = professionalAdapter
        }
    }

    private fun setupSearchAndFilters() {
        // Live Search Input Filter
        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                currentSearchQuery = s?.toString()?.trim() ?: ""
                applyFilters()
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        // Filter Chip 1: Categoria
        binding.chipFilterCategory.setOnClickListener {
            showCategoryFilterDialog()
        }

        // Filter Chip 2: Avaliação
        binding.chipFilterRating.setOnClickListener {
            showRatingFilterDialog()
        }

        // Filter Chip 3: Proximidade
        binding.chipFilterLocation.setOnClickListener {
            showDistanceFilterDialog()
        }

        // Filter Icon in Search Bar
        binding.btnFilter.setOnClickListener {
            showCategoryFilterDialog()
        }
    }

    private fun setupNotificationAndActions() {
        binding.imgNotification.setOnClickListener {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("Notificações")
                .setMessage("Você tem 2 novas mensagens de orçamentos pendentes.")
                .setPositiveButton("Entendido", null)
                .show()
        }

        binding.btnSeeAll.setOnClickListener {
            selectedCategoryFilter = null
            selectedSubcategoryFilter = null
            minRatingFilter = 0.0
            maxDistanceFilter = 999.0
            currentSearchQuery = ""
            binding.etSearch.setText("")
            applyFilters()
            Toast.makeText(requireContext(), "Exibindo todos os profissionais", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showCategoryFilterDialog() {
        val categories = arrayOf(
            "Todas as Categorias",
            "Assistência Técnica",
            "Reformas e Reparos",
            "Serviços Domésticos",
            "Design e Tecnologia",
            "Eventos",
            "Aulas e Treinamentos",
            "Saúde e Bem-Estar",
            "Autos e Transporte"
        )
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Filtrar por Categoria")
            .setItems(categories) { _, which ->
                selectedCategoryFilter = if (which == 0) null else categories[which]
                selectedSubcategoryFilter = null
                applyFilters()
            }
            .show()
    }

    private fun showRatingFilterDialog() {
        val ratings = arrayOf("Todas as notas", "⭐ 4.9 ou superior", "⭐ 4.8 ou superior", "⭐ 4.5 ou superior")
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Filtrar por Avaliação")
            .setItems(ratings) { _, which ->
                minRatingFilter = when (which) {
                    1 -> 4.9
                    2 -> 4.8
                    3 -> 4.5
                    else -> 0.0
                }
                applyFilters()
            }
            .show()
    }

    private fun showDistanceFilterDialog() {
        val distances = arrayOf("Todas as distâncias", "Até 2.0 km", "Até 3.0 km", "Até 5.0 km")
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Filtrar por Proximidade")
            .setItems(distances) { _, which ->
                maxDistanceFilter = when (which) {
                    1 -> 2.0
                    2 -> 3.0
                    3 -> 5.0
                    else -> 999.0
                }
                applyFilters()
            }
            .show()
    }

    private fun applyFilters() {
        val filteredList = allProfessionals.filter { item ->
            val matchesCategory = selectedCategoryFilter == null || item.category.equals(selectedCategoryFilter, ignoreCase = true)
            val matchesSubcategory = selectedSubcategoryFilter == null || item.subcategory.contains(selectedSubcategoryFilter!!, ignoreCase = true)
            val matchesRating = item.ratingValue >= minRatingFilter
            val matchesDistance = item.distanceKm <= maxDistanceFilter
            val matchesSearch = currentSearchQuery.isEmpty() ||
                    item.name.contains(currentSearchQuery, ignoreCase = true) ||
                    item.specialty.contains(currentSearchQuery, ignoreCase = true) ||
                    item.category.contains(currentSearchQuery, ignoreCase = true) ||
                    item.subcategory.contains(currentSearchQuery, ignoreCase = true)

            matchesCategory && matchesSubcategory && matchesRating && matchesDistance && matchesSearch
        }

        professionalAdapter.updateList(filteredList)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    // Models for UI presentation
    data class CategoryItem(
        val name: String,
        val iconRes: Int,
        val subcategories: List<String>
    )

    data class ProfessionalItem(
        val name: String,
        val specialty: String,
        val category: String,
        val subcategory: String,
        val rating: String,
        val ratingValue: Double,
        val location: String,
        val distanceKm: Double,
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
        private var items: List<ProfessionalItem>,
        private val onItemClick: (ProfessionalItem) -> Unit
    ) : RecyclerView.Adapter<ProfessionalAdapter.ViewHolder>() {

        fun updateList(newList: List<ProfessionalItem>) {
            items = newList
            notifyDataSetChanged()
        }

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