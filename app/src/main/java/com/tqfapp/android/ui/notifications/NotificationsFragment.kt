package com.tqfapp.android.ui.notifications

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.tqfapp.android.R

class NotificationsFragment : Fragment() {

    private val notificationsList = mutableListOf(
        NotificationItem(
            id = "1",
            title = "Proposta Aceita",
            message = "Sua proposta para o serviço de Instalação de Ar-Condicionado foi aceita pelo cliente.",
            time = "Há 10 min",
            iconRes = R.drawable.ic_check_circle,
            isUnread = true
        ),
        NotificationItem(
            id = "2",
            title = "Nova Mensagem no Chat",
            message = "Mariana Costa enviou uma nova mensagem: 'Qual o horário previsto para a visita?'",
            time = "Há 45 min",
            iconRes = R.drawable.ic_chat,
            isUnread = true
        ),
        NotificationItem(
            id = "3",
            title = "Serviço Concluído",
            message = "O serviço de Reparo Hidráulico foi marcado como concluído. Deixe uma avaliação!",
            time = "Ontem",
            iconRes = R.drawable.ic_briefcase,
            isUnread = false
        )
    )

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
        val layoutEmpty = view.findViewById<View>(R.id.layoutEmptyNotifications)

        btnBack?.setOnClickListener {
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }

        val adapter = NotificationAdapter(notificationsList) { item ->
            item.isUnread = false
            Toast.makeText(requireContext(), item.title, Toast.LENGTH_SHORT).show()
        }

        rvNotifications?.layoutManager = LinearLayoutManager(requireContext())
        rvNotifications?.adapter = adapter

        btnClearAll?.setOnClickListener {
            notificationsList.clear()
            adapter.notifyDataSetChanged()
            rvNotifications?.visibility = View.GONE
            layoutEmpty?.visibility = View.VISIBLE
            Toast.makeText(requireContext(), "Todas as notificações foram limpas", Toast.LENGTH_SHORT).show()
        }
    }

    data class NotificationItem(
        val id: String,
        val title: String,
        val message: String,
        val time: String,
        val iconRes: Int,
        var isUnread: Boolean
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