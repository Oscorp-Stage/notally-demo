package com.omgodse.notally.recyclerview

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.drawable.ColorDrawable
import android.view.View
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.omgodse.notally.R
import com.omgodse.notally.recyclerview.adapter.BaseNoteAdapter
import com.omgodse.notally.recyclerview.viewholder.BaseNoteVH
import com.omgodse.notally.room.BaseNote
import kotlin.math.abs

class SwipeToActionCallback(
    private val context: Context,
    private val onSwipe: (position: Int, direction: Int, note: BaseNote) -> Unit
) : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT) {

    private val deleteBackground = ColorDrawable(ContextCompat.getColor(context, R.color.delete_background))
    private val archiveBackground = ColorDrawable(ContextCompat.getColor(context, R.color.archive_background))
    private val clearPaint = Paint().apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR) }
    private val deleteIcon = ContextCompat.getDrawable(context, R.drawable.delete)
    private val archiveIcon = ContextCompat.getDrawable(context, R.drawable.archive)

    override fun onMove(
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder,
        target: RecyclerView.ViewHolder
    ): Boolean = false

    override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
        val position = viewHolder.adapterPosition

        // Only process swipe for BaseNote items (not headers)
        if (position != RecyclerView.NO_POSITION && viewHolder is BaseNoteVH) {
            // Get the adapter
            val adapter = (viewHolder.itemView.parent as? RecyclerView)?.adapter as? BaseNoteAdapter

            if (adapter != null) {
                val item = adapter.currentList[position]
                if (item is BaseNote) {
                    onSwipe(position, direction, item)
                } else {
                    // If it's not a BaseNote (e.g., a Header), reset the swipe
                    adapter.notifyItemChanged(position)
                }
            }
        }
    }

    override fun getSwipeThreshold(viewHolder: RecyclerView.ViewHolder): Float {
        // Require 40% swipe to trigger action
        return 0.4f
    }

    override fun getSwipeDirs(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder): Int {
        // Don't allow swiping on Headers
        return if (viewHolder is BaseNoteVH) {
            super.getSwipeDirs(recyclerView, viewHolder)
        } else {
            0
        }
    }

    override fun onChildDraw(
        c: Canvas,
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder,
        dX: Float,
        dY: Float,
        actionState: Int,
        isCurrentlyActive: Boolean
    ) {
        // Only draw background for BaseNoteVH items
        if (viewHolder !is BaseNoteVH) {
            super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive)
            return
        }

        val itemView = viewHolder.itemView
        val isCanceled = dX == 0f && !isCurrentlyActive

        if (isCanceled) {
            clearCanvas(c, itemView.right + dX, itemView.top.toFloat(), itemView.right.toFloat(), itemView.bottom.toFloat())
            super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive)
            return
        }

        // Draw background
        when {
            dX < 0 -> drawDeleteBackground(c, itemView, dX) // Swipe left - delete
            dX > 0 -> drawArchiveBackground(c, itemView, dX) // Swipe right - archive
        }

        super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive)
    }

    private fun drawDeleteBackground(c: Canvas, itemView: View, dX: Float) {
        val background = deleteBackground
        val iconMargin = (itemView.height - deleteIcon!!.intrinsicHeight) / 2
        val iconTop = itemView.top + (itemView.height - deleteIcon.intrinsicHeight) / 2
        val iconBottom = iconTop + deleteIcon.intrinsicHeight

        // Calculate left bounds based on swipe amount
        val iconLeft = itemView.right - iconMargin - deleteIcon.intrinsicWidth
        val iconRight = itemView.right - iconMargin

        // Draw background
        background.setBounds(
            (itemView.right + dX).toInt(),
            itemView.top,
            itemView.right,
            itemView.bottom
        )
        background.draw(c)

        // Only draw icon if swiped far enough
        if (abs(dX) > iconMargin * 2) {
            deleteIcon.setBounds(iconLeft, iconTop, iconRight, iconBottom)
            deleteIcon.draw(c)
        }
    }

    private fun drawArchiveBackground(c: Canvas, itemView: View, dX: Float) {
        val background = archiveBackground
        val iconMargin = (itemView.height - archiveIcon!!.intrinsicHeight) / 2
        val iconTop = itemView.top + (itemView.height - archiveIcon.intrinsicHeight) / 2
        val iconBottom = iconTop + archiveIcon.intrinsicHeight

        // Calculate left bounds based on swipe amount
        val iconLeft = itemView.left + iconMargin
        val iconRight = itemView.left + iconMargin + archiveIcon.intrinsicWidth

        // Draw background
        background.setBounds(
            itemView.left,
            itemView.top,
            (itemView.left + dX).toInt(),
            itemView.bottom
        )
        background.draw(c)

        // Only draw icon if swiped far enough
        if (abs(dX) > iconMargin * 2) {
            archiveIcon.setBounds(iconLeft, iconTop, iconRight, iconBottom)
            archiveIcon.draw(c)
        }
    }

    private fun clearCanvas(c: Canvas, left: Float, top: Float, right: Float, bottom: Float) {
        c.drawRect(left, top, right, bottom, clearPaint)
    }
}