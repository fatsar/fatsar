package com.fatsar.toplanti.ui

import android.content.Context
import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.fatsar.toplanti.R
import com.fatsar.toplanti.databinding.ItemDateHeaderBinding
import com.fatsar.toplanti.databinding.ItemInsightBinding
import com.fatsar.toplanti.model.Insight
import com.fatsar.toplanti.model.InsightType

/**
 * Notlar sekmesi: kararlar, önemli notlar, açık sorular ve riskler tür
 * başlıklarıyla gruplanır (FR-021); her madde kaynak rengiyle işaretlenir
 * ve dokununca kaynağından dinlenir (FR-022).
 */
class InsightAdapter(
    private val onClick: (Insight) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private sealed class Row {
        data class Header(val label: String) : Row()
        data class Item(val insight: Insight) : Row()
    }

    private val rows = mutableListOf<Row>()

    fun submit(insights: List<Insight>, context: Context) {
        rows.clear()
        val order = listOf(
            InsightType.DECISION to context.getString(R.string.section_decisions),
            InsightType.IMPORTANT_NOTE to context.getString(R.string.section_notes),
            InsightType.QUESTION to context.getString(R.string.section_questions),
            InsightType.RISK to context.getString(R.string.section_risks)
        )
        for ((type, label) in order) {
            val group = insights.filter { it.type == type }
            if (group.isNotEmpty()) {
                rows.add(Row.Header(label))
                group.forEach { rows.add(Row.Item(it)) }
            }
        }
        notifyDataSetChanged()
    }

    override fun getItemViewType(position: Int): Int =
        if (rows[position] is Row.Header) 0 else 1

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == 0) HeaderVH(ItemDateHeaderBinding.inflate(inflater, parent, false))
        else ItemVH(ItemInsightBinding.inflate(inflater, parent, false))
    }

    override fun getItemCount(): Int = rows.size

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val row = rows[position]) {
            is Row.Header -> (holder as HeaderVH).binding.headerText.text = row.label
            is Row.Item -> (holder as ItemVH).bind(row.insight)
        }
    }

    private class HeaderVH(val binding: ItemDateHeaderBinding) : RecyclerView.ViewHolder(binding.root)

    private inner class ItemVH(val binding: ItemInsightBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(insight: Insight) {
            val ctx = binding.root.context
            binding.insightText.text = insight.content
            binding.confidenceText.text = ctx.getString(
                R.string.confidence_short, (insight.confidence * 100).toInt()
            )
            binding.accentBar.backgroundTintList = ColorStateList.valueOf(
                ContextCompat.getColor(ctx, accentColor(insight.type))
            )
            // Kaynak bağlantısı yoksa "dinle" ipucu gösterilmez
            val hasSource = insight.sourceSegmentIds.isNotEmpty()
            binding.sourceIcon.visibility = if (hasSource) View.VISIBLE else View.GONE
            binding.sourceHint.visibility = if (hasSource) View.VISIBLE else View.GONE
            binding.root.setOnClickListener { onClick(insight) }
        }

        private fun accentColor(type: InsightType): Int = when (type) {
            InsightType.DECISION -> R.color.ok_600
            InsightType.IMPORTANT_NOTE -> R.color.brand_600
            InsightType.QUESTION -> R.color.accent_600
            InsightType.RISK -> R.color.rec_600
        }
    }
}
