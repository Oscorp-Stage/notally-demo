package com.omgodse.notally.recyclerview.viewholder

import android.animation.ValueAnimator
import android.graphics.drawable.Drawable
import android.util.Log
import android.util.TypedValue
import android.view.View
import android.widget.TextView
import androidx.core.animation.doOnEnd
import androidx.core.content.ContextCompat
import androidx.core.view.children
import androidx.core.view.isVisible
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import com.omgodse.notally.R
import com.omgodse.notally.databinding.RecyclerBaseNoteBinding
import com.omgodse.notally.miscellaneous.Operations
import com.omgodse.notally.miscellaneous.applySpans
import com.omgodse.notally.preferences.DateFormat
import com.omgodse.notally.preferences.TextSize
import com.omgodse.notally.recyclerview.ItemListener
import com.omgodse.notally.room.BaseNote
import com.omgodse.notally.room.Color
import com.omgodse.notally.room.Image
import com.omgodse.notally.room.ListItem
import com.omgodse.notally.room.SpanRepresentation
import com.omgodse.notally.room.Type
import org.ocpsoft.prettytime.PrettyTime
import java.io.File
import java.util.Date

class BaseNoteVH(
    private val binding: RecyclerBaseNoteBinding,
    private val dateFormat: String,
    private val textSize: String,
    private val maxItems: Int,
    maxLines: Int,
    maxTitle: Int,
    listener: ItemListener,
    private val prettyTime: PrettyTime,
    private val formatter: java.text.DateFormat,
) : RecyclerView.ViewHolder(binding.root) {

    // Add properties to track animation state
    private var currentElevation = 0f
    private val defaultElevation = 0f
    private val selectedElevation = 16f
    private var elevationAnimator: ValueAnimator? = null
    private val animationDuration = 300L
    private val interpolator = FastOutSlowInInterpolator()

    init {
        val title = TextSize.getDisplayTitleSize(textSize)
        val body = TextSize.getDisplayBodySize(textSize)

        binding.Title.setTextSize(TypedValue.COMPLEX_UNIT_SP, title)
        binding.Date.setTextSize(TypedValue.COMPLEX_UNIT_SP, body)
        binding.Note.setTextSize(TypedValue.COMPLEX_UNIT_SP, body)

        binding.LinearLayout.children.forEach { view ->
            view as TextView
            view.setTextSize(TypedValue.COMPLEX_UNIT_SP, body)
        }

        binding.Title.maxLines = maxTitle
        binding.Note.maxLines = maxLines

        binding.root.setOnClickListener {
            listener.onClick(adapterPosition)
        }

        binding.root.setOnLongClickListener {
            listener.onLongClick(adapterPosition)
            return@setOnLongClickListener true
        }
    }

    fun updateCheck(checked: Boolean) {
        binding.root.isChecked = checked
        animateSelectionState(checked)
    }

    private fun animateSelectionState(selected: Boolean) {
        Log.d("CardAnimation", "Animation started - Selected: $selected, Target elevation: ${if (selected) selectedElevation else defaultElevation}")
        // Cancel any ongoing animations
        elevationAnimator?.cancel()

        // Calculate target values
        val targetElevation = if (selected) selectedElevation else defaultElevation

        // Create animator for elevation
        elevationAnimator = ValueAnimator.ofFloat(currentElevation, targetElevation).apply {
            duration = animationDuration
            interpolator = this@BaseNoteVH.interpolator

            addUpdateListener { animator ->
                val value = animator.animatedValue as Float
                currentElevation = value
                binding.root.cardElevation = value
            }

            doOnEnd {
                currentElevation = targetElevation
                Log.d("CardAnimation", "Animation completed - Current elevation: $currentElevation")
            }

            start()
        }

        // Apply a subtle scale animation when selected
        if (selected) {
            binding.root.animate()
                .scaleX(1.05f)
                .scaleY(1.05f)
                .setDuration(animationDuration)
                .setInterpolator(interpolator)
                .start()
        } else {
            binding.root.animate()
                .scaleX(1.0f)
                .scaleY(1.0f)
                .setDuration(animationDuration)
                .setInterpolator(interpolator)
                .start()
        }
    }

    fun bind(baseNote: BaseNote, mediaRoot: File?, checked: Boolean) {
        // Update check state without animation on initial bind
        binding.root.isChecked = checked

        // Set the current elevation without animation when binding
        if (checked) {
            currentElevation = selectedElevation
            binding.root.cardElevation = selectedElevation
            binding.root.scaleX = 1.02f
            binding.root.scaleY = 1.02f
        } else {
            currentElevation = defaultElevation
            binding.root.cardElevation = defaultElevation
            binding.root.scaleX = 1.0f
            binding.root.scaleY = 1.0f
        }

        when (baseNote.type) {
            Type.NOTE -> bindNote(baseNote.body, baseNote.spans)
            Type.LIST -> bindList(baseNote.items)
        }

        setDate(baseNote.timestamp)
        setColor(baseNote.color)
        setImages(baseNote.images, mediaRoot)

        binding.Title.text = baseNote.title
        binding.Title.isVisible = baseNote.title.isNotEmpty()

        Operations.bindLabels(binding.LabelGroup, baseNote.labels, textSize)

        if (isEmpty(baseNote)) {
            binding.Title.setText(getEmptyMessage(baseNote))
            binding.Title.visibility = View.VISIBLE
        }
    }

    // Rest of the code remains the same...
    private fun bindNote(body: String, spans: List<SpanRepresentation>) {
        binding.LinearLayout.visibility = View.GONE

        binding.Note.text = body.applySpans(spans)
        binding.Note.isVisible = body.isNotEmpty()
    }

    private fun bindList(items: List<ListItem>) {
        binding.Note.visibility = View.GONE

        if (items.isEmpty()) {
            binding.LinearLayout.visibility = View.GONE
        } else {
            binding.LinearLayout.visibility = View.VISIBLE

            val filteredList = items.take(maxItems)
            binding.LinearLayout.children.forEachIndexed { index, view ->
                if (view.id != R.id.ItemsRemaining) {
                    if (index < filteredList.size) {
                        val item = filteredList[index]
                        view as TextView
                        view.text = item.body
                        handleChecked(view, item.checked)
                        view.visibility = View.VISIBLE
                    } else view.visibility = View.GONE
                }
            }

            if (items.size > maxItems) {
                binding.ItemsRemaining.visibility = View.VISIBLE
                binding.ItemsRemaining.text = (items.size - maxItems).toString()
            } else binding.ItemsRemaining.visibility = View.GONE
        }
    }


    private fun setDate(timestamp: Long) {
        if (dateFormat != DateFormat.none) {
            binding.Date.visibility = View.VISIBLE
            val date = Date(timestamp)
            when (dateFormat) {
                DateFormat.relative -> binding.Date.text = prettyTime.format(date)
                DateFormat.absolute -> binding.Date.text = formatter.format(date)
            }
        } else binding.Date.visibility = View.GONE
    }

    private fun setColor(color: Color) {
        val context = binding.root.context

        if (color == Color.DEFAULT) {
            val stroke = ContextCompat.getColorStateList(context, R.color.chip_stroke)
            binding.root.setStrokeColor(stroke)
            binding.root.setCardBackgroundColor(0)
        } else {
            binding.root.strokeColor = 0
            val colorInt = Operations.extractColor(color, context)
            binding.root.setCardBackgroundColor(colorInt)
        }
    }

    private fun setImages(images: List<Image>, mediaRoot: File?) {
        if (images.isNotEmpty()) {
            binding.ImageView.visibility = View.VISIBLE
            binding.Message.visibility = View.GONE

            val image = images[0]
            val file = if (mediaRoot != null) File(mediaRoot, image.name) else null

            Glide.with(binding.ImageView)
                .load(file)
                .centerCrop()
                .transition(DrawableTransitionOptions.withCrossFade())
                .diskCacheStrategy(DiskCacheStrategy.NONE)
                .listener(object : RequestListener<Drawable> {

                    override fun onLoadFailed(
                        e: GlideException?,
                        model: Any?,
                        target: Target<Drawable>,
                        isFirstResource: Boolean
                    ): Boolean {
                        binding.Message.visibility = View.VISIBLE
                        return false
                    }

                    override fun onResourceReady(
                        resource: Drawable,
                        model: Any,
                        target: Target<Drawable>,
                        dataSource: DataSource,
                        isFirstResource: Boolean
                    ): Boolean {
                        return false
                    }
                })
                .into(binding.ImageView)
        } else {
            binding.ImageView.visibility = View.GONE
            binding.Message.visibility = View.GONE
            Glide.with(binding.ImageView).clear(binding.ImageView)
        }
    }

    private fun isEmpty(baseNote: BaseNote): Boolean {
        return when (baseNote.type) {
            Type.NOTE -> baseNote.title.isBlank() && baseNote.body.isBlank() && baseNote.images.isEmpty()
            Type.LIST -> baseNote.title.isBlank() && baseNote.items.isEmpty() && baseNote.images.isEmpty()
        }
    }

    private fun getEmptyMessage(baseNote: BaseNote): Int {
        return when (baseNote.type) {
            Type.NOTE -> R.string.empty_note
            Type.LIST -> R.string.empty_list
        }
    }

    private fun handleChecked(textView: TextView, checked: Boolean) {
        if (checked) {
            textView.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.checkbox_16, 0, 0, 0)
        } else textView.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.checkbox_outline_16, 0, 0, 0)
    }
}