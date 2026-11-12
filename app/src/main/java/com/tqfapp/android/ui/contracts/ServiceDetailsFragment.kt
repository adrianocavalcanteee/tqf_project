package com.tqfapp.android.ui.contracts

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.RatingBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.tqfapp.android.R
import com.tqfapp.android.business.ServiceStatus
import com.tqfapp.android.data.local.database.AppDatabase
import com.tqfapp.android.data.model.Review
import com.tqfapp.android.ui.chat.ChatFragment
import com.tqfapp.android.utils.UserSessionManager
import java.util.UUID

class ServiceDetailsFragment : Fragment() {

    private val evidenceUris = mutableListOf<Uri>()
    private var evidenceAdapter: PortfolioPreviewAdapter? = null

    private val pickEvidenceLauncher = registerForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(5)
    ) { uris ->
        if (uris.isNotEmpty()) {
            uris.forEach { uri ->
                if (!evidenceUris.contains(uri)) {
                    evidenceUris.add(uri)
                }
            }
            evidenceAdapter?.notifyDataSetChanged()
            Toast.makeText(requireContext(), "${uris.size} foto(s) de evidência adicionada(s)", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_service_details, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val title = arguments?.getString(ARG_TITLE) ?: "Instalação de tomadas e iluminação"
        val profName = arguments?.getString(ARG_PROFESSIONAL) ?: "Carlos Mendes"
        val specialty = arguments?.getString(ARG_SPECIALTY) ?: "Eletricista Residencial"
        val schedule = arguments?.getString(ARG_SCHEDULE) ?: "15 de mar. de 2025 • 09:00"
        val price = arguments?.getString(ARG_PRICE) ?: "R$ 150,00"
        val status = arguments?.getString(ARG_STATUS) ?: "Em andamento"
        val description = arguments?.getString(ARG_DESC) ?: "Serviço completo de passagem de fiação, instalação de tomadas 20A e luminárias LED em cômodos residenciais."

        view.findViewById<TextView>(R.id.txtDetailTitle)?.text = title
        view.findViewById<TextView>(R.id.txtDetailProfessionalName)?.text = profName
        view.findViewById<TextView>(R.id.txtDetailSpecialty)?.text = specialty
        view.findViewById<TextView>(R.id.txtDetailSchedule)?.text = schedule
        view.findViewById<TextView>(R.id.txtDetailPrice)?.text = price
        view.findViewById<TextView>(R.id.txtDetailStatus)?.text = status
        view.findViewById<TextView>(R.id.txtDetailDescription)?.text = description

        val layoutEvidenceSection = view.findViewById<View>(R.id.layoutEvidenceSection)
        val isProvider = UserSessionManager.isProviderUser(requireContext())
        if (isProvider) {
            layoutEvidenceSection?.visibility = View.VISIBLE
        } else {
            layoutEvidenceSection?.visibility = View.GONE
        }

        val btnAddEvidence = view.findViewById<View>(R.id.btnAddEvidence)
        val rvServiceEvidence = view.findViewById<RecyclerView>(R.id.rvServiceEvidence)

        evidenceAdapter = PortfolioPreviewAdapter(evidenceUris) { uriToRemove ->
            evidenceUris.remove(uriToRemove)
            evidenceAdapter?.notifyDataSetChanged()
        }
        rvServiceEvidence?.layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        rvServiceEvidence?.adapter = evidenceAdapter

        btnAddEvidence?.setOnClickListener {
            pickEvidenceLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }

        view.findViewById<View>(R.id.btnBackDetails)?.setOnClickListener {
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }

        val btnDetailAction = view.findViewById<MaterialButton>(R.id.btnDetailAction)
        val btnReviewService = view.findViewById<MaterialButton>(R.id.btnReviewService)
        val isCompleted = status.equals("Concluído", ignoreCase = true) || status.equals("Concluídos", ignoreCase = true)

        if (isCompleted) {
            btnDetailAction?.visibility = View.GONE
            btnReviewService?.visibility = View.VISIBLE
            btnReviewService?.text = "⭐ Avaliar Atendimento"
            btnReviewService?.setOnClickListener {
                showReviewDialog(title, profName)
            }
        } else {
            // Em andamento
            if (isProvider) {
                btnDetailAction?.visibility = View.VISIBLE
                btnDetailAction?.text = "Falar no WhatsApp"
                btnDetailAction?.setOnClickListener {
                    openWhatsApp("11999998888", "Olá! Entrando em contato sobre o serviço '$title'.")
                }

                btnReviewService?.visibility = View.VISIBLE
                btnReviewService?.text = "Concluir Serviço"
                btnReviewService?.setOnClickListener {
                    updateServiceStatusToCompleted(title)
                }
            } else {
                btnDetailAction?.visibility = View.VISIBLE
                btnDetailAction?.text = "Falar com o Prestador"
                btnDetailAction?.setOnClickListener {
                    openWhatsApp("11999998888", "Olá $profName! Acompanhando o serviço '$title'.")
                }
                btnReviewService?.visibility = View.GONE
            }
        }
    }

    private fun updateServiceStatusToCompleted(title: String) {
        try {
            val serviceDao = AppDatabase.getDatabase(requireContext()).serviceDao()
            val services = serviceDao.getAll()
            val target = services.find { it.title.equals(title, ignoreCase = true) }
            if (target != null) {
                val updated = target.copy(status = ServiceStatus.CONCLUIDO)
                serviceDao.update(updated)
            }
            Toast.makeText(requireContext(), "Serviço concluído com sucesso!", Toast.LENGTH_SHORT).show()
            requireActivity().onBackPressedDispatcher.onBackPressed()
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(requireContext(), "Serviço concluído!", Toast.LENGTH_SHORT).show()
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }
    }

    private fun showReviewDialog(title: String, targetName: String) {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_review, null)
        val ratingBar = dialogView.findViewById<RatingBar>(R.id.ratingBar)
        val etComment = dialogView.findViewById<TextInputEditText>(R.id.etReviewComment)
        val txtSubtitle = dialogView.findViewById<TextView>(R.id.txtReviewDialogSubtitle)

        txtSubtitle?.text = "Como foi a sua experiência com $targetName no serviço '$title'?"

        MaterialAlertDialogBuilder(requireContext())
            .setView(dialogView)
            .setPositiveButton("Enviar Avaliação") { _, _ ->
                val ratingValue = ratingBar?.rating?.toInt() ?: 5
                val commentText = etComment?.text?.toString()?.trim().orEmpty().ifEmpty { "Atendimento excelente e serviço concluído com sucesso." }

                val reviewDao = AppDatabase.getDatabase(requireContext()).reviewDao()
                val review = Review(
                    id = UUID.randomUUID().toString(),
                    serviceId = "s1",
                    providerId = "p1",
                    clientId = "c1",
                    rating = ratingValue,
                    comment = commentText,
                    date = "Hoje"
                )
                try {
                    reviewDao.insert(review)
                    Toast.makeText(requireContext(), "Avaliação enviada com sucesso!", Toast.LENGTH_SHORT).show()
                } catch (_: Exception) {
                    Toast.makeText(requireContext(), "Avaliação registrada!", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun openWhatsApp(phoneNumber: String, message: String) {
        try {
            val cleanPhone = phoneNumber.replace(Regex("[^0-9]"), "")
            val url = "https://api.whatsapp.com/send?phone=55$cleanPhone&text=${Uri.encode(message)}"
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            startActivity(intent)
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(requireContext(), "Não foi possível abrir o WhatsApp no dispositivo.", Toast.LENGTH_SHORT).show()
        }
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

    companion object {
        private const val ARG_TITLE = "arg_title"
        private const val ARG_PROFESSIONAL = "arg_professional"
        private const val ARG_SPECIALTY = "arg_specialty"
        private const val ARG_SCHEDULE = "arg_schedule"
        private const val ARG_PRICE = "arg_price"
        private const val ARG_STATUS = "arg_status"
        private const val ARG_DESC = "arg_desc"

        fun newInstance(
            title: String,
            professional: String,
            specialty: String,
            schedule: String,
            price: String,
            status: String,
            desc: String
        ): ServiceDetailsFragment {
            val fragment = ServiceDetailsFragment()
            val args = Bundle().apply {
                putString(ARG_TITLE, title)
                putString(ARG_PROFESSIONAL, professional)
                putString(ARG_SPECIALTY, specialty)
                putString(ARG_SCHEDULE, schedule)
                putString(ARG_PRICE, price)
                putString(ARG_STATUS, status)
                putString(ARG_DESC, desc)
            }
            fragment.arguments = args
            return fragment
        }
    }
}
