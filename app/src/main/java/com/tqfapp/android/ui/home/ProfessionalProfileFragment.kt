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
import com.tqfapp.android.business.ServiceCategories
import com.tqfapp.android.business.RatingFormat
import com.tqfapp.android.data.repository.ReviewRepository
import com.tqfapp.android.data.repository.ServiceRepository
import com.tqfapp.android.data.repository.UserRepository
import com.tqfapp.android.ui.auth.LoginActivity
import com.tqfapp.android.utils.LocationUtils
import com.tqfapp.android.utils.MapUtils

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
        if (uri != null) {
            view?.findViewById<ImageView>(R.id.imgAvatar)?.setImageURI(uri)
            val storageRepository = com.tqfapp.android.data.repository.StorageRepository(requireContext())
            val uploadResult = storageRepository.uploadProfilePicture(uri)
            if (uploadResult.isSuccess) {
                val publicUrl = uploadResult.getOrNull()
                val userRepository = UserRepository(requireContext())
                val user = userRepository.getCurrentUser()
                if (user != null && publicUrl != null) {
                    userRepository.updateUser(user.copy(profilePictureUri = publicUrl))
                }
                Toast.makeText(requireContext(), "Foto enviada para o Supabase Storage com sucesso!", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(requireContext(), "Foto de perfil atualizada!", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Photo Picker launcher para Foto de Capa
    private val pickCoverLauncher = registerForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            view?.findViewById<ImageView>(R.id.imgCover)?.setImageURI(uri)
            Toast.makeText(requireContext(), "Foto de capa atualizada!", Toast.LENGTH_SHORT).show()
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

        // Geocodificação Reversa para garantir sincronização entre o texto e a Latitude/Longitude reais do prestador
        val geocodedAddress = LocationUtils.getAddressFromCoordinates(requireContext(), officeLatitude, officeLongitude)

        val finalAddressText = when {
            !passedAddress.isNullOrBlank() && !isOwner -> passedAddress
            !savedAddress.isNullOrBlank() -> savedAddress
            !geocodedAddress.isNullOrBlank() -> geocodedAddress
            else -> "Manaus - AM (Atendimento Presencial)"
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

        view.findViewById<View>(R.id.btnFavorite)?.setOnClickListener {
            showToast("Adicionado aos favoritos")
        }

        view.findViewById<View>(R.id.btnShare)?.setOnClickListener {
            showToast("Compartilhar perfil")
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

        view.findViewById<View>(R.id.btnChangeCover)?.setOnClickListener {
            pickCoverLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }

        view.findViewById<View>(R.id.imgCover)?.setOnClickListener {
            if (isOwner) {
                pickCoverLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            }
        }

        view.findViewById<View>(R.id.btnEditProfile)?.setOnClickListener {
            showEditProfileDialog(view)
        }

        view.findViewById<View>(R.id.btnSeeAllPortfolio)?.setOnClickListener {
            showToast("Ver todo o portfólio")
        }

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
        val currentAddress = LocationUtils.getAddressFromCoordinates(requireContext(), officeLatitude, officeLongitude)
        if (!currentAddress.isNullOrEmpty()) {
            etLocation?.setText(currentAddress)
            txtLocationStatus?.visibility = View.VISIBLE
            txtLocationStatus?.text = "✓ Localização capturada: $currentAddress"
        }

        btnSearchAddress?.setOnClickListener {
            val addressText = etLocation?.text?.toString()?.trim().orEmpty()
            if (addressText.isEmpty()) {
                Toast.makeText(requireContext(), "Digite um endereço para localizar no mapa", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val coords = LocationUtils.getCoordinatesFromAddress(requireContext(), addressText)
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

        MaterialAlertDialogBuilder(requireContext())
            .setView(dialogView)
            .setPositiveButton("Salvar") { _, _ ->
                val newName = etName?.text?.toString()?.trim().orEmpty()
                val newCategory = actvCategory?.text?.toString()?.trim().orEmpty()
                val newSpecialty = etSpecialty?.text?.toString()?.trim().orEmpty()
                val newBio = etBio?.text?.toString()?.trim().orEmpty()
                val newPhone = etProviderPhone?.text?.toString()?.trim().orEmpty()
                val addressText = etLocation?.text?.toString()?.trim().orEmpty()

                var newLat = etLatitude?.text?.toString()?.toDoubleOrNull()
                var newLng = etLongitude?.text?.toString()?.toDoubleOrNull()

                if (addressText.isNotEmpty() && (newLat == null || newLng == null)) {
                    val coords = LocationUtils.getCoordinatesFromAddress(requireContext(), addressText)
                    if (coords != null) {
                        newLat = coords.latitude
                        newLng = coords.longitude
                    }
                }

                if (newName.isNotEmpty()) txtName?.text = newName
                if (newSpecialty.isNotEmpty()) txtSpecialty?.text = newSpecialty
                if (newBio.isNotEmpty()) txtBio?.text = newBio

                if (newLat != null && newLng != null) {
                    officeLatitude = newLat
                    officeLongitude = newLng
                    updateOfficeMapMarker(rootView)
                }

                // Só edita o próprio perfil logado; nunca cai para outro usuário do cache.
                val user = currentUser ?: userRepository.getCurrentUser()
                if (user != null) {
                    val updated = user.copy(
                        name = if (newName.isNotEmpty()) newName else user.name,
                        category = if (newCategory.isNotEmpty()) newCategory else user.category,
                        specialty = if (newSpecialty.isNotEmpty()) newSpecialty else user.specialty,
                        bio = if (newBio.isNotEmpty()) newBio else user.bio,
                        telephone = if (newPhone.isNotEmpty()) newPhone else user.telephone,
                        services = dialogServicesList.toList(),
                        latitude = if (newLat != null) newLat else user.latitude,
                        longitude = if (newLng != null) newLng else user.longitude,
                        portfolioUris = editPortfolioUris.map { it.toString() }
                    )
                    userRepository.updateUser(updated)
                    reloadUserData(rootView)
                }

                showToast("Perfil de prestador atualizado!")
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

    private fun setupPortfolio(view: View) {
        val portfolioImages = listOf(
            R.drawable.ic_tech_repair,
            R.drawable.ic_laptop,
            R.drawable.ic_clipboard,
            R.drawable.ic_briefcase
        )

        view.findViewById<RecyclerView>(R.id.rvPortfolio)?.apply {
            layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
            adapter = PortfolioAdapter(portfolioImages) {
                showToast("Visualizando imagem do portfólio")
            }
        }
    }

    private fun setupReviews(view: View) {
        val isOwner = arguments?.getBoolean(ARG_IS_OWNER, false) ?: false
        val userRepository = UserRepository(requireContext())
        val currentUser = if (isOwner) userRepository.getCurrentUser() else null
        val targetProviderId = currentUser?.id ?: arguments?.getString(ARG_PROVIDER_ID)

        val providerReviews = if (!targetProviderId.isNullOrEmpty()) {
            ReviewRepository(requireContext()).getReviewsByProvider(targetProviderId)
        } else {
            emptyList()
        }

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
            adapter = ReviewAdapter(reviewsList) {
                showToast("Avaliação de ${it.reviewerName}")
            }
        }
    }

    private fun setupCompletedServicesCount(view: View) {
        val isOwner = arguments?.getBoolean(ARG_IS_OWNER, false) ?: false
        val userRepository = UserRepository(requireContext())
        val currentUser = if (isOwner) userRepository.getCurrentUser() else null
        val targetProviderId = currentUser?.id ?: arguments?.getString(ARG_PROVIDER_ID)

        val completedCount = if (!targetProviderId.isNullOrEmpty()) {
            ServiceRepository(requireContext()).getProviderCompletedServices(targetProviderId).size
        } else {
            0
        }

        view.findViewById<TextView>(R.id.txtCompletedCount)?.text = completedCount.toString()
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

    private inner class PortfolioAdapter(
        private val items: List<Int>,
        private val onItemClick: (Int) -> Unit
    ) : RecyclerView.Adapter<PortfolioAdapter.ViewHolder>() {

        inner class ViewHolder(val view: View) : RecyclerView.ViewHolder(view) {
            val imgPortfolio: ImageView = view.findViewById(R.id.imgPortfolio)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_portfolio, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = items[position]
            holder.imgPortfolio.setImageResource(item)
            holder.view.setOnClickListener { onItemClick(item) }
        }

        override fun getItemCount(): Int = items.size
    }

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

    private fun reloadUserData(view: View) {
        val userRepository = UserRepository(requireContext())
        val user = userRepository.getCurrentUser()
        if (user != null) {
            user.name.takeIf { it.isNotEmpty() }?.let { view.findViewById<TextView>(R.id.txtProfessionalName)?.text = it }
            user.specialty?.takeIf { it.isNotEmpty() }?.let { view.findViewById<TextView>(R.id.txtSpecialty)?.text = it }
            officeLatitude = user.latitude ?: DEFAULT_LATITUDE
            officeLongitude = user.longitude ?: DEFAULT_LONGITUDE
            setupDynamicChips(view, user.services)
        }
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
            holder.imgPortfolio.setImageURI(uri)
            holder.view.setOnClickListener { onItemClick(uri) }
        }

        override fun getItemCount(): Int = items.size
    }
}
