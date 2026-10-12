package com.chate.thesis

import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.google.gson.JsonParser
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class EventLogAdapter(private var logs: List<LogEntry>) : RecyclerView.Adapter<EventLogAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val logActionBadge: TextView = view.findViewById(R.id.logActionBadge)
        val logEventType: TextView = view.findViewById(R.id.logEventType)
        val logTimestamp: TextView = view.findViewById(R.id.logTimestamp)
        val logDetails: TextView = view.findViewById(R.id.logDetails)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_event_log, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val log = logs[position]
        val context = holder.itemView.context

        val actionStr = log.action ?: "LOG"
        holder.logActionBadge.text = actionStr

        val (textColor, bgColor) = when (actionStr) {
            "ALLOW" -> Pair(R.color.risk_allow, R.color.risk_allow_bg)
            "WARN" -> Pair(R.color.risk_warn, R.color.risk_warn_bg)
            "STEP_UP_AUTH" -> Pair(R.color.risk_step_up, R.color.risk_step_up_bg)
            "BLOCK_SESSION" -> Pair(R.color.risk_block, R.color.risk_block_bg)
            else -> Pair(R.color.text_secondary, R.color.surface_container_high)
        }

        holder.logActionBadge.setTextColor(ContextCompat.getColor(context, textColor))
        val bgDrawable = holder.logActionBadge.background
        if (bgDrawable is GradientDrawable) {
            bgDrawable.setColor(ContextCompat.getColor(context, bgColor))
        }

        holder.logEventType.text = log.eventType

        val timeStr = try {
            val instant = Instant.parse(log.timestamp)
            val formatter = DateTimeFormatter.ofPattern("HH:mm:ss.SSS").withZone(ZoneId.systemDefault())
            formatter.format(instant)
        } catch (e: Exception) {
            log.timestamp.take(12)
        }
        holder.logTimestamp.text = timeStr

        // Parse payload json if possible
        var parsedDetails = ""
        try {
            val jsonObject = JsonParser.parseString(log.payload).asJsonObject
            val composite = jsonObject.get("composite")?.asString?.toDoubleOrNull()
            val ocsvm = jsonObject.get("ocsvm")?.asString?.toDoubleOrNull()
            val mahal = jsonObject.get("mahal")?.asString?.toDoubleOrNull()
            if (composite != null && ocsvm != null && mahal != null) {
                parsedDetails = String.format("Risk: %.3f  |  OCSVM: %.3f  |  Mahal: %.3f", composite, ocsvm, mahal)
            }
        } catch (_: Exception) {}

        if (parsedDetails.isEmpty()) {
            val riskFormatted = log.riskScore?.let { String.format("Risk: %.3f  |  ", it) } ?: ""
            parsedDetails = "$riskFormatted${log.payload}"
        }

        holder.logDetails.text = parsedDetails
    }

    override fun getItemCount() = logs.size

    fun updateLogs(newLogs: List<LogEntry>) {
        logs = newLogs
        notifyDataSetChanged()
    }
}
