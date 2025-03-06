package com.omgodse.notally.fragments

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.LiveData
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.omgodse.notally.activities.MakeList
import com.omgodse.notally.activities.TakeNote
import com.omgodse.notally.databinding.FragmentNotesBinding
import com.omgodse.notally.miscellaneous.Constants
import com.omgodse.notally.recyclerview.ItemListener
import com.omgodse.notally.recyclerview.SwipeToActionCallback
import com.omgodse.notally.recyclerview.adapter.BaseNoteAdapter
import com.omgodse.notally.room.BaseNote
import com.omgodse.notally.room.Folder
import com.omgodse.notally.room.Item
import com.omgodse.notally.room.Type
import com.omgodse.notally.viewmodels.BaseNoteModel
import java.text.DateFormat
import com.omgodse.notally.R
import com.omgodse.notally.preferences.View as ViewPref

abstract class NotallyFragment : Fragment(), ItemListener {

    private var adapter: BaseNoteAdapter? = null
    internal var binding: FragmentNotesBinding? = null
    private var itemTouchHelper: ItemTouchHelper? = null

    internal val model: BaseNoteModel by activityViewModels()

    override fun onDestroyView() {
        super.onDestroyView()
        binding = null
        adapter = null
        itemTouchHelper = null
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding?.ImageView?.setImageResource(getBackground())

        setupAdapter()
        setupRecyclerView()
        setupSwipeActions()
        setupObserver()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        setHasOptionsMenu(true)
        binding = FragmentNotesBinding.inflate(inflater)
        return binding?.root
    }


    // See [RecyclerView.ViewHolder.getAdapterPosition]
    override fun onClick(position: Int) {
        if (position != -1) {
            adapter?.currentList?.get(position)?.let { item ->
                if (item is BaseNote) {
                    if (model.actionMode.isEnabled()) {
                        handleNoteSelection(item.id, position, item)
                    } else {
                        when (item.type) {
                            Type.NOTE -> goToActivity(TakeNote::class.java, item)
                            Type.LIST -> goToActivity(MakeList::class.java, item)
                        }
                    }
                }
            }
        }
    }

    override fun onLongClick(position: Int) {
        if (position != -1) {
            adapter?.currentList?.get(position)?.let { item ->
                if (item is BaseNote) {
                    handleNoteSelection(item.id, position, item)
                }
            }
        }
    }

    private fun handleNoteSelection(id: Long, position: Int, baseNote: BaseNote) {
        if (model.actionMode.selectedNotes.contains(id)) {
            model.actionMode.remove(id)
        } else model.actionMode.add(id, baseNote)
        adapter?.notifyItemChanged(position, 0)
    }


    private fun setupAdapter() {
        val textSize = model.preferences.textSize.value
        val maxItems = model.preferences.maxItems
        val maxLines = model.preferences.maxLines
        val maxTitle = model.preferences.maxTitle
        val dateFormat = model.preferences.dateFormat.value
        val formatter = DateFormat.getDateInstance(DateFormat.FULL)

        adapter = BaseNoteAdapter(model.actionMode.selectedIds, dateFormat, textSize, maxItems, maxLines, maxTitle, formatter, model.mediaRoot, this)
        adapter?.registerAdapterDataObserver(object : RecyclerView.AdapterDataObserver() {

            override fun onItemRangeInserted(positionStart: Int, itemCount: Int) {
                if (itemCount > 0) {
                    binding?.RecyclerView?.scrollToPosition(positionStart)
                }
            }
        })
        binding?.RecyclerView?.adapter = adapter
        binding?.RecyclerView?.setHasFixedSize(true)
    }

    private fun setupObserver() {
        getObservable().observe(viewLifecycleOwner) { list ->
            adapter?.submitList(list)
            binding?.ImageView?.isVisible = list.isEmpty()
        }

        model.actionMode.closeListener.observe(viewLifecycleOwner) { event ->
            event.handle { ids ->
                adapter?.currentList?.forEachIndexed { index, item ->
                    if (item is BaseNote && ids.contains(item.id)) {
                        adapter?.notifyItemChanged(index, 0)
                    }
                }
                // Re-enable swipe when action mode is closed
                if (!model.actionMode.isEnabled()) {
                    itemTouchHelper?.attachToRecyclerView(binding?.RecyclerView)
                } else {
                    itemTouchHelper?.attachToRecyclerView(null)
                }
            }
        }
    }

    private fun setupRecyclerView() {
        binding?.RecyclerView?.layoutManager = if (model.preferences.view.value == ViewPref.grid) {
            StaggeredGridLayoutManager(2, RecyclerView.VERTICAL)
        } else LinearLayoutManager(requireContext())
    }

    private fun setupSwipeActions() {
        val swipeCallback = SwipeToActionCallback(
            requireContext(),
            { position, direction, note ->
                when (direction) {
                    ItemTouchHelper.LEFT -> {
                        // Swipe left to delete
                        showDeleteConfirmation(note)
                    }
                    ItemTouchHelper.RIGHT -> {
                        // Swipe right to archive/unarchive/restore based on current folder
                        when (note.folder) {
                            Folder.NOTES -> {
                                model.updateFolder(note.id, Folder.ARCHIVED)
                                showUndoSnackbar(getString(R.string.note_archived)) {
                                    model.updateFolder(note.id, Folder.NOTES)
                                }
                            }
                            Folder.ARCHIVED -> {
                                model.updateFolder(note.id, Folder.NOTES)
                                showUndoSnackbar(getString(R.string.note_unarchived)) {
                                    model.updateFolder(note.id, Folder.ARCHIVED)
                                }
                            }
                            Folder.DELETED -> {
                                model.updateFolder(note.id, Folder.NOTES)
                                showUndoSnackbar(getString(R.string.note_restored)) {
                                    model.updateFolder(note.id, Folder.DELETED)
                                }
                            }
                        }
                    }
                }
            }
        )

        itemTouchHelper = ItemTouchHelper(swipeCallback).apply {
            // Only attach if not in action mode
            if (!model.actionMode.isEnabled()) {
                attachToRecyclerView(binding?.RecyclerView)
            }
        }

        // We'll use the closeListener to monitor action mode changes
        // rather than depending on a listener property that doesn't exist
    }

    private fun showDeleteConfirmation(note: BaseNote) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.delete_note)
            .setMessage(
                when (note.folder) {
                    Folder.DELETED -> R.string.delete_note_forever
                    else -> R.string.delete_note_confirmation
                }
            )
            .setNegativeButton(R.string.cancel) { _, _ ->
                // Refresh adapter to restore the swiped item
                adapter?.notifyDataSetChanged()
            }
            .setPositiveButton(R.string.delete) { _, _ ->
                when (note.folder) {
                    Folder.DELETED -> model.permanentlyDeleteNote(note.id)
                    else -> {
                        model.updateFolder(note.id, Folder.DELETED)
                        showUndoSnackbar(getString(R.string.note_deleted)) {
                            model.updateFolder(note.id, Folder.NOTES)
                        }
                    }
                }
            }
            .setCancelable(false)
            .show()
    }

    private fun showUndoSnackbar(message: String, action: () -> Unit) {
        binding?.root?.let {
            Snackbar.make(it, message, Snackbar.LENGTH_LONG)
                .setAction(R.string.undo) { action() }
                .show()
        }
    }

    private fun goToActivity(activity: Class<*>, baseNote: BaseNote) {
        val intent = Intent(requireContext(), activity)
        intent.putExtra(Constants.SelectedBaseNote, baseNote.id)
        startActivity(intent)
    }

    abstract fun getBackground(): Int

    abstract fun getObservable(): LiveData<List<Item>>
}