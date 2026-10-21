package com.ecotracker.ui

import android.content.Context
import android.util.AttributeSet
import android.view.Gravity
import android.view.LayoutInflater
import android.widget.FrameLayout
import androidx.core.content.ContextCompat
import com.ecotracker.R
import com.ecotracker.databinding.ViewEcoToggleBinding

/**
 * Custom toggle switch designed for EcoTracker that displays "ON" and "OFF"
 * clearly inside the toggle track, with smooth thumb sliding animation.
 */
class EcoToggleSwitch @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private val binding: ViewEcoToggleBinding =
        ViewEcoToggleBinding.inflate(LayoutInflater.from(context), this, true)

    private var _isChecked: Boolean = false
    var onCheckedChangeListener: ((Boolean) -> Unit)? = null

    val isChecked: Boolean
        get() = _isChecked

    init {
        isClickable = true
        isFocusable = true

        setOnClickListener {
            setChecked(!_isChecked, notify = true, animate = true)
        }

        post {
            applyState(_isChecked, animate = false)
        }
    }

    fun setChecked(checked: Boolean, notify: Boolean = true, animate: Boolean = true) {
        if (_isChecked == checked) return
        _isChecked = checked
        applyState(checked, animate)
        if (notify) {
            onCheckedChangeListener?.invoke(checked)
        }
    }

    private fun applyState(checked: Boolean, animate: Boolean) {
        val density = resources.displayMetrics.density
        // Container width 68dp, thumb 26dp with 4dp margin. Max translation is 34dp.
        val maxTranslationX = 34f * density
        val targetTranslationX = if (checked) maxTranslationX else 0f

        if (animate) {
            binding.viewThumb.animate()
                .translationX(targetTranslationX)
                .setDuration(220)
                .start()
        } else {
            binding.viewThumb.translationX = targetTranslationX
        }

        if (checked) {
            binding.viewTrack.setBackgroundResource(R.drawable.bg_toggle_track_on)
            binding.tvToggleText.text = context.getString(R.string.switch_text_on)
            binding.tvToggleText.setTextColor(ContextCompat.getColor(context, R.color.white))

            val params = binding.tvToggleText.layoutParams as LayoutParams
            params.gravity = Gravity.CENTER_VERTICAL or Gravity.START
            params.marginStart = (12 * density).toInt()
            params.marginEnd = 0
            binding.tvToggleText.layoutParams = params

            contentDescription = "Notifications enabled. Toggle to turn off."
        } else {
            binding.viewTrack.setBackgroundResource(R.drawable.bg_toggle_track_off)
            binding.tvToggleText.text = context.getString(R.string.switch_text_off)
            binding.tvToggleText.setTextColor(ContextCompat.getColor(context, R.color.text_muted))

            val params = binding.tvToggleText.layoutParams as LayoutParams
            params.gravity = Gravity.CENTER_VERTICAL or Gravity.END
            params.marginStart = 0
            params.marginEnd = (10 * density).toInt()
            binding.tvToggleText.layoutParams = params

            contentDescription = "Notifications disabled. Toggle to turn on."
        }
    }
}
