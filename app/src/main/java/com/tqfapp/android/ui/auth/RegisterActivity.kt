package com.tqfapp.android.ui.auth

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Patterns
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.tqfapp.android.R
import com.tqfapp.android.data.model.User
import com.tqfapp.android.data.repository.UserRepository
import com.tqfapp.android.databinding.ActivityRegisterBinding
import com.tqfapp.android.ui.home.HomeActivity
import com.tqfapp.android.utils.LocationUtils
import com.tqfapp.android.utils.UserSessionManager
import java.util.UUID

class RegisterActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRegisterBinding
    private lateinit var userRepository: UserRepository

    private var isProviderMode: Boolean = false
    private var selectedAvatarUri: Uri? = null
    private val selectedPortfolioUris = mutableListOf<Uri>()
    private lateinit var portfolioAdapter: PortfolioPreviewAdapter

    // Launcher para selecionar Foto de Perfil
    private val pickAvatarLauncher = registerForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            selectedAvatarUri = uri
            binding.imgRegisterAvatar.setImageURI(uri)
            binding.txtSelectAvatarLabel.text = "Alterar Foto de Perfil"
            Toast.makeText(this, "Foto de perfil selecionada!", Toast.LENGTH_SHORT).show()
        }
    }

    // Launcher para selecionar Imagens do Portfólio (Máximo 5)
    private val pickPortfolioLauncher = registerForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(5)
    ) { uris ->
        if (uris.isNotEmpty()) {
            uris.forEach { uri ->
                if (selectedPortfolioUris.size < 5 && !selectedPortfolioUris.contains(uri)) {
                    selectedPortfolioUris.add(uri)
                }
            }
            portfolioAdapter.notifyDataSetChanged()
            Toast.makeText(this, "${selectedPortfolioUris.size} foto(s) no portfólio", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRegisterBinding.inflate(layoutInflater)
        setContentView(binding.root)

        userRepository = UserRepository(this)

        setupUserTypeToggle()
        setupCategoryDropdown()
        setupPhotoPickers()
        setupPortfolioRecyclerView()
        setupClickListeners()
    }

    private fun setupUserTypeToggle() {
        binding.toggleRegisterUserType.check(R.id.btnRegisterContractor)
        binding.toggleRegisterUserType.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                when (checkedId) {
                    R.id.btnRegisterContractor -> {
                        isProviderMode = false
                        binding.layoutProviderFields.visibility = View.GONE
                        binding.btnRegisterContractor.setTextColor(getColor(R.color.brand_blue))
                        binding.btnRegisterProvider.setTextColor(getColor(R.color.text_secondary))
                    }
                    R.id.btnRegisterProvider -> {
                        isProviderMode = true
                        binding.layoutProviderFields.visibility = View.VISIBLE
                        binding.btnRegisterProvider.setTextColor(getColor(R.color.brand_blue))
                        binding.btnRegisterContractor.setTextColor(getColor(R.color.text_secondary))
                    }
                }
            }
        }
    }

    private fun setupCategoryDropdown() {
        val categories = listOf(
            "Assistência Técnica",
            "Reformas e Reparos",
            "Serviços Domésticos",
            "Design e Tecnologia",
            "Eventos",
            "Aulas e Treinamentos",
            "Saúde e Bem-Estar",
            "Autos e Transporte"
        )

        val adapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, categories)
        binding.actvCategory.setAdapter(adapter)
    }

    private fun setupPhotoPickers() {
        // Seleção de Foto de Perfil
        binding.containerAvatar.setOnClickListener {
            pickAvatarLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }
        binding.txtSelectAvatarLabel.setOnClickListener {
            pickAvatarLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }

        // Seleção de Fotos de Portfólio
        binding.btnAddPortfolio.setOnClickListener {
            if (selectedPortfolioUris.size >= 5) {
                Toast.makeText(this, "Limite máximo de 5 fotos atingido.", Toast.LENGTH_SHORT).show()
            } else {
                pickPortfolioLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            }
        }
    }

    private fun setupPortfolioRecyclerView() {
        portfolioAdapter = PortfolioPreviewAdapter(selectedPortfolioUris) { uriToRemove ->
            selectedPortfolioUris.remove(uriToRemove)
            portfolioAdapter.notifyDataSetChanged()
        }

        binding.rvPortfolioPreviews.apply {
            layoutManager = LinearLayoutManager(this@RegisterActivity, LinearLayoutManager.HORIZONTAL, false)
            adapter = portfolioAdapter
        }
    }

    private fun setupClickListeners() {
        binding.btnBack.setOnClickListener {
            finish()
        }

        binding.txtLogin.setOnClickListener {
            finish()
        }

        // Botão para buscar endereço e converter em coordenadas
        binding.btnSearchAddress.setOnClickListener {
            val addressText = binding.etLocation.text?.toString()?.trim().orEmpty()
            if (addressText.isEmpty()) {
                Toast.makeText(this, "Digite um endereço para localizar no mapa", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val coords = LocationUtils.getCoordinatesFromAddress(this, addressText)
            if (coords != null) {
                binding.etLatitude.setText(coords.latitude.toString())
                binding.etLongitude.setText(coords.longitude.toString())
                binding.txtLocationStatus.visibility = View.VISIBLE
                binding.txtLocationStatus.text = "✓ Endereço localizado com sucesso!"
                Toast.makeText(this, "Endereço localizado no mapa!", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Endereço não encontrado. Tente incluir rua, número e cidade.", Toast.LENGTH_LONG).show()
            }
        }

        binding.btnRegister.setOnClickListener {
            performRegistration()
        }
    }

    private fun performRegistration() {
        clearErrors()

        val name = binding.etName.text?.toString()?.trim().orEmpty()
        val email = binding.etEmail.text?.toString()?.trim().orEmpty()
        val phone = binding.etPhone.text?.toString()?.trim().orEmpty()
        val password = binding.etPassword.text?.toString()?.trim().orEmpty()
        val confirmPassword = binding.etConfirmPassword.text?.toString()?.trim().orEmpty()

        // Validação dos Campos Obrigatórios
        var isValid = true

        if (name.isEmpty()) {
            binding.tilName.error = "Digite seu nome completo"
            isValid = false
        }

        if (email.isEmpty() || !Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            binding.tilEmail.error = "Digite um e-mail válido"
            isValid = false
        }

        if (phone.isEmpty()) {
            binding.tilPhone.error = "Digite seu telefone / WhatsApp"
            isValid = false
        }

        if (password.length < 6) {
            binding.tilPassword.error = "A senha deve conter no mínimo 6 caracteres"
            isValid = false
        }

        if (confirmPassword != password) {
            binding.tilConfirmPassword.error = "As senhas não coincidem"
            isValid = false
        }

        // Validação Específica para Prestador de Serviço
        var category: String? = null
        var specialty: String? = null
        var latitude: Double? = null
        var longitude: Double? = null

        if (isProviderMode) {
            category = binding.actvCategory.text?.toString()?.trim().orEmpty()
            specialty = binding.etSpecialty.text?.toString()?.trim().orEmpty()

            val addressText = binding.etLocation.text?.toString()?.trim().orEmpty()
            if (addressText.isNotEmpty()) {
                val coords = LocationUtils.getCoordinatesFromAddress(this, addressText)
                if (coords != null) {
                    latitude = coords.latitude
                    longitude = coords.longitude
                }
            }

            if (latitude == null) {
                latitude = binding.etLatitude.text?.toString()?.toDoubleOrNull() ?: -23.550520
            }
            if (longitude == null) {
                longitude = binding.etLongitude.text?.toString()?.toDoubleOrNull() ?: -46.633308
            }

            if (category.isEmpty()) {
                binding.tilCategory.error = "Selecione a categoria do seu serviço"
                isValid = false
            }

            if (specialty.isEmpty()) {
                binding.tilSpecialty.error = "Digite sua especialidade"
                isValid = false
            }
        }

        if (!isValid) return

        // Cria a instância do modelo de usuário pronto para integração
        val newUser = User(
            id = UUID.randomUUID().toString(),
            name = name,
            email = email,
            phone = phone,
            isProvider = isProviderMode,
            category = category,
            specialty = specialty,
            profilePictureUri = selectedAvatarUri?.toString(),
            portfolioUris = selectedPortfolioUris.map { it.toString() },
            rating = 5.0,
            latitude = latitude,
            longitude = longitude
        )

        // Envia para a camada de repositório (pronto para ser integrado com o BD/API)
        val result = userRepository.registerUser(newUser, password)

        result.onSuccess { user ->
            UserSessionManager.saveUserSession(this, user.isProvider, user.name, user.email)

            val profileType = if (user.isProvider) "Prestador de Serviço" else "Contratante"
            Toast.makeText(this, "Cadastro de $profileType realizado com sucesso!", Toast.LENGTH_LONG).show()

            val intent = Intent(this, HomeActivity::class.java).apply {
                putExtra(LoginActivity.EXTRA_IS_PROVIDER, user.isProvider)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            startActivity(intent)
            finish()
        }.onFailure { exception ->
            binding.tilEmail.error = exception.message ?: "Erro ao cadastrar usuário"
            Toast.makeText(this, exception.message ?: "Falha no cadastro", Toast.LENGTH_SHORT).show()
        }
    }

    private fun clearErrors() {
        binding.tilName.error = null
        binding.tilEmail.error = null
        binding.tilPhone.error = null
        binding.tilPassword.error = null
        binding.tilConfirmPassword.error = null
        binding.tilCategory.error = null
        binding.tilSpecialty.error = null
    }

    // Adapter interno para exibição das fotos de portfólio selecionadas
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