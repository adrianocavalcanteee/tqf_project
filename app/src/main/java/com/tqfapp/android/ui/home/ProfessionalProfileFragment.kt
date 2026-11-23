package com.tqfapp.android.ui.home

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.MapView
import com.google.android.gms.maps.MapsInitializer
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.tqfapp.android.ui.common.bindServiceChips
import com.google.android.material.chip.ChipGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.tqfapp.android.R
import com.tqfapp.android.data.model.Review
import com.tqfapp.android.business.ServiceCategories
import com.tqfapp.android.business.RatingFormat
import com.tqfapp.android.data.repository.ReviewRepository
import com.tqfapp.android.data.repository.ServiceRepository
import com.tqfapp.android.data.repository.UserRepository
import com.tqfapp.android.ui.auth.LoginActivity
import com.tqfapp.android.utils.LocationUtils
import com.tqfapp.android.utils.MapUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.tqfapp.android.data.model.User
import com.tqfapp.android.data.repository.StorageRepository
import com.tqfapp.android.ui.common.PortfolioPhotoAdapter
import com.tqfapp.android.ui.common.loadImage

class ProfessionalProfileFragment : Fragment() {

    // Instância do Google Maps e MapView
    private var googleMap: GoogleMap? = null
    private var mapView: MapView? = null
    private var isMapCreated = false
    private var officeLatitude: Double = DEFAULT_LATITUDE
    private var officeLongitude: Double = DEFAULT_LONGITUDE

