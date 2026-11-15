package com.noorazan.app.ui.prayertimes

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.noorazan.app.R
import com.noorazan.app.databinding.ItemPrayerTimeBinding
import com.noorazan.app.model.PrayerName
import com.noorazan.app.model.PrayerTimeItem

class PrayerTimesAdapter(
    private val onToggleAzan: (PrayerName, Boolean) -> Unit
) : RecyclerView.Adapter<PrayerTimesAdapter.PrayerViewHolder>() {

    private var items: List<PrayerTimeItem> = emptyList()

    fun submitList(newItems: List<PrayerTimeItem>) {
        items = newItems
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PrayerViewHolder {
        val binding = ItemPrayerTimeBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return PrayerViewHolder(binding)
    }

    override fun onBindViewHolder(holder: PrayerViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    inner class PrayerViewHolder(
        private val binding: ItemPrayerTimeBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: PrayerTimeItem) {
            binding.tvPrayerName.text = item.prayer.displayName
            binding.tvPrayerArabic.text = item.prayer.arabicName
            binding.tvPrayerTime.text = item.formattedTime

            if (item.isNext) {
                binding.cardPrayer.strokeWidth = 4
                binding.cardPrayer.strokeColor = Color.parseColor("#1B5E20")
                binding.cardPrayer.setCardBackgroundColor(Color.parseColor("#E8F5E9"))
                binding.tvNextBadge.visibility = View.VISIBLE
            } else {
                binding.cardPrayer.strokeWidth = 1
                binding.cardPrayer.strokeColor = Color.parseColor("#E0E6E3")
                binding.cardPrayer.setCardBackgroundColor(Color.WHITE)
                binding.tvNextBadge.visibility = View.GONE
            }

            if (item.prayer.isAzanPrayer) {
                binding.btnToggleAlarm.visibility = View.VISIBLE
                binding.btnToggleAlarm.setImageResource(
                    if (item.isEnabled) R.drawable.ic_volume_on else R.drawable.ic_volume_off
                )
                binding.btnToggleAlarm.setOnClickListener {
                    onToggleAzan(item.prayer, !item.isEnabled)
                }
            } else {
                binding.btnToggleAlarm.visibility = View.INVISIBLE
            }
        }
    }
}
