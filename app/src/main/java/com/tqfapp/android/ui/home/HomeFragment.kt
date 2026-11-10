package com.tqfapp.android.ui.home

import android.annotation.SuppressLint
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.tqfapp.android.R
import com.tqfapp.android.business.ServiceStatus
import com.tqfapp.android.data.local.database.AppDatabase
import com.tqfapp.android.data.model.Service
import com.tqfapp.android.databinding.FragmentHomeBinding
import com.tqfapp.android.databinding.ItemCategoryBinding
import com.tqfapp.android.ui.notifications.NotificationsFragment
import kotlinx.coroutines.runBlocking

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private var googleMap: GoogleMap? = null

    companion object {
        // Coordenadas padrão centralizadas em Manaus, Amazonas
        private val MANAUS_LAT_LNG = LatLng(-3.1190, -60.0217)
        private const val DEFAULT_ZOOM = 12f
    }

    // Lista completa de prestadores de serviço de exemplo com coordenadas fixas em Manaus
    private val allProfessionals = listOf(
        ProfessionalItem(
            name = "Carlos Mendes",
            specialty = "Eletricista Residencial e Predial",
            category = "Reformas e Reparos",
            subcategory = "Eletricista Residencial e Predial",
            rating = "4,9 (86 avaliações)",
            ratingValue = 4.9,
            location = "2,1 km • Centro, Manaus",
            distanceKm = 2.1,
            price = "R$ 80,00",
            status = "⚡ Atende hoje",
            isVerified = true,
            bio = "Eletricista residencial e predial com mais de 8 anos de experiência em Manaus. Instalações elétricas, quadros de disjuntores, chuveiros e reparos com total segurança.",
            latitude = -3.1316,
            longitude = -60.0234
        ),
        ProfessionalItem(
            name = "Rafael Lima",
            specialty = "Encanador / Bombeiro Hidráulico",
            category = "Reformas e Reparos",
            subcategory = "Encanador / Bombeiro Hidráulico",
            rating = "4,8 (64 avaliações)",
            ratingValue = 4.8,
            location = "3,4 km • Adrianópolis, Manaus",
            distanceKm = 3.4,
            price = "R$ 90,00",
            status = "⚡ Atende hoje",
            isVerified = true,
            bio = "Bombeiro hidráulico e encanador em Manaus especializado em desentupimentos, detecção de vazamentos, instalação de torneiras e registros com garantia.",
            latitude = -3.1072,
            longitude = -60.0125
        ),
        ProfessionalItem(
            name = "Juliana Alves",
            specialty = "Diarista e Faxineira",
            category = "Serviços Domésticos",
            subcategory = "Diarista e Faxineira",
            rating = "4,9 (112 avaliações)",
            ratingValue = 4.9,
            location = "1,8 km • Ponta Negra, Manaus",
            distanceKm = 1.8,
            price = "R$ 70,00",
            status = "⚡ Atende hoje",
            isVerified = true,
            bio = "Profissional de serviços domésticos e organização residencial em Manaus. Trabalho com pontualidade, higiene e atenção aos mínimos detalhes.",
            latitude = -3.0780,
            longitude = -60.0980
        ),
        ProfessionalItem(
            name = "João Ferreira",
            specialty = "Técnico em Manutenção de Notebooks e Computadores",
            category = "Assistência Técnica",
            subcategory = "Técnico em Manutenção de Notebooks e Computadores",
            rating = "5,0 (289 avaliações)",
            ratingValue = 5.0,
            location = "2,1 km • Vieiralves, Manaus",
            distanceKm = 2.1,
            price = "R$ 100,00",
            status = "⚡ Atende hoje",
            isVerified = true,
            bio = "Técnico de TI com mais de 5 anos de experiência em manutenção de computadores e notebooks no Vieiralves. Trabalho ágil, transparente e com garantia.",
            latitude = -3.1005,
            longitude = -60.0200
        ),
        ProfessionalItem(
            name = "Roberto Silva",
            specialty = "Mecânico Automotivo / Eletricista Auto",
            category = "Autos e Transporte",
            subcategory = "Mecânico Automotivo / Eletricista Auto",
            rating = "4,8 (93 avaliações)",
            ratingValue = 4.8,
            location = "2,4 km • Aleixo, Manaus",
            distanceKm = 2.4,
            price = "R$ 120,00",
            status = "⚡ Atende hoje",
            isVerified = true,
            bio = "Mecânico e eletricista automotivo na região do Aleixo, Manaus. Oficina especializada em injeção eletrônica, freios, suspensão e socorro mecânico.",
            latitude = -3.0950,
            longitude = -59.9910
        ),
        ProfessionalItem(
            name = "Lucas Prado",
            specialty = "Desenvolvedor de Aplicativos Mobile",
            category = "Design e Tecnologia",
            subcategory = "Desenvolvedor de Aplicativos Mobile",
            rating = "4,9 (45 avaliações)",
            ratingValue = 4.9,
            location = "1,5 km • Flores, Manaus",
            distanceKm = 1.5,
            price = "R$ 150,00",
            status = "⚡ Atende hoje",
            isVerified = true,
            bio = "Desenvolvedor de aplicativos Android e iOS nativos e híbridos em Manaus. Criação de sistemas modernos, rápidos e integrados para empresas e autônomos.",
            latitude = -3.0730,
            longitude = -60.0150
        ),
        ProfessionalItem(
            name = "Mariana Costa",
            specialty = "Fotógrafa e Cinegrafista",
            category = "Eventos",
            subcategory = "Fotógrafo e Cinegrafista",
            rating = "5,0 (110 avaliações)",
            ratingValue = 5.0,
            location = "3,0 km • Parque 10, Manaus",
            distanceKm = 3.0,
            price = "R$ 200,00",
            status = "⚡ Atende hoje",
            isVerified = true,
            bio = "Fotógrafa e videomaker profissional no Parque 10 em Manaus. Eventos, ensaios corporativos e de produtos com equipamentos de alta qualidade.",
            latitude = -3.0880,
            longitude = -60.0100
        )
    )

    // Filtros selecionados
    private var selectedCategoryFilter: String? = null
    private var selectedSubcategoryFilter: String? = null
    private var minRatingFilter: Double = 0.0
    private var maxDistanceFilter: Double = 999.0
    private var currentSearchQuery: String = ""

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

        seedServicesInRoom()
        setupCategories()
        setupMapFragment()
        setupMapTouchHandling()
        setupSearchAndFilters()
        setupNotificationAndActions()
    }

    private fun seedServicesInRoom() {
        val serviceDao = AppDatabase.getDatabase(requireContext()).serviceDao()
        runBlocking {
            if (serviceDao.getAll().isEmpty()) {
                val initialServices = listOf(
                    Service("s1", "Eletricista Residencial e Predial", "Instalação e reparos elétricos", 80.0, ServiceStatus.EM_ABERTO),
                    Service("s2", "Encanador / Bombeiro Hidráulico", "Reparos de vazamentos e tubulação", 90.0, ServiceStatus.EM_ABERTO),
                    Service("s3", "Diarista e Faxineira", "Limpeza residencial completa", 70.0, ServiceStatus.EM_ABERTO),
                    Service("s4", "Técnico em Manutenção de Notebooks", "Conserto de computadores", 100.0, ServiceStatus.EM_ABERTO),
                    Service("s5", "Mecânico Automotivo / Eletricista Auto", "Revisão e manutenção veicular", 120.0, ServiceStatus.EM_ABERTO)
                )
                initialServices.forEach { serviceDao.insert(it) }
            }
        }
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

    /**
     * Inicializa o SupportMapFragment e centraliza o mapa em Manaus com os marcadores
     */
    private fun setupMapFragment() {
        val mapFragment = childFragmentManager.findFragmentById(R.id.mapFragment) as? SupportMapFragment
        mapFragment?.getMapAsync { map ->
            googleMap = map
            map.apply {
                uiSettings.isZoomControlsEnabled = true
                uiSettings.isCompassEnabled = true
                uiSettings.isMapToolbarEnabled = true

                // Clique no Marcador abre o Perfil do Prestador
                setOnMarkerClickListener { marker ->
                    val professional = marker.tag as? ProfessionalItem
                    if (professional != null) {
                        openProfessionalProfile(professional)
                    }
                    false
                }

                // Clique na Janela de Informações (InfoWindow) também abre o Perfil
                setOnInfoWindowClickListener { marker ->
                    val professional = marker.tag as? ProfessionalItem
                    if (professional != null) {
                        openProfessionalProfile(professional)
                    }
                }
            }

            // Centraliza o mapa inicialmente em Manaus - AM
            centerMapOnManaus()

            // Carrega e exibe os marcadores de acordo com os filtros atuais
            applyFilters()
        }
    }

    /**
     * Permite gestos suaves no Mapa dentro do NestedScrollView
     */
    @SuppressLint("ClickableViewAccessibility")
    private fun setupMapTouchHandling() {
        binding.mapTouchOverlay.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                    binding.root.requestDisallowInterceptTouchEvent(true)
                    false
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    binding.root.requestDisallowInterceptTouchEvent(false)
                    false
                }
                else -> false
            }
        }
    }

    /**
     * Centraliza a câmera do mapa em Manaus, Amazonas
     */
    private fun centerMapOnManaus() {
        googleMap?.animateCamera(CameraUpdateFactory.newLatLngZoom(MANAUS_LAT_LNG, DEFAULT_ZOOM))
    }

    /**
     * Navega para a tela de Perfil do Prestador selecionado
     */
    private fun openProfessionalProfile(professional: ProfessionalItem) {
        val fragment = ProfessionalProfileFragment.newInstance(
            isOwner = false,
            name = professional.name,
            specialty = professional.specialty,
            rating = professional.rating,
            bio = professional.bio,
            latitude = professional.latitude,
            longitude = professional.longitude
        )

        parentFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, fragment)
            .addToBackStack(null)
            .commit()
    }

    private fun setupSearchAndFilters() {
        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                currentSearchQuery = s?.toString()?.trim() ?: ""
                applyFilters()
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        binding.chipFilterCategory.setOnClickListener { showCategoryFilterDialog() }
        binding.chipFilterRating.setOnClickListener { showRatingFilterDialog() }
        binding.chipFilterLocation.setOnClickListener { showDistanceFilterDialog() }
        binding.btnFilter.setOnClickListener { showCategoryFilterDialog() }
    }

    private fun setupNotificationAndActions() {
        binding.imgNotification.setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragmentContainer, NotificationsFragment())
                .addToBackStack(null)
                .commit()
        }

        binding.btnSeeAll.setOnClickListener {
            selectedCategoryFilter = null
            selectedSubcategoryFilter = null
            minRatingFilter = 0.0
            maxDistanceFilter = 999.0
            currentSearchQuery = ""
            binding.etSearch.setText("")
            centerMapOnManaus()
            applyFilters()
            Toast.makeText(requireContext(), "Exibindo todos os profissionais no mapa", Toast.LENGTH_SHORT).show()
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

    /**
     * Filtra a lista de prestadores e atualiza os marcadores vermelhos no mapa dinamicamente
     */
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

        updateMapMarkers(filteredList)
    }

    /**
     * Atualiza os marcadores vermelhos no Google Maps com base nos prestadores filtrados
     */
    private fun updateMapMarkers(professionals: List<ProfessionalItem>) {
        val map = googleMap ?: return
        map.clear()

        if (professionals.isEmpty()) {
            Toast.makeText(requireContext(), "Nenhum prestador encontrado com estes filtros", Toast.LENGTH_SHORT).show()
            return
        }

        for (professional in professionals) {
            val position = LatLng(professional.latitude, professional.longitude)
            val marker = map.addMarker(
                MarkerOptions()
                    .position(position)
                    .title(professional.name)
                    .snippet("${professional.specialty} • ${professional.rating}")
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED))
            )
            marker?.tag = professional
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        googleMap = null
        _binding = null
    }

    // Models
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
        val isVerified: Boolean,
        val bio: String = "Profissional autônomo com ampla experiência. Atendimento com agilidade, transparência e garantia no serviço.",
        val latitude: Double = -3.1190,
        val longitude: Double = -60.0217
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
}