    // Photo Picker launcher para Foto de Perfil
    private val pickAvatarLauncher = registerForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) uploadAvatar(uri)
    }

    // Photo Picker para a foto de capa
    private val pickCoverLauncher = registerForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) uploadCover(uri)
    }

    /** Capa: mostra na hora, envia em IO (perfis_prestador.cover_url) e volta a anterior se falhar. */
    private fun uploadCover(uri: Uri) {
        val imgCover = view?.findViewById<ImageView>(R.id.imgCover) ?: return
        val previous = UserRepository(requireContext()).getCurrentUser()?.coverUri
        imgCover.setImageURI(uri)
        val appContext = requireContext().applicationContext
        viewLifecycleOwner.lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) { UserRepository(appContext).updateCoverPicture(uri) }
            result.onSuccess { url ->
                showCover(url)
                showToast("Foto de capa atualizada!")
            }.onFailure {
                showCover(previous)
                showToast(it.message ?: "Não foi possível atualizar a capa.")
            }
        }
    }

    /** Exibe a capa (ou o degradê padrão) e ajusta o texto do botão. */
    private fun showCover(coverUri: String?) {
        val root = view ?: return
        val imgCover = root.findViewById<ImageView>(R.id.imgCover) ?: return
        if (coverUri.isNullOrBlank()) imgCover.setImageDrawable(null) else imgCover.loadImage(coverUri, android.R.color.transparent)
        root.findViewById<TextView>(R.id.txtChangeCover)?.text = if (coverUri.isNullOrBlank()) "Adicionar capa" else "Trocar capa"
    }

    /** Foto de perfil: mostra na hora, envia em IO e, se falhar, volta a foto anterior com o erro real. */
    private fun uploadAvatar(uri: Uri) {
        val imgAvatar = view?.findViewById<ImageView>(R.id.imgAvatar) ?: return
        val previous = UserRepository.cachedProfilePicture()
        imgAvatar.setImageURI(uri)
        val appContext = requireContext().applicationContext
        viewLifecycleOwner.lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) { UserRepository(appContext).updateProfilePicture(uri) }
            result.onSuccess { url ->
                imgAvatar.loadImage(url)
                showToast("Foto de perfil atualizada!")
            }.onFailure {
                imgAvatar.loadImage(previous)
                showToast(it.message ?: "Não foi possível atualizar a foto.")
            }
        }
    }

    private val editPortfolioUris = mutableListOf<Uri>()
    private var editPortfolioAdapter: PortfolioPreviewAdapter? = null

    private val editPortfolioLauncher = registerForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(5)
    ) { uris ->
        if (uris.isNotEmpty()) {
            uris.forEach { uri ->
                if (!editPortfolioUris.contains(uri)) {
                    editPortfolioUris.add(uri)
                }
            }
            editPortfolioAdapter?.notifyDataSetChanged()
            Toast.makeText(requireContext(), "${uris.size} foto(s) adicionada(s) ao portfólio", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_professional_profile, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val isOwner = arguments?.getBoolean(ARG_IS_OWNER, false) ?: false
        val userRepository = UserRepository(requireContext())
        val currentUser = if (isOwner) userRepository.getCurrentUser() else null

        // Carrega dados dinâmicos passados via Bundle ou do Usuário logado
        val name = currentUser?.name ?: arguments?.getString(ARG_NAME)
        val specialty = currentUser?.specialty ?: currentUser?.category ?: arguments?.getString(ARG_SPECIALTY)
        // Sem argumento, a nota é calculada a partir das avaliações reais do prestador (setupReviews).
        val rating = arguments?.getString(ARG_RATING)
        val bio = currentUser?.bio ?: arguments?.getString(ARG_BIO) ?: "Você ainda não escreveu uma descrição. Toque em editar para contar sobre seu trabalho."
        val services = currentUser?.services.orEmpty()

        if (!name.isNullOrEmpty()) {
            view.findViewById<TextView>(R.id.txtProfessionalName)?.text = name
        }
        view.findViewById<ImageView>(R.id.imgAvatar)?.loadImage(currentUser?.profilePictureUri)
        showCover(currentUser?.coverUri)
        if (!specialty.isNullOrEmpty()) {
            view.findViewById<TextView>(R.id.txtSpecialty)?.text = specialty
        }
        if (!rating.isNullOrEmpty()) {
            view.findViewById<TextView>(R.id.txtRating)?.text = rating
        }
        if (!bio.isNullOrEmpty()) {
            view.findViewById<TextView>(R.id.txtBio)?.text = bio
        }

        setupDynamicChips(view, services)

        // Carrega as coordenadas específicas do prestador com fallback seguro para São Paulo
        officeLatitude = currentUser?.latitude ?: arguments?.getDouble(ARG_LATITUDE, DEFAULT_LATITUDE) ?: DEFAULT_LATITUDE
        officeLongitude = currentUser?.longitude ?: arguments?.getDouble(ARG_LONGITUDE, DEFAULT_LONGITUDE) ?: DEFAULT_LONGITUDE

        setupProfileMode(view, isOwner)
        setupClickListeners(view, isOwner)
        setupPortfolio(view)
        setupReviews(view)
        setupCompletedServicesCount(view)

        // REGRA: Como este fragmento exibe o perfil de um Prestador, o mapa e o marcador SEMPRE aparecem
        val txtOfficeMapTitle = view.findViewById<View>(R.id.txtOfficeMapTitle)
        val cardMapContainer = view.findViewById<View>(R.id.cardMapContainer)
        mapView = view.findViewById(R.id.mapOffice)

        txtOfficeMapTitle?.visibility = View.VISIBLE
        cardMapContainer?.visibility = View.VISIBLE

        setupOfficeMapView(view, savedInstanceState)
    }

    /**
     * Inicializa o MapView evitando problemas de renderização (tela preta)
     */
    private fun setupOfficeMapView(view: View, savedInstanceState: Bundle?) {
        try {
            MapsInitializer.initialize(requireContext())
            mapView = view.findViewById(R.id.mapOffice)
            mapView?.onCreate(savedInstanceState)
            isMapCreated = true
            mapView?.getMapAsync { map ->
                googleMap = map
                MapUtils.applyCustomMapStyle(requireContext(), map)
                updateOfficeMapMarker(view)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Atualiza o marcador do escritório e o texto do card de endereço de forma sincronizada usando Geocoder dinâmico
     */
    private fun updateOfficeMapMarker(view: View) {
        val map = googleMap ?: return
        val txtName = view.findViewById<TextView>(R.id.txtProfessionalName)?.text?.toString() ?: "Prestador"

        val isOwner = arguments?.getBoolean(ARG_IS_OWNER, false) ?: false
        val currentUser = if (isOwner) try { UserRepository(requireContext()).getCurrentUser() } catch (_: Exception) { null } else null

        // Se for o proprietário logado, consulta o endereço do usuário; senão, utiliza o endereço passado via argumento do prestador clicado
        val passedAddress = arguments?.getString(ARG_ADDRESS)
        val savedAddress = if (isOwner) currentUser?.address ?: passedAddress else passedAddress

        val finalAddressText = when {
            !passedAddress.isNullOrBlank() && !isOwner -> passedAddress
            !savedAddress.isNullOrBlank() -> savedAddress
            else -> "Endereço não informado"
        }
        // Sem endereço salvo: tenta o endereço das coordenadas em IO (Geocoder faz rede).
        if (savedAddress.isNullOrBlank() && passedAddress.isNullOrBlank()) {
            val geoContext = requireContext().applicationContext
            viewLifecycleOwner.lifecycleScope.launch {
                val geocoded = withContext(Dispatchers.IO) {
                    LocationUtils.getAddressFromCoordinates(geoContext, officeLatitude, officeLongitude)
                } ?: return@launch
                getView()?.findViewById<TextView>(R.id.txtOfficeAddressSubtitle)?.text = "📍 $geocoded"
            }
        }

        // Sincroniza o TextView do subtítulo do endereço sobre o mapa
        view.findViewById<TextView>(R.id.txtOfficeAddressSubtitle)?.text = "📍 $finalAddressText"

        MapUtils.setupOfficeLocationOnMap(
            googleMap = map,
            latitude = officeLatitude,
            longitude = officeLongitude,
            title = "Escritório de $txtName",
            snippet = finalAddressText
        )
    }

    private fun setupProfileMode(view: View, isOwner: Boolean) {
        val btnEditProfile = view.findViewById<View>(R.id.btnEditProfile)
        val btnChangeCover = view.findViewById<View>(R.id.btnChangeCover)
        val btnChangeAvatar = view.findViewById<View>(R.id.btnChangeAvatar)
        val layoutActionButtons = view.findViewById<View>(R.id.layoutActionButtons)
        val cardProviderMenu = view.findViewById<View>(R.id.cardProviderMenu)

        if (isOwner) {
            btnEditProfile?.visibility = View.VISIBLE
            btnChangeCover?.visibility = View.VISIBLE
            btnChangeAvatar?.visibility = View.VISIBLE
            cardProviderMenu?.visibility = View.VISIBLE
            layoutActionButtons?.visibility = View.GONE
        } else {
            layoutActionButtons?.visibility = View.VISIBLE
            btnEditProfile?.visibility = View.GONE
            btnChangeCover?.visibility = View.GONE
            btnChangeAvatar?.visibility = View.GONE
            cardProviderMenu?.visibility = View.GONE
        }
    }

    private fun setupClickListeners(view: View, isOwner: Boolean) {
        view.findViewById<View>(R.id.btnBack)?.setOnClickListener {
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }

        // Favoritos ainda não existe no app/banco: o botão fica oculto em vez de simular a ação.
        view.findViewById<View>(R.id.btnFavorite)?.visibility = View.GONE

        // Compartilha um texto com nome, categoria e especialidade pelo seletor do Android.
        view.findViewById<View>(R.id.btnShare)?.setOnClickListener {
            val name = view.findViewById<TextView>(R.id.txtProfessionalName)?.text?.toString().orEmpty()
            val specialty = view.findViewById<TextView>(R.id.txtSpecialty)?.text?.toString().orEmpty()
            val text = listOf(name, specialty).filter { it.isNotBlank() }.joinToString(" • ")
            val share = Intent(Intent.ACTION_SEND)
                .setType("text/plain")
                .putExtra(Intent.EXTRA_TEXT, "Conheça $text no TQFapp - Tem Quem Faça!")
            startActivity(Intent.createChooser(share, "Compartilhar perfil"))
        }

        view.findViewById<View>(R.id.menuLogoutProvider)?.setOnClickListener {
            performLogout()
        }

        view.findViewById<View>(R.id.btnChangeAvatar)?.setOnClickListener {
            pickAvatarLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }

        view.findViewById<View>(R.id.imgAvatar)?.setOnClickListener {
            if (isOwner) {
                pickAvatarLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            }
        }

        val pickCover = View.OnClickListener {
            if (isOwner) pickCoverLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }
        view.findViewById<View>(R.id.btnChangeCover)?.setOnClickListener(pickCover)
        view.findViewById<View>(R.id.imgCover)?.setOnClickListener(pickCover)

        view.findViewById<View>(R.id.btnEditProfile)?.setOnClickListener {
            showEditProfileDialog(view)
        }

        // Não há tela de portfólio completo: as fotos já aparecem todas na lista horizontal.
        view.findViewById<View>(R.id.btnSeeAllPortfolio)?.visibility = View.GONE

        val openAllReviews = {
            val isOwner = arguments?.getBoolean(ARG_IS_OWNER, false) ?: false
            val userRepository = UserRepository(requireContext())
            val currentUser = if (isOwner) userRepository.getCurrentUser() else null
            val targetProviderId = currentUser?.id ?: arguments?.getString(ARG_PROVIDER_ID) ?: ""
            val providerName = currentUser?.name ?: arguments?.getString(ARG_NAME) ?: "Prestador"

            val fragment = AllReviewsFragment.newInstance(targetProviderId, providerName)
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragmentContainer, fragment)
                .addToBackStack(null)
                .commit()
        }

        view.findViewById<View>(R.id.btnSeeAllReviews)?.setOnClickListener { openAllReviews() }
        view.findViewById<View>(R.id.txtReviewsTitle)?.setOnClickListener { openAllReviews() }
    }

    /**
     * Diálogo amigável para o Prestador definir o endereço do escritório
     */
    private fun showEditProfileDialog(rootView: View) {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_edit_provider_profile, null)
        val etName = dialogView.findViewById<TextInputEditText>(R.id.etName)
        val actvCategory = dialogView.findViewById<AutoCompleteTextView>(R.id.actvCategory)
        val etSpecialty = dialogView.findViewById<TextInputEditText>(R.id.etSpecialty)
        val etBio = dialogView.findViewById<TextInputEditText>(R.id.etBio)
        val etLocation = dialogView.findViewById<TextInputEditText>(R.id.etLocation)
        val etLatitude = dialogView.findViewById<TextInputEditText>(R.id.etLatitude)
        val etLongitude = dialogView.findViewById<TextInputEditText>(R.id.etLongitude)
        val btnSearchAddress = dialogView.findViewById<View>(R.id.btnSearchAddress)
        val txtLocationStatus = dialogView.findViewById<TextView>(R.id.txtLocationStatus)
        val etNewService = dialogView.findViewById<TextInputEditText>(R.id.etNewService)
        val btnAddService = dialogView.findViewById<View>(R.id.btnAddService)
        val rvDialogServices = dialogView.findViewById<RecyclerView>(R.id.rvDialogServices)
        val btnAddPortfolio = dialogView.findViewById<View>(R.id.btnAddPortfolio)
        val rvPortfolioPreviews = dialogView.findViewById<RecyclerView>(R.id.rvPortfolioPreviews)
        val etProviderPhone = dialogView.findViewById<TextInputEditText>(R.id.etProviderPhone)

        val txtName = rootView.findViewById<TextView>(R.id.txtProfessionalName)
        val txtSpecialty = rootView.findViewById<TextView>(R.id.txtSpecialty)
        val txtBio = rootView.findViewById<TextView>(R.id.txtBio)

        val userRepository = UserRepository(requireContext())
        val currentUser = userRepository.getCurrentUser()

        etName?.setText(currentUser?.name ?: txtName?.text)
        etSpecialty?.setText(currentUser?.specialty ?: txtSpecialty?.text)
        etBio?.setText(currentUser?.bio ?: txtBio?.text)
        etProviderPhone?.setText(currentUser?.telephone)
        etLatitude?.setText(officeLatitude.toString())
        etLongitude?.setText(officeLongitude.toString())

        val categories = ServiceCategories.ALL
        val categoryAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, categories)
        actvCategory?.setAdapter(categoryAdapter)

        currentUser?.category?.let { actvCategory?.setText(it, false) }

        editPortfolioUris.clear()
        currentUser?.portfolioUris?.forEach { editPortfolioUris.add(Uri.parse(it)) }

        editPortfolioAdapter = PortfolioPreviewAdapter(editPortfolioUris) { uriToRemove ->
            editPortfolioUris.remove(uriToRemove)
            editPortfolioAdapter?.notifyDataSetChanged()
        }
        rvPortfolioPreviews?.layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        rvPortfolioPreviews?.adapter = editPortfolioAdapter

        btnAddPortfolio?.setOnClickListener {
            editPortfolioLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }

        val dialogServicesList = currentUser?.services.orEmpty().toMutableList()

        val dialogServiceAdapter = DialogServiceAdapter(dialogServicesList) { serviceToRemove ->
            dialogServicesList.remove(serviceToRemove)
            rvDialogServices?.adapter?.notifyDataSetChanged()
        }
        rvDialogServices?.layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        rvDialogServices?.adapter = dialogServiceAdapter

        btnAddService?.setOnClickListener {
            val newService = etNewService?.text?.toString()?.trim().orEmpty()
            if (newService.isNotEmpty()) {
                if (!dialogServicesList.contains(newService)) {
                    dialogServicesList.add(newService)
                    dialogServiceAdapter.notifyDataSetChanged()
                    etNewService?.setText("")
                } else {
                    Toast.makeText(requireContext(), "Serviço já adicionado", Toast.LENGTH_SHORT).show()
                }
            }
        }

        val cardDialogMap = dialogView.findViewById<View>(R.id.cardDialogMap)
        val dialogMapView = dialogView.findViewById<MapView>(R.id.dialogMapView)
        var dialogGoogleMap: GoogleMap? = null

        fun updateDialogMapMarker(map: GoogleMap, coords: LatLng, title: String) {
            map.clear()
            map.uiSettings.isZoomControlsEnabled = true
            map.animateCamera(CameraUpdateFactory.newLatLngZoom(coords, 15f))
            map.addMarker(
                MarkerOptions()
                    .position(coords)
                    .title("📍 $title")
                    .snippet("Local do Escritório")
            )?.showInfoWindow()
        }

        if (dialogMapView != null) {
            try {
                MapsInitializer.initialize(requireContext())
                dialogMapView.onCreate(null)
                dialogMapView.onResume()
                dialogMapView.getMapAsync { map ->
                    dialogGoogleMap = map
                    MapUtils.applyCustomMapStyle(requireContext(), map)
                    if (officeLatitude != 0.0 && officeLongitude != 0.0) {
                        cardDialogMap?.visibility = View.VISIBLE
                        updateDialogMapMarker(map,
                            LatLng(officeLatitude, officeLongitude), etLocation?.text?.toString() ?: "Escritório")
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // Converte as coordenadas atuais em endereço legível
        // Geocoder faz rede: consulta em IO, tela atualizada ao terminar.
        val geoContext = requireContext().applicationContext
        viewLifecycleOwner.lifecycleScope.launch {
            val currentAddress = withContext(Dispatchers.IO) { LocationUtils.getAddressFromCoordinates(geoContext, officeLatitude, officeLongitude) }
            if (!currentAddress.isNullOrEmpty()) {
                etLocation?.setText(currentAddress)
                txtLocationStatus?.visibility = View.VISIBLE
                txtLocationStatus?.text = "✓ Localização capturada: $currentAddress"
            }
        }

        btnSearchAddress?.setOnClickListener {
            val addressText = etLocation?.text?.toString()?.trim().orEmpty()
            if (addressText.isEmpty()) {
                Toast.makeText(requireContext(), "Digite um endereço para localizar no mapa", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Geocoder faz rede: consulta em IO, tela atualizada ao terminar.
            val geoContext = requireContext().applicationContext
            viewLifecycleOwner.lifecycleScope.launch {
                val coords = withContext(Dispatchers.IO) { LocationUtils.getCoordinatesFromAddress(geoContext, addressText) }
                if (coords != null) {
                    etLatitude?.setText(coords.latitude.toString())
                    etLongitude?.setText(coords.longitude.toString())
                    txtLocationStatus?.visibility = View.VISIBLE
                    txtLocationStatus?.text = "✓ Endereço localizado! Posição pronta no mapa."

                    cardDialogMap?.visibility = View.VISIBLE
                    dialogGoogleMap?.let { map ->
                        updateDialogMapMarker(map, coords, addressText)
                    }

                    Toast.makeText(requireContext(), "Endereço localizado e posicionado no mapa!", Toast.LENGTH_SHORT).show()
                } else {
                    txtLocationStatus?.visibility = View.VISIBLE
                    txtLocationStatus?.text = "⚠️ Endereço não encontrado. Verifique os dados digitados."
                    Toast.makeText(requireContext(), "Endereço não encontrado. Tente incluir rua, número e cidade.", Toast.LENGTH_LONG).show()
                }
            }
        }

        MaterialAlertDialogBuilder(requireContext())
            .setView(dialogView)
            .setPositiveButton("Salvar") { _, _ ->
                val newName = etName?.text?.toString()?.trim().orEmpty()
                val newCategory = actvCategory?.text?.toString()?.trim().orEmpty()
                val newSpecialty = etSpecialty?.text?.toString()?.trim().orEmpty()
                val newBio = etBio?.text?.toString()?.trim().orEmpty()
                val newPhone = etProviderPhone?.text?.toString()?.trim().orEmpty()
                val addressText = etLocation?.text?.toString()?.trim().orEmpty()

                val typedLat = etLatitude?.text?.toString()?.toDoubleOrNull()
                val typedLng = etLongitude?.text?.toString()?.toDoubleOrNull()
                val services = dialogServicesList.toList()
                val portfolio = editPortfolioUris.map { it.toString() }
                val appContext = requireContext().applicationContext

                if (newName.isNotEmpty()) txtName?.text = newName
                if (newSpecialty.isNotEmpty()) txtSpecialty?.text = newSpecialty
                if (newBio.isNotEmpty()) txtBio?.text = newBio

                // Geocoding, upload das fotos novas do portfólio e gravação: tudo em IO.
                viewLifecycleOwner.lifecycleScope.launch {
                    val saved = withContext(Dispatchers.IO) {
                        val repository = UserRepository(appContext)
                        // Só edita o próprio perfil logado; nunca cai para outro usuário do cache.
                        val user = currentUser ?: repository.getCurrentUser() ?: return@withContext null
                        val coords = if (addressText.isNotEmpty() && (typedLat == null || typedLng == null)) {
                            LocationUtils.getCoordinatesFromAddress(appContext, addressText)
                        } else null
                        val (_, uploadedPortfolio) = StorageRepository(appContext).uploadLocalMedia(null, portfolio)
                        val updated = user.copy(
                            name = newName.ifEmpty { user.name },
                            category = newCategory.ifEmpty { user.category },
                            specialty = newSpecialty.ifEmpty { user.specialty },
                            bio = newBio.ifEmpty { user.bio },
                            telephone = newPhone.ifEmpty { user.telephone },
                            address = addressText.ifEmpty { user.address },
                            services = services,
                            latitude = coords?.latitude ?: typedLat ?: user.latitude,
                            longitude = coords?.longitude ?: typedLng ?: user.longitude,
                            portfolioUris = uploadedPortfolio
                        )
                        if (repository.updateUser(updated)) repository.getCurrentUser() ?: updated else null
                    }
                    if (getView() == null) return@launch
                    if (saved == null) {
                        showToast("Não foi possível salvar o perfil. Tente novamente.")
                        return@launch
                    }
                    reloadUserData(rootView, saved)
                    if (portfolio.size != saved.portfolioUris.size) {
                        showToast("Perfil atualizado, mas algumas fotos do portfólio não foram enviadas.")
                    } else {
                        showToast("Perfil de prestador atualizado!")
                    }
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun performLogout() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Sair da conta")
            .setMessage("Tem certeza que deseja encerrar sua sessão de prestador?")
            .setPositiveButton("Sair") { _, _ ->
                val userRepository = com.tqfapp.android.data.repository.UserRepository(requireContext())
                userRepository.logout()
                Toast.makeText(requireContext(), "Sessão encerrada com sucesso.", Toast.LENGTH_SHORT).show()
                val intent = Intent(requireContext(), LoginActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                }
                startActivity(intent)
                requireActivity().finish()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    /** Fotos reais do portfólio (perfis_prestador.portfolio_urls); usuário já está em cache. */
    private fun setupPortfolio(view: View, photos: List<String>? = null) {
        val isOwner = arguments?.getBoolean(ARG_IS_OWNER, false) ?: false
        val portfolio = photos
            ?: if (isOwner) UserRepository(requireContext()).getCurrentUser()?.portfolioUris.orEmpty() else emptyList()

        view.findViewById<RecyclerView>(R.id.rvPortfolio)?.apply {
            layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
            adapter = PortfolioPhotoAdapter(portfolio)
            visibility = if (portfolio.isEmpty()) View.GONE else View.VISIBLE
        }
        view.findViewById<View>(R.id.txtNoPortfolio)?.visibility = if (portfolio.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun setupReviews(view: View) {
        val isOwner = arguments?.getBoolean(ARG_IS_OWNER, false) ?: false
        val userRepository = UserRepository(requireContext())
        val currentUser = if (isOwner) userRepository.getCurrentUser() else null
        val targetProviderId = currentUser?.id ?: arguments?.getString(ARG_PROVIDER_ID)

        // Avaliações vêm do Supabase: consulta em IO, tela preenchida quando chegar.
        val appContext = requireContext().applicationContext
        viewLifecycleOwner.lifecycleScope.launch {
            val providerReviews = withContext(Dispatchers.IO) {
                if (!targetProviderId.isNullOrEmpty()) {
                    ReviewRepository(appContext).getReviewsByProvider(targetProviderId)
                } else {
                    emptyList()
                }
            }
            if (getView() == null) return@launch
            showReviews(view, providerReviews)
        }
    }

    private fun showReviews(view: View, providerReviews: List<Review>) {
        val count = providerReviews.size
        view.findViewById<TextView>(R.id.txtReviewsTitle)?.text = "Avaliações ($count)"

        if (arguments?.getString(ARG_RATING) == null) {
            view.findViewById<TextView>(R.id.txtRating)?.text =
                RatingFormat.summary(providerReviews) ?: "Sem avaliações ainda"
        }

        val reviewsList = providerReviews.map {
            ReviewItem(
                reviewerName = "Cliente",
                date = it.date,
                comment = "⭐ ${it.rating}/5 - ${it.comment}",
                avatarRes = R.drawable.ic_profile
            )
        }

        view.findViewById<RecyclerView>(R.id.rvReviews)?.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = ReviewAdapter(reviewsList) { }
        }
    }

    private fun setupCompletedServicesCount(view: View) {
        val isOwner = arguments?.getBoolean(ARG_IS_OWNER, false) ?: false
        val userRepository = UserRepository(requireContext())
        val currentUser = if (isOwner) userRepository.getCurrentUser() else null
        val targetProviderId = currentUser?.id ?: arguments?.getString(ARG_PROVIDER_ID)

        val appContext = requireContext().applicationContext
        viewLifecycleOwner.lifecycleScope.launch {
            val completedCount = withContext(Dispatchers.IO) {
                if (!targetProviderId.isNullOrEmpty()) {
                    ServiceRepository(appContext).getProviderCompletedServices(targetProviderId).size
                } else {
                    0
                }
            }
            view.findViewById<TextView>(R.id.txtCompletedCount)?.text = completedCount.toString()
        }
    }



    private fun showToast(message: String) {
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
    }

    // Passagem de Ciclo de Vida para o MapView evitar telas pretas
    override fun onResume() {
        super.onResume()
        if (isMapCreated) {
            try { mapView?.onResume() } catch (_: Exception) {}
        }
    }

    override fun onPause() {
        if (isMapCreated) {
            try { mapView?.onPause() } catch (_: Exception) {}
        }
        super.onPause()
    }

    override fun onDestroyView() {
        if (isMapCreated) {
            try { mapView?.onDestroy() } catch (_: Exception) {}
        }
        isMapCreated = false
        googleMap = null
        mapView = null
        super.onDestroyView()
    }

    override fun onLowMemory() {
        super.onLowMemory()
        if (isMapCreated) {
            try { mapView?.onLowMemory() } catch (_: Exception) {}
        }
    }

    companion object {
        private const val ARG_IS_OWNER = "arg_is_owner"
        private const val ARG_PROVIDER_ID = "arg_provider_id"
        private const val ARG_NAME = "arg_name"
        private const val ARG_SPECIALTY = "arg_specialty"
        private const val ARG_RATING = "arg_rating"
        private const val ARG_BIO = "arg_bio"
        private const val ARG_LATITUDE = "arg_latitude"
        private const val ARG_LONGITUDE = "arg_longitude"
        private const val ARG_ADDRESS = "arg_address"

        // Coordenadas padrão de exemplo (Manaus, AM)
        const val DEFAULT_LATITUDE = -3.1190
        const val DEFAULT_LONGITUDE = -60.0217

        fun newInstance(
            isOwner: Boolean = false,
            providerId: String? = null,
            name: String? = null,
            specialty: String? = null,
            rating: String? = null,
            bio: String? = null,
            latitude: Double = DEFAULT_LATITUDE,
            longitude: Double = DEFAULT_LONGITUDE,
            address: String? = null
        ): ProfessionalProfileFragment {
            val fragment = ProfessionalProfileFragment()
            val args = Bundle().apply {
                putBoolean(ARG_IS_OWNER, isOwner)
                providerId?.let { putString(ARG_PROVIDER_ID, it) }
                name?.let { putString(ARG_NAME, it) }
                specialty?.let { putString(ARG_SPECIALTY, it) }
                rating?.let { putString(ARG_RATING, it) }
                bio?.let { putString(ARG_BIO, it) }
                putDouble(ARG_LATITUDE, latitude)
                putDouble(ARG_LONGITUDE, longitude)
                address?.let { putString(ARG_ADDRESS, it) }
            }
            fragment.arguments = args
            return fragment
        }
    }

    data class ReviewItem(
        val reviewerName: String,
        val date: String,
        val comment: String,
        val avatarRes: Int
    )

    private inner class ReviewAdapter(
        private val items: List<ReviewItem>,
        private val onItemClick: (ReviewItem) -> Unit
    ) : RecyclerView.Adapter<ReviewAdapter.ViewHolder>() {

        inner class ViewHolder(val view: View) : RecyclerView.ViewHolder(view) {
            val txtReviewerName: TextView = view.findViewById(R.id.txtReviewerName)
            val txtReviewDate: TextView = view.findViewById(R.id.txtReviewDate)
            val txtReviewComment: TextView = view.findViewById(R.id.txtReviewComment)
            val imgReviewerAvatar: ImageView = view.findViewById(R.id.imgReviewerAvatar)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_review, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = items[position]
            holder.txtReviewerName.text = item.reviewerName
            holder.txtReviewDate.text = item.date
            holder.txtReviewComment.text = item.comment
            holder.imgReviewerAvatar.setImageResource(item.avatarRes)
            holder.view.setOnClickListener { onItemClick(item) }
        }

        override fun getItemCount(): Int = items.size
    }

    private fun setupDynamicChips(view: View, services: List<String>) {
        view.findViewById<ChipGroup>(R.id.chipGroupServices)?.bindServiceChips(services)
    }

    /** Reaplica na tela o perfil recém-salvo (sem nova consulta na thread principal). */
    private fun reloadUserData(view: View, user: User) {
        user.name.takeIf { it.isNotEmpty() }?.let { view.findViewById<TextView>(R.id.txtProfessionalName)?.text = it }
        user.specialty?.takeIf { it.isNotEmpty() }?.let { view.findViewById<TextView>(R.id.txtSpecialty)?.text = it }
        officeLatitude = user.latitude ?: DEFAULT_LATITUDE
        officeLongitude = user.longitude ?: DEFAULT_LONGITUDE
        updateOfficeMapMarker(view)
        setupDynamicChips(view, user.services)
        setupPortfolio(view, user.portfolioUris)
    }

    private inner class DialogServiceAdapter(
        private val items: MutableList<String>,
        private val onDeleteClick: (String) -> Unit
    ) : RecyclerView.Adapter<DialogServiceAdapter.ViewHolder>() {

        inner class ViewHolder(val view: View) : RecyclerView.ViewHolder(view) {
            val txtServiceName: TextView = view.findViewById(R.id.txtDialogServiceName)
            val btnRemove: ImageView = view.findViewById(R.id.btnRemoveDialogService)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_dialog_service, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val serviceName = items[position]
            holder.txtServiceName.text = serviceName
            holder.btnRemove.setOnClickListener { onDeleteClick(serviceName) }
        }

        override fun getItemCount(): Int = items.size
    }

    private inner class PortfolioPreviewAdapter(
        private val items: List<Uri>,
        private val onItemClick: (Uri) -> Unit
    ) : RecyclerView.Adapter<PortfolioPreviewAdapter.ViewHolder>() {

        inner class ViewHolder(val view: View) : RecyclerView.ViewHolder(view) {
            val imgPortfolio: ImageView = view.findViewById(R.id.imgPortfolio)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_portfolio, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val uri = items[position]
            holder.imgPortfolio.loadImage(uri.toString())
            holder.view.setOnClickListener { onItemClick(uri) }
        }

        override fun getItemCount(): Int = items.size
    }
}
