package com.chate.thesis

import android.graphics.Color
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class EventLogAdapter(private var logs: List<LogEntry>) : RecyclerView.Adapter<EventLogAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val logText: TextView = view.findViewById(R.id.logText)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_event_log, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val log = logs[position]
        val context = holder.itemView.context
        
        val colorOnSurface = com.google.android.material.color.MaterialColors.getColor(holder.itemView, com.google.android.material.R.attr.colorOnSurface)
        val colorOnSurfaceVariant = com.google.android.material.color.MaterialColors.getColor(holder.itemView, com.google.android.material.R.attr.colorOnSurfaceVariant)
        val colorSurfaceVariant = com.google.android.material.color.MaterialColors.getColor(holder.itemView, com.google.android.material.R.attr.colorSurfaceVariant)
        
        val ssb = SpannableStringBuilder()

        // 1. Level (D, V, W, E based on action)
        val actionStr = log.action ?: ""
        val (levelChar, levelColor) = when (actionStr) {
            "ALLOW" -> Pair("V", ContextCompat.getColor(context, R.color.risk_allow))
            "WARN" -> Pair("W", ContextCompat.getColor(context, R.color.risk_warn))
            "STEP_UP_AUTH" -> Pair("E", ContextCompat.getColor(context, R.color.risk_step_up))
            "BLOCK_SESSION" -> Pair("F", ContextCompat.getColor(context, R.color.risk_block))
            else -> Pair("D", colorOnSurfaceVariant)
        }

        val startLevel = ssb.length
        ssb.append("$levelChar ")
        ssb.setSpan(ForegroundColorSpan(levelColor), startLevel, ssb.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)

        // 2. Timestamp
        val timeStr = try {
            val instant = Instant.parse(log.timestamp)
            val formatter = DateTimeFormatter.ofPattern("HH:mm:ss.SSS").withZone(ZoneId.systemDefault())
            formatter.format(instant)
        } catch (e: Exception) {
            log.timestamp.take(12)
        }
        val startTime = ssb.length
        ssb.append("$timeStr ")
        ssb.setSpan(ForegroundColorSpan(colorOnSurfaceVariant), startTime, ssb.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)

        // 3. Tag (Event Type)
        val tagStr = log.eventType.padEnd(10).take(20)
        val startTag = ssb.length
        ssb.append(" $tagStr ")
        ssb.setSpan(BackgroundColorSpan(colorSurfaceVariant), startTag, ssb.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        ssb.setSpan(ForegroundColorSpan(colorOnSurface), startTag, ssb.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)

        ssb.append(" ")

        // 4. Message (Risk Score + Action + Payload)
        val riskScoreFormatted = log.riskScore?.let { String.format("%.3f", it) } ?: "N/A"
        val message = "Risk: $riskScoreFormatted | Action: $actionStr | ${log.payload}"
        val startMsg = ssb.length
        ssb.append(message)
        ssb.setSpan(ForegroundColorSpan(colorOnSurface), startMsg, ssb.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)

        holder.logText.text = ssb
    }

    override fun getItemCount() = logs.size

    fun updateLogs(newLogs: List<LogEntry>) {
        logs = newLogs
        notifyDataSetChanged()
    }
}
