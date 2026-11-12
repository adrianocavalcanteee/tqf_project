package com.tqfapp.android.ui.home

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CircleOptions
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.tqfapp.android.R
import com.tqfapp.android.business.ServiceStatus
import com.tqfapp.android.data.local.database.AppDatabase
import com.tqfapp.android.data.model.Service
import com.tqfapp.android.data.repository.UserRepository
import com.tqfapp.android.databinding.FragmentHomeBinding
import com.tqfapp.android.databinding.ItemCategoryBinding
import com.tqfapp.android.ui.notifications.NotificationsFragment
import com.tqfapp.android.utils.LocationUtils
import com.tqfapp.android.utils.MapUtils
import kotlinx.coroutines.runBlocking

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private var googleMap: GoogleMap? = null
    private var clientLatLng: LatLng? = null

    companion object {
        // Coordenadas padrão centralizadas em Manaus, Amazonas
        private val MANAUS_LAT_LNG = LatLng(-3.1190, -60.0217)
        private const val DEFAULT_ZOOM = 12f
    }

    // Launcher para solicitação de permissões de localização (GPS)
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
        val coarseGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] ?: false

        if (fineGranted || coarseGranted) {
            enableMyLocationLayer()
        } else {
            Toast.makeText(
                requireContext(),
                "Permissão de GPS negada. Exibindo apenas o endereço cadastrado.",
                Toast.LENGTH_SHORT
            ).show()
        }
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
                    Service("s1", "Eletricista Residencial e Predial", "Instalação e reparos elétricos", category = "Reformas e Reparos", price = 80.0, status = ServiceStatus.EM_ANDAMENTO),
                    Service("s2", "Encanador / Bombeiro Hidráulico", "Reparos de vazamentos e tubulação", category = "Reformas e Reparos", price = 90.0, status = ServiceStatus.CONCLUIDO),
                    Service("s3", "Diarista e Faxineira", "Limpeza residencial completa", category = "Serviços Domésticos", price = 70.0, status = ServiceStatus.PENDENTE),
                    Service("s4", "Técnico em Manutenção de Notebooks", "Conserto de computadores", category = "Assistência Técnica", price = 100.0, status = ServiceStatus.EM_ANDAMENTO),
                    Service("s5", "Mecânico Automotivo / Eletricista Auto", "Revisão e manutenção veicular", category = "Autos e Transporte", price = 120.0, status = ServiceStatus.PENDENTE)
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

            // Aplica o estilo visual personalizado do JSON ao Google Maps
            MapUtils.applyCustomMapStyle(requireContext(), map)

            map.apply {
                uiSettings.isZoomControlsEnabled = true
                uiSettings.isCompassEnabled = true
                uiSettings.isMapToolbarEnabled = true

                // Clique no Marcador abre o Perfil do Prestador
                setOnMarkerClickListener { marker ->
                    val professional = marker.tag as? ProfessionalItem
                    if (professional != null) {
                        openProfessionalProfile(professional)
                    } else {
                        marker.showInfoWindow()
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

            // Habilita a camada nativa da bolinha azul de localização no Google Maps (GPS)
            enableMyLocationLayer()

            // Centraliza o mapa inicialmente em Manaus - AM
            centerMapOnManaus()

            // Carrega e exibe os marcadores de acordo com os filtros atuais
            applyFilters()
        }
    }

    /**
     * Habilita a camada da Bolinha Azul de Localização nativa do Google Maps se a permissão for concedida.
     * Caso contrário, solicita as permissões necessárias.
     */
    @SuppressLint("MissingPermission")
    private fun enableMyLocationLayer() {
        val map = googleMap ?: return
        if (hasLocationPermission()) {
            try {
                map.isMyLocationEnabled = true
                map.uiSettings.isMyLocationButtonEnabled = true
            } catch (e: SecurityException) {
                e.printStackTrace()
            }
        } else {
            requestPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    /**
     * Verifica se o aplicativo possui permissão de GPS/Localização
     */
    private fun hasLocationPermission(): Boolean {
        val ctx = context ?: return false
        return ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Obtém ou inicializa as coordenadas do cliente (usando o endereço cadastrado ou fallback de Manaus)
     */
    private fun getOrInitClientLocation(): LatLng {
        clientLatLng?.let { return it }

        val userRepository = UserRepository(requireContext())
        val currentUser = userRepository.getCurrentUser()

        var coords: LatLng? = null
        val addressText = currentUser?.address
        if (!addressText.isNullOrBlank()) {
            coords = LocationUtils.getCoordinatesFromAddress(requireContext(), addressText)
        }

        if (coords == null && currentUser?.latitude != null && currentUser.longitude != null) {
            coords = LatLng(currentUser.latitude, currentUser.longitude)
        }

        // Coordenada de exemplo padrão do cliente em Manaus - AM (Av. Djalma Batista, Chapada)
        val finalCoords = coords ?: LatLng(-3.1019, -60.0265)
        clientLatLng = finalCoords
        return finalCoords
    }

    /**
     * Plota o Marcador Azul e o Círculo de alcance da localização do Cliente no Mapa
     */
    private fun setupClientLocationMarker() {
        val map = googleMap ?: return
        val currentUser = try { UserRepository(requireContext()).getCurrentUser() } catch (_: Exception) { null }
        val clientPos = getOrInitClientLocation()

        val clientName = currentUser?.name ?: "Cliente (Você)"
        val clientAddress = currentUser?.address ?: "Av. Djalma Batista, 1661 - Chapada, Manaus - AM"

        // Marcador Azul (Azure Pin) da Localização Cadastrada do Cliente
        map.addMarker(
            MarkerOptions()
                .position(clientPos)
                .title("📍 $clientName")
                .snippet("Endereço: $clientAddress")
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE))
        )

        // Círculo Translúcido Azul indicando o raio da área do cliente
        map.addCircle(
            CircleOptions()
                .center(clientPos)
                .radius(600.0) // 600m de raio
                .strokeWidth(2f)
                .strokeColor(Color.parseColor("#007AFF"))
                .fillColor(Color.parseColor("#22007AFF"))
        )
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
     * Filtra a lista de prestadores e atualiza os marcadores no mapa dinamicamente
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
     * Atualiza os marcadores no Google Maps (Plota a localização do Cliente e dos Prestadores Filtrados)
     */
    private fun updateMapMarkers(professionals: List<ProfessionalItem>) {
        val map = googleMap ?: return
        map.clear()

        // 1. Desenha a localização cadastrada do cliente contratante (Pin Azul + Círculo)
        setupClientLocationMarker()

        if (professionals.isEmpty()) {
            Toast.makeText(requireContext(), "Nenhum prestador encontrado com estes filtros", Toast.LENGTH_SHORT).show()
            return
        }

        // 2. Desenha os marcadores dos prestadores de serviço com cores dinâmicas por categoria
        for (professional in professionals) {
            val position = LatLng(professional.latitude, professional.longitude)
            val markerHue = MapUtils.getCategoryHue(professional.category)

            val marker = map.addMarker(
                MarkerOptions()
                    .position(position)
                    .title(professional.name)
                    .snippet("${professional.specialty} • ${professional.rating}")
                    .icon(BitmapDescriptorFactory.defaultMarker(markerHue))
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
