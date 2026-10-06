package com.tqfapp.android.ui.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.tqfapp.android.R
import com.tqfapp.android.databinding.FragmentProfessionalProfileBinding
import com.tqfapp.android.databinding.ItemPortfolioBinding
import com.tqfapp.android.databinding.ItemReviewBinding

class ProfessionalProfileFragment : Fragment() {

    private var _binding: FragmentProfessionalProfileBinding? = null
    private val binding get() = _binding!!

    // Photo Picker launcher for Profile Avatar
    private val pickAvatarLauncher = registerForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            binding.imgAvatar.setImageURI(uri)
            Toast.makeText(requireContext(), "Foto de perfil atualizada!", Toast.LENGTH_SHORT).show()
        }
    }

    // Photo Picker launcher for Cover Image
    private val pickCoverLauncher = registerForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            binding.imgCover.setImageURI(uri)
            Toast.makeText(requireContext(), "Foto de capa atualizada!", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentProfessionalProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val isOwner = arguments?.getBoolean(ARG_IS_OWNER, false) ?: false
        setupProfileMode(isOwner)

        setupClickListeners()
        setupPortfolio()
        setupReviews()
    }

    private fun setupProfileMode(isOwner: Boolean) {
        if (isOwner) {
            // MODO PRESTADOR (Vendo seu próprio perfil)
            // Exibe edição de perfil e uploads de fotos
            binding.btnEditProfile.visibility = View.VISIBLE
            binding.btnChangeCover.visibility = View.VISIBLE
            binding.btnChangeAvatar.visibility = View.VISIBLE

            // Esconde botões de contratar e chat (pois é o próprio prestador)
            binding.layoutActionButtons.visibility = View.GONE
        } else {
            // MODO CONTRATANTE (Cliente visualizando o prestador)
            // Exibe botões "Contratar agora" e "Chamar no chat"
            binding.layoutActionButtons.visibility = View.VISIBLE
            binding.btnHireNow.visibility = View.VISIBLE
            binding.btnCallChat.visibility = View.VISIBLE

            // Esconde botões de edição
            binding.btnEditProfile.visibility = View.GONE
            binding.btnChangeCover.visibility = View.GONE
            binding.btnChangeAvatar.visibility = View.GONE
        }
    }

    private fun setupClickListeners() {
        binding.btnBack.setOnClickListener {
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }

        binding.btnFavorite.setOnClickListener {
            showToast("Adicionado aos favoritos")
        }

        binding.btnShare.setOnClickListener {
            showToast("Compartilhar perfil")
        }

        // Photo Uploads (Avatar & Cover)
        binding.btnChangeAvatar.setOnClickListener {
            pickAvatarLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }

        binding.imgAvatar.setOnClickListener {
            val isOwner = arguments?.getBoolean(ARG_IS_OWNER, false) ?: false
            if (isOwner) {
                pickAvatarLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            }
        }

        binding.btnChangeCover.setOnClickListener {
            pickCoverLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }

        binding.imgCover.setOnClickListener {
            val isOwner = arguments?.getBoolean(ARG_IS_OWNER, false) ?: false
            if (isOwner) {
                pickCoverLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            }
        }

        // Edit Profile Dialog
        binding.btnEditProfile.setOnClickListener {
            showEditProfileDialog()
        }

        binding.btnHireNow.setOnClickListener {
            showToast("Iniciando contratação de ${binding.txtProfessionalName.text}...")
        }

        binding.btnCallChat.setOnClickListener {
            showToast("Abrindo conversa com ${binding.txtProfessionalName.text}")
        }

        binding.btnSeeAllPortfolio.setOnClickListener {
            showToast("Ver todo o portfólio")
        }

        binding.btnSeeAllReviews.setOnClickListener {
            showToast("Ver todas as avaliações")
        }
    }

    private fun showEditProfileDialog() {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_edit_provider_profile, null)
        val etName = dialogView.findViewById<TextInputEditText>(R.id.etName)
        val etSpecialty = dialogView.findViewById<TextInputEditText>(R.id.etSpecialty)
        val etBio = dialogView.findViewById<TextInputEditText>(R.id.etBio)

        etName?.setText(binding.txtProfessionalName.text)
        etSpecialty?.setText(binding.txtSpecialty.text)
        etBio?.setText(binding.txtBio.text)

        MaterialAlertDialogBuilder(requireContext())
            .setView(dialogView)
            .setPositiveButton("Salvar") { _, _ ->
                val newName = etName?.text?.toString()?.trim().orEmpty()
                val newSpecialty = etSpecialty?.text?.toString()?.trim().orEmpty()
                val newBio = etBio?.text?.toString()?.trim().orEmpty()

                if (newName.isNotEmpty()) binding.txtProfessionalName.text = newName
                if (newSpecialty.isNotEmpty()) binding.txtSpecialty.text = newSpecialty
                if (newBio.isNotEmpty()) binding.txtBio.text = newBio

                showToast("Perfil de prestador atualizado!")
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun setupPortfolio() {
        val portfolioImages = listOf(
            R.drawable.ic_profile,
            R.drawable.ic_profile,
            R.drawable.ic_profile,
            R.drawable.ic_profile
        )

        binding.rvPortfolio.apply {
            layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
            adapter = PortfolioAdapter(portfolioImages) {
                showToast("Visualizando imagem do portfólio")
            }
        }
    }

    private fun setupReviews() {
        val reviews = listOf(
            ReviewItem(
                reviewerName = "Mariana Costa",
                date = "10 de mar. de 2025",
                comment = "Excelente profissional! Resolveu meu problema com muita agilidade. Recomendo!",
                avatarRes = R.drawable.ic_profile
            )
        )

        binding.rvReviews.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = ReviewAdapter(reviews) {
                showToast("Avaliação de ${it.reviewerName}")
            }
        }
    }

    private fun showToast(message: String) {
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val ARG_IS_OWNER = "arg_is_owner"

        fun newInstance(isOwner: Boolean = false): ProfessionalProfileFragment {
            val fragment = ProfessionalProfileFragment()
            val args = Bundle()
            args.putBoolean(ARG_IS_OWNER, isOwner)
            fragment.arguments = args
            return fragment
        }
    }

    // Model for Review
    data class ReviewItem(
        val reviewerName: String,
        val date: String,
        val comment: String,
        val avatarRes: Int
    )

    // Portfolio Adapter
    private inner class PortfolioAdapter(
        private val items: List<Int>,
        private val onItemClick: (Int) -> Unit
    ) : RecyclerView.Adapter<PortfolioAdapter.ViewHolder>() {

        inner class ViewHolder(val binding: ItemPortfolioBinding) : RecyclerView.ViewHolder(binding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val binding = ItemPortfolioBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return ViewHolder(binding)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = items[position]
            holder.binding.imgPortfolio.setImageResource(item)
            holder.binding.root.setOnClickListener { onItemClick(item) }
        }

        override fun getItemCount(): Int = items.size
    }

    // Review Adapter
    private inner class ReviewAdapter(
        private val items: List<ReviewItem>,
        private val onItemClick: (ReviewItem) -> Unit
    ) : RecyclerView.Adapter<ReviewAdapter.ViewHolder>() {

        inner class ViewHolder(val binding: ItemReviewBinding) : RecyclerView.ViewHolder(binding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val binding = ItemReviewBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return ViewHolder(binding)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = items[position]
            holder.binding.apply {
                txtReviewerName.text = item.reviewerName
                txtReviewDate.text = item.date
                txtReviewComment.text = item.comment
                imgReviewerAvatar.setImageResource(item.avatarRes)
                root.setOnClickListener { onItemClick(item) }
            }
        }

        override fun getItemCount(): Int = items.size
    }
}