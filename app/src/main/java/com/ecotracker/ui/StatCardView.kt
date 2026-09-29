package com.ecotracker.ui

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import androidx.constraintlayout.widget.ConstraintLayout
import com.ecotracker.R
import com.ecotracker.databinding.ViewStatCardBinding

/**
 * Compact reusable metric card used in the "This Week" impact snapshot
 * (e.g. Activities / CO₂ Saved / Water Saved). Configurable via XML attrs
 * so new stats can be added without duplicating layout code, and updatable
 * at runtime via [setValue] once real data comes in.
 */
class StatCardView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : ConstraintLayout(context, attrs, defStyleAttr) {

    private val binding = ViewStatCardBinding.inflate(LayoutInflater.from(context), this)

    init {
        context.theme.obtainStyledAttributes(attrs, R.styleable.StatCardView, 0, 0).apply {
            try {
                val iconRes = getResourceId(R.styleable.StatCardView_statIcon, 0)
                val iconBgRes = getResourceId(R.styleable.StatCardView_statIconBackground, 0)
                val value = getString(R.styleable.StatCardView_statValue)
                val label = getString(R.styleable.StatCardView_statLabel)

                if (iconRes != 0) binding.ivStatIcon.setImageResource(iconRes)
                if (iconBgRes != 0) binding.ivStatIcon.setBackgroundResource(iconBgRes)
                if (value != null) binding.tvStatValue.text = value
                binding.tvStatLabel.text = label
            } finally {
                recycle()
            }
        }
    }

    fun setValue(value: String) {
        binding.tvStatValue.text = value
    }
}
