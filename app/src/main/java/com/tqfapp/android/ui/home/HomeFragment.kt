package com.tqfapp.android.ui.home

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CircleOptions
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.material.chip.Chip
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.tqfapp.android.R
import com.tqfapp.android.data.model.Service
import com.tqfapp.android.data.model.User
import com.tqfapp.android.data.repository.ServiceRepository
import com.tqfapp.android.data.repository.UserRepository
import com.tqfapp.android.databinding.FragmentHomeBinding
import com.tqfapp.android.databinding.ItemCategoryBinding
import com.tqfapp.android.ui.notifications.NotificationsFragment
import com.tqfapp.android.ui.request.DescricaoServicoActivity
import com.tqfapp.android.ui.request.ProviderProfileActivity
import com.tqfapp.android.ui.request.SugestaoPrestadoresActivity
import com.tqfapp.android.utils.LocationUtils
import com.tqfapp.android.utils.MapUtils
import com.tqfapp.android.utils.UserSessionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.cos
import com.tqfapp.android.ui.common.showSkeleton
import com.tqfapp.android.ui.common.loadImage
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.google.android.gms.tasks.Tasks
import java.util.concurrent.TimeUnit

/**
 * Home do Contratante: mapa com PINs + lista de prestadores, ambos alimentados pelo backend.
 * Nenhum prestador é fixo no código: a lista começa vazia e é preenchida via [ProfessionalAdapter.updateData].
 */
