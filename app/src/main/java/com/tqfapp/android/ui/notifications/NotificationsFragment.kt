package com.tqfapp.android.ui.notifications

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.tqfapp.android.R
import com.tqfapp.android.business.ServiceStatus
import com.tqfapp.android.data.model.Service
import com.tqfapp.android.data.repository.ServiceRepository
import com.tqfapp.android.data.repository.UserRepository
import com.tqfapp.android.ui.request.AvaliarSolicitacaoActivity
import com.tqfapp.android.ui.request.SugestaoPrestadoresActivity
import com.tqfapp.android.utils.UserSessionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Notificações derivadas do status real das solicitações (sem itens fixos e sem chat interno).
 * Push remoto (FCM) ainda é uma lacuna de backend; ver REGRAS_DE_NEGOCIO_E_FLUXO.md.
 */
class NotificationsFragment : Fragment() {

    private val notificationsList = mutableListOf<NotificationItem>()
    private var adapter: NotificationAdapter? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_notifications, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val btnBack = view.findViewById<View>(R.id.btnBackNotifications)
        val btnClearAll = view.findViewById<View>(R.id.btnClearAllNotifications)
        val rvNotifications = view.findViewById<RecyclerView>(R.id.rvNotifications)

        btnBack?.setOnClickListener {
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }

        adapter = NotificationAdapter(notificationsList) { item ->
            item.isUnread = false
            item.onOpen?.invoke()
        }

        rvNotifications?.layoutManager = LinearLayoutManager(requireContext())
        rvNotifications?.adapter = adapter

        btnClearAll?.setOnClickListener {
            notificationsList.clear()
            renderList(view)
        }

        loadNotifications(view)
    }

    private fun loadNotifications(view: View) {
        val appContext = requireContext().applicationContext
        val isProvider = UserSessionManager.isProviderUser(appContext)
        viewLifecycleOwner.lifecycleScope.launch {
            val services = withContext(Dispatchers.IO) {
                try {
                    val user = UserRepository(appContext).getCurrentUser() ?: return@withContext emptyList()
                    val repository = ServiceRepository(appContext)
                    if (isProvider) {
                        repository.getProviderRequests(user.id, user.category)
                    } else {
                        repository.getClientRequests(user.id).filter { it.status != ServiceStatus.PENDENTE }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    emptyList()
                }
            }

            if (this@NotificationsFragment.view == null) return@launch
            val seenRefused = UserSessionManager.getSeenRefusedRequestIds(appContext)
            notificationsList.clear()
            notificationsList.addAll(services.mapNotNull { toNotification(it, seenRefused) })
            renderList(view)
        }
    }

    private fun toNotification(service: Service, seenRefused: Set<String>): NotificationItem? {
        val who = service.providerName.ifBlank { "O prestador" }
        return when (service.status) {
            ServiceStatus.PENDENTE -> NotificationItem(
                id = service.id,
                title = "Nova solicitação",
                message = "${service.clientName.ifBlank { "Um cliente" }} solicitou: ${service.title}",
                time = service.date,
                iconRes = R.drawable.ic_briefcase,
                isUnread = true,
                onOpen = { startActivity(AvaliarSolicitacaoActivity.newIntent(requireContext(), service)) }
            )
            ServiceStatus.EM_ANDAMENTO -> NotificationItem(
                id = service.id,
                title = "Solicitação aceita",
                message = "$who aceitou \"${service.title}\" e entrará em contato pelo seu WhatsApp.",
                time = service.date,
                iconRes = R.drawable.ic_check_circle,
                isUnread = false
            )
            ServiceStatus.RECUSADO -> NotificationItem(
                id = service.id,
                title = "Prestador indisponível",
                message = "$who não pode atender \"${service.title}\". Toque para ver outros prestadores de ${service.category}.",
                time = service.date,
                iconRes = R.drawable.ic_bell,
                isUnread = service.id !in seenRefused,
                onOpen = { startActivity(SugestaoPrestadoresActivity.newIntent(requireContext(), service)) }
            )
            ServiceStatus.CONCLUIDO -> NotificationItem(
                id = service.id,
                title = "Serviço concluído",
                message = "\"${service.title}\" foi concluído. Avalie o atendimento no seu Histórico.",
                time = service.date,
                iconRes = R.drawable.ic_star,
                isUnread = false
            )
            ServiceStatus.CANCELADO -> null
        }
    }

    private fun renderList(view: View) {
        adapter?.notifyDataSetChanged()
        val isEmpty = notificationsList.isEmpty()
        view.findViewById<View>(R.id.rvNotifications)?.visibility = if (isEmpty) View.GONE else View.VISIBLE
        view.findViewById<View>(R.id.layoutEmptyNotifications)?.visibility = if (isEmpty) View.VISIBLE else View.GONE
    }

    data class NotificationItem(
        val id: String,
        val title: String,
        val message: String,
        val time: String,
        val iconRes: Int,
        var isUnread: Boolean,
        val onOpen: (() -> Unit)? = null
    )

    private inner class NotificationAdapter(
        private val items: List<NotificationItem>,
        private val onItemClick: (NotificationItem) -> Unit
    ) : RecyclerView.Adapter<NotificationAdapter.ViewHolder>() {

        inner class ViewHolder(val view: View) : RecyclerView.ViewHolder(view) {
            val txtTitle: TextView = view.findViewById(R.id.txtNotificationTitle)
            val txtMessage: TextView = view.findViewById(R.id.txtNotificationMessage)
            val txtTime: TextView = view.findViewById(R.id.txtNotificationTime)
            val imgIcon: ImageView = view.findViewById(R.id.imgNotificationIcon)
            val viewUnreadDot: View = view.findViewById(R.id.viewUnreadDot)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_notification, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = items[position]
            holder.txtTitle.text = item.title
            holder.txtMessage.text = item.message
            holder.txtTime.text = item.time
            holder.imgIcon.setImageResource(item.iconRes)
            holder.viewUnreadDot.visibility = if (item.isUnread) View.VISIBLE else View.GONE

            holder.view.setOnClickListener {
                holder.viewUnreadDot.visibility = View.GONE
                onItemClick(item)
            }
        }

        override fun getItemCount(): Int = items.size
    }
}