class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private var googleMap: GoogleMap? = null
    private var clientLatLng: LatLng? = null
    private var currentUser: User? = null

    private lateinit var professionalAdapter: ProfessionalAdapter
    private var categoryAdapter: CategoryAdapter? = null

    /** Prestadores carregados do backend (fonte única para mapa e lista). */
    private var providers: List<User> = emptyList()

    // Filtros selecionados
    private var selectedCategoryFilter: String? = null
    private var selectedSubcategoryFilter: String? = null

    /** Raio de busca em km (null = todos). Só vale com localização real do contratante. */
    private var selectedRadiusKm: Double? = null
    /** false quando o contratante não tem endereço/coordenadas: a distância seria até um ponto padrão. */
    private var hasRealClientLocation = false
    /** true quando as distâncias são medidas pelo GPS do aparelho (e não pelo endereço do perfil). */
    private var usingDeviceLocation = false

    companion object {
        // Coordenadas padrão centralizadas em Manaus, Amazonas
        private val MANAUS_LAT_LNG = LatLng(-3.1190, -60.0217)
        private const val DEFAULT_ZOOM = 12f

        /** Opções do filtro de raio (km); null = "Todos". */
        private val RADIUS_OPTIONS_KM = listOf(2.0, 5.0, 10.0, 25.0, null)
        private const val DEFAULT_RADIUS_KM = 10.0
        private const val PREFS_HOME = "tqf_home_prefs"
        private const val KEY_RADIUS_KM = "radius_km"
        private const val GPS_TIMEOUT_SECONDS = 4L
    }

    // Launcher para solicitação de permissões de localização (GPS)
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
        val coarseGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] ?: false

        if (fineGranted || coarseGranted) {
            enableMyLocationLayer()
            // Permissão recém-concedida: recalcula as distâncias a partir do GPS.
            clientLatLng = null
            loadProviders()
        } else {
            Toast.makeText(
                requireContext(),
                "Permissão de GPS negada. Exibindo apenas o endereço cadastrado.",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

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

        setupProfessionalsList()
        setupCategories()
        setupMapFragment()
        setupMapTouchHandling()
        setupNotificationAndActions()
        setupOpenRequestButton()
        setupRadiusChips()
        loadProviders()
    }

    /**
     * Chips de raio de busca (2/5/10/25 km/Todos). A escolha fica salva neste aparelho; sem localização
     * real do contratante só "Todos" fica habilitado (ver [updateRadiusAvailability]).
     */
    private fun setupRadiusChips() {
        val prefs = requireContext().getSharedPreferences(PREFS_HOME, Context.MODE_PRIVATE)
        val saved = prefs.getFloat(KEY_RADIUS_KM, DEFAULT_RADIUS_KM.toFloat())
        selectedRadiusKm = if (saved <= 0f) null else saved.toDouble()

        binding.chipGroupRadius.removeAllViews()
        RADIUS_OPTIONS_KM.forEach { radius ->
            val chip = Chip(requireContext()).apply {
                id = View.generateViewId()
                text = radius?.let { "${it.toInt()} km" } ?: "Todos"
                tag = radius ?: 0.0
                isCheckable = true
                isChecked = radius == selectedRadiusKm
            }
            binding.chipGroupRadius.addView(chip)
        }
        binding.chipGroupRadius.setOnCheckedStateChangeListener { group, checkedIds ->
            val chip = checkedIds.firstOrNull()?.let { group.findViewById<Chip>(it) } ?: return@setOnCheckedStateChangeListener
            val radius = (chip.tag as Double).takeIf { it > 0 }
            selectedRadiusKm = radius
            prefs.edit().putFloat(KEY_RADIUS_KM, (radius ?: 0.0).toFloat()).apply()
            applyFilters()
            zoomToRadius()
        }
    }

    /** Sem endereço do contratante não há como medir distância: força "Todos" e explica no aviso. */
    private fun updateRadiusAvailability() {
        if (_binding == null) return
        binding.txtRadiusHint.visibility = if (hasRealClientLocation) View.GONE else View.VISIBLE
        for (i in 0 until binding.chipGroupRadius.childCount) {
            val chip = binding.chipGroupRadius.getChildAt(i) as Chip
            val isAll = (chip.tag as Double) <= 0
            chip.isEnabled = hasRealClientLocation || isAll
            if (!hasRealClientLocation && isAll) chip.isChecked = true
        }
    }

    /** Enquadra o círculo do raio escolhido (ou volta ao zoom padrão em "Todos"). */
    private fun zoomToRadius() {
        val map = googleMap ?: return
        val center = clientLatLng ?: return
        val radiusKm = selectedRadiusKm.takeIf { hasRealClientLocation }
        if (radiusKm == null) {
            map.animateCamera(CameraUpdateFactory.newLatLngZoom(center, DEFAULT_ZOOM))
            return
        }
        val latDelta = radiusKm / 111.0
        val lngDelta = radiusKm / (111.0 * cos(Math.toRadians(center.latitude)))
        val bounds = LatLngBounds(
            LatLng(center.latitude - latDelta, center.longitude - lngDelta),
            LatLng(center.latitude + latDelta, center.longitude + lngDelta)
        )
        try {
            map.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, 48))
        } catch (_: IllegalStateException) {
            // Mapa ainda sem tamanho medido: aplica quando ele terminar de carregar.
            map.setOnMapLoadedCallback { map.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, 48)) }
        }
    }

    /** "+": pedido aberto para todos os prestadores da categoria (pré-seleciona o filtro atual). */
    private fun setupOpenRequestButton() {
        val isClient = !UserSessionManager.isProviderUser(requireContext())
        binding.fabOpenRequest.visibility = if (isClient) View.VISIBLE else View.GONE
        binding.imgHomeAvatar.setOnClickListener { (activity as? HomeActivity)?.showProfileTab() }
        binding.fabOpenRequest.setOnClickListener {
            startActivity(DescricaoServicoActivity.newOpenRequestIntent(requireContext(), selectedCategoryFilter))
        }
    }

    override fun onResume() {
        super.onResume()
        checkRefusedRequests()
    }

    private fun setupProfessionalsList() {
        professionalAdapter = ProfessionalAdapter { provider -> openProviderProfile(provider) }
        binding.rvProfessionals.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = professionalAdapter
        }
    }

    /**
     * Busca prestadores reais (Supabase com cache Room) e calcula a distância até o cliente.
     */
    private fun loadProviders() {
        binding.progressProfessionals.showSkeleton(true)
        val appContext = requireContext().applicationContext
        viewLifecycleOwner.lifecycleScope.launch {
            val loaded = withContext(Dispatchers.IO) {
                try {
                    val userRepository = UserRepository(appContext)
                    currentUser = userRepository.getCurrentUser()
                    val origin = resolveClientLocation(appContext, currentUser)
                    userRepository.getProviders().map { provider ->
                        val lat = provider.latitude
                        val lng = provider.longitude
                        if (lat != null && lng != null) {
                            provider.copy(distanceKm = LocationUtils.distanceKm(origin, LatLng(lat, lng)))
                        } else {
                            provider
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    emptyList()
                }
            }

            if (_binding == null) return@launch
            binding.progressProfessionals.showSkeleton(false)
            bindHeader()
            providers = loaded
            updateRadiusAvailability()
            applyFilters()
            zoomToRadius()
        }
    }

    /** Cabeçalho: foto e nome do contratante e bairro/cidade do endereço cadastrado. */
    private fun bindHeader() {
        binding.txtHomeGreeting.text = currentUser?.name?.trim()?.takeIf { it.isNotBlank() } ?: "Bem-vindo(a)"
        binding.imgHomeAvatar.loadImage(currentUser?.profilePictureUri)
        binding.txtHomeLocation.text = if (usingDeviceLocation) {
            "Sua localização atual"
        } else {
            currentUser?.address
                ?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() }
                ?.takeLast(2)?.joinToString(", ")
                ?.takeIf { it.isNotBlank() } ?: "Sua região"
        }
    }

    /**
     * Mostra o aviso de recusa (feedback ao cliente) e dá acesso ao motor de recomendação.
     */
    private fun checkRefusedRequests() {
        val appContext = requireContext().applicationContext
        viewLifecycleOwner.lifecycleScope.launch {
            val pending = withContext(Dispatchers.IO) {
                try {
                    val clientId = UserRepository(appContext).getCurrentUser()?.id ?: return@withContext null
                    val seen = UserSessionManager.getSeenRefusedRequestIds(appContext)
                    ServiceRepository(appContext).getRefusedClientRequests(clientId)
                        .firstOrNull { it.id !in seen }
                } catch (_: Exception) {
                    null
                }
            }

            if (_binding == null) return@launch
            showRefusedBanner(pending)
        }
    }

    private fun showRefusedBanner(refused: Service?) {
        if (refused == null) {
            binding.cardRefusedRequest.visibility = View.GONE
            return
        }
        val who = refused.providerName.ifBlank { "O prestador" }
        binding.txtRefusedMessage.text =
            "$who não está disponível para \"${refused.title}\". Veja outros profissionais de ${refused.category}."
        binding.cardRefusedRequest.visibility = View.VISIBLE
        val openSuggestions = View.OnClickListener {
            startActivity(SugestaoPrestadoresActivity.newIntent(requireContext(), refused))
        }
        binding.cardRefusedRequest.setOnClickListener(openSuggestions)
        binding.btnSeeSuggestions.setOnClickListener(openSuggestions)
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
            }.also { categoryAdapter = it }
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

                // Clique no PIN abre o Perfil do Prestador
                setOnMarkerClickListener { marker ->
                    val provider = marker.tag as? User
                    if (provider != null) {
                        openProviderProfile(provider)
                    } else {
                        marker.showInfoWindow()
                    }
                    false
                }

                setOnInfoWindowClickListener { marker ->
                    (marker.tag as? User)?.let { openProviderProfile(it) }
                }
            }

            // Habilita a camada nativa da bolinha azul de localização no Google Maps (GPS)
            enableMyLocationLayer()

            // Centraliza o mapa inicialmente em Manaus - AM
            centerMapOnManaus()

            // Plota os marcadores já carregados (se houver) e enquadra o raio escolhido
            applyFilters()
            zoomToRadius()
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

    /** Posição atual pelo GPS (última conhecida ou uma leitura rápida). Bloqueante: chamar em IO. */
    @SuppressLint("MissingPermission")
    private fun deviceLocation(context: android.content.Context): LatLng? {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!granted) return null
        return try {
            val client = LocationServices.getFusedLocationProviderClient(context)
            val location = Tasks.await(client.lastLocation, GPS_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                ?: Tasks.await(
                    client.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, CancellationTokenSource().token),
                    GPS_TIMEOUT_SECONDS,
                    TimeUnit.SECONDS
                )
            location?.let { LatLng(it.latitude, it.longitude) }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Coordenadas do cliente (coordenadas do cadastro > endereço cadastrado > GPS > centro de Manaus).
     * Usa Geocoder/GPS, portanto deve rodar fora da main thread.
     */
    private fun resolveClientLocation(context: android.content.Context, user: User?): LatLng {
        clientLatLng?.let { return it }

        // 1º coordenadas salvas no cadastro; 2º endereço do perfil; 3º GPS do aparelho (se houver permissão).
        var coords: LatLng? = if (user?.latitude != null && user.longitude != null) {
            LatLng(user.latitude, user.longitude)
        } else null
        val addressText = user?.address
        if (coords == null && !addressText.isNullOrBlank()) {
            coords = LocationUtils.getCoordinatesFromAddress(context, addressText)
        }
        usingDeviceLocation = false
        if (coords == null) {
            coords = deviceLocation(context)
            usingDeviceLocation = coords != null
        }

        val finalCoords = coords ?: MANAUS_LAT_LNG
        hasRealClientLocation = coords != null
        clientLatLng = finalCoords
        return finalCoords
    }

    /**
     * Plota a Bolinha Azul e o círculo de alcance da localização do Cliente no Mapa
     */
    private fun setupClientLocationMarker() {
        val map = googleMap ?: return
        val clientPos = clientLatLng ?: return

        val customDotIcon = MapUtils.getBitmapDescriptorFromVector(
            requireContext(),
            R.drawable.ic_user_location_dot
        )

        val markerOptions = MarkerOptions()
            .position(clientPos)
            .title("📍 ${currentUser?.name ?: "Sua localização"}")
            .anchor(0.5f, 0.5f)
        currentUser?.address?.let { markerOptions.snippet("Endereço: $it") }

        if (customDotIcon != null) {
            markerOptions.icon(customDotIcon)
        } else {
            markerOptions.icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE))
        }

        map.addMarker(markerOptions)

        val radiusKm = activeRadiusKm() ?: return
        map.addCircle(
            CircleOptions()
                .center(clientPos)
                .radius(radiusKm * 1000)
                .strokeWidth(2f)
                .strokeColor(ContextCompat.getColor(requireContext(), R.color.tqf_primary))
                .fillColor(ContextCompat.getColor(requireContext(), R.color.tqf_map_radius_fill))
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
                    binding.scrollHome.requestDisallowInterceptTouchEvent(true)
                    false
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    binding.scrollHome.requestDisallowInterceptTouchEvent(false)
                    false
                }
                else -> false
            }
        }
    }

    private fun centerMapOnManaus() {
        googleMap?.animateCamera(CameraUpdateFactory.newLatLngZoom(clientLatLng ?: MANAUS_LAT_LNG, DEFAULT_ZOOM))
    }

    /**
     * Fluxo do cliente: Home (Mapa/Lista) ➔ Perfil do Prestador
     */
    private fun openProviderProfile(provider: User) {
        startActivity(ProviderProfileActivity.newIntent(requireContext(), provider))
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
            // "Todos" no raio também: o listener dos chips reaplica os filtros e o zoom.
            (0 until binding.chipGroupRadius.childCount)
                .map { binding.chipGroupRadius.getChildAt(it) as Chip }
                .firstOrNull { (it.tag as Double) <= 0 }
                ?.isChecked = true
            centerMapOnManaus()
            applyFilters()
        }
    }

    /**
     * Aplica os filtros e atualiza mapa e lista a partir da mesma fonte de dados.
     */
    private fun applyFilters() {
        if (_binding == null) return
        val filteredList = providers
            // Prestador que desligou "Disponível para chamados" não aparece para novos contratantes.
            .filter { it.isAvailable }
            // Raio: só quem tem localização e está dentro da distância escolhida.
            .filter { provider ->
                val radius = activeRadiusKm() ?: return@filter true
                provider.distanceKm?.let { it <= radius } ?: false
            }
            .filter { provider ->
                val matchesCategory = selectedCategoryFilter == null ||
                        provider.category.equals(selectedCategoryFilter, ignoreCase = true)
                val matchesSubcategory = selectedSubcategoryFilter == null ||
                        provider.specialty?.contains(selectedSubcategoryFilter!!, ignoreCase = true) == true
                matchesCategory && matchesSubcategory
            }
            .sortedWith(compareBy<User> { it.distanceKm ?: Double.MAX_VALUE }.thenByDescending { it.rating })

        professionalAdapter.updateData(filteredList)
        categoryAdapter?.setSelected(selectedCategoryFilter)
        updateListState(filteredList.size)
        updateMapMarkers(filteredList)
    }

    private fun activeRadiusKm(): Double? = selectedRadiusKm.takeIf { hasRealClientLocation }

    private fun updateListState(count: Int) {
        val isLoading = binding.progressProfessionals.visibility == View.VISIBLE
        binding.txtEmptyProfessionals.visibility = if (count == 0 && !isLoading) View.VISIBLE else View.GONE
        binding.txtProfessionalsCount.text = when {
            isLoading -> ""
            count == 1 -> "1 profissional encontrado"
            else -> "$count profissionais encontrados"
        }
        val filterLabel = selectedSubcategoryFilter ?: selectedCategoryFilter
        if (filterLabel != null && !isLoading) {
            binding.txtProfessionalsCount.append(" • $filterLabel")
        }
        activeRadiusKm()?.takeIf { !isLoading }?.let {
            binding.txtProfessionalsCount.append(" • até ${it.toInt()} km")
        }
        binding.txtEmptyProfessionals.text = if (activeRadiusKm() != null) {
            "Nenhum profissional nesse raio. Aumente a distância acima."
        } else {
            "Nenhum prestador encontrado com estes filtros."
        }
    }

    /**
     * Atualiza os PINs no Google Maps (cliente + prestadores filtrados com coordenadas conhecidas)
     */
    private fun updateMapMarkers(filtered: List<User>) {
        val map = googleMap ?: return
        map.clear()

        setupClientLocationMarker()

        for (provider in filtered) {
            val lat = provider.latitude ?: continue
            val lng = provider.longitude ?: continue
            val marker = map.addMarker(
                MarkerOptions()
                    .position(LatLng(lat, lng))
                    .title(provider.name)
                    .snippet("${provider.category.orEmpty()} • ${ProfessionalAdapter.metricsLine(provider)}")
                    .icon(BitmapDescriptorFactory.defaultMarker(MapUtils.getCategoryHue(provider.category)))
            )
            marker?.tag = provider
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        googleMap = null
        _binding = null
    }

    data class CategoryItem(
        val name: String,
        val iconRes: Int,
        val subcategories: List<String>
    )

    private inner class CategoryAdapter(
        private val items: List<CategoryItem>,
        private val onItemClick: (CategoryItem) -> Unit
    ) : RecyclerView.Adapter<CategoryAdapter.ViewHolder>() {

        private var selectedName: String? = null

        /** Destaca o chip da categoria filtrada (null = nenhum). */
        fun setSelected(name: String?) {
            if (name == selectedName) return
            selectedName = name
            notifyDataSetChanged()
        }

        inner class ViewHolder(val binding: ItemCategoryBinding) : RecyclerView.ViewHolder(binding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val binding = ItemCategoryBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return ViewHolder(binding)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = items[position]
            holder.binding.txtCategoryName.text = item.name
            holder.binding.imgCategoryIcon.setImageResource(item.iconRes)
            holder.binding.root.isSelected = item.name == selectedName
            holder.binding.root.setOnClickListener { onItemClick(item) }
        }

        override fun getItemCount(): Int = items.size
    }
}
