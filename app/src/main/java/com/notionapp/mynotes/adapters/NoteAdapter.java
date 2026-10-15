package com.notionapp.mynotes.adapters;

import android.content.res.ColorStateList;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.notionapp.mynotes.databinding.ItemNoteBinding;
import com.notionapp.mynotes.models.Note;
import com.notionapp.mynotes.utils.CategoryUtils;
import com.notionapp.mynotes.utils.ChecklistUtils;

/**
 * NoteAdapter — displays notes in the RecyclerView using ListAdapter + DiffUtil.
 *
 * Why ListAdapter instead of RecyclerView.Adapter?
 * ListAdapter uses DiffUtil internally to:
 * 1. Calculate the minimal set of changes between old and new lists
 * 2. Animate insertions, removals, and moves automatically
 * 3. Run the diff calculation on a background thread
 *
 * This means smoother animations and better performance than notifyDataSetChanged().
 *
 * In Java, the click callback is defined via a simple interface instead of
 * Kotlin's function type (Note) -> Unit.
 */
public class NoteAdapter extends ListAdapter<Note, NoteAdapter.NoteViewHolder> {

    // ==============================
    // Click Callback Interface
    // ==============================

    /**
     * Functional interface for note click events.
     * Implemented as a lambda at the call site.
     */
    public interface OnNoteClickListener {
        void onNoteClick(Note note);
    }

    private final OnNoteClickListener onNoteClick;
    private OnNoteClickListener onNoteLongClick;

    /** Long-press handler (used for pin/unpin). */
    public void setOnNoteLongClickListener(OnNoteClickListener listener) {
        this.onNoteLongClick = listener;
    }

    public NoteAdapter(OnNoteClickListener onNoteClick) {
        super(NOTE_DIFF_CALLBACK);
        this.onNoteClick = onNoteClick;
    }

    // ==============================
    // DiffUtil Callback
    // ==============================

    /**
     * DiffUtil Callback — tells ListAdapter how to compare notes.
     *
     * areItemsTheSame: checks if two items represent the SAME note (by ID)
     * areContentsTheSame: checks if the note's data has CHANGED (triggers rebind)
     *
     * Note.equals() covers the content comparison — it checks all fields.
     */
    private static final DiffUtil.ItemCallback<Note> NOTE_DIFF_CALLBACK =
            new DiffUtil.ItemCallback<Note>() {
                @Override
                public boolean areItemsTheSame(@NonNull Note oldItem, @NonNull Note newItem) {
                    return oldItem.getId() == newItem.getId();
                }

                @Override
                public boolean areContentsTheSame(@NonNull Note oldItem, @NonNull Note newItem) {
                    return oldItem.equals(newItem);
                }
            };

    // ==============================
    // ViewHolder
    // ==============================

    /**
     * ViewHolder — binds note data to the card layout using ViewBinding.
     *
     * ViewBinding generates a type-safe binding class (ItemNoteBinding)
     * from item_note.xml, eliminating findViewById calls.
     */
    class NoteViewHolder extends RecyclerView.ViewHolder {

        private final ItemNoteBinding binding;

        NoteViewHolder(ItemNoteBinding binding) {
            super(binding.getRoot());
            this.binding = binding;

            // Set click listener once in the constructor — not in bind()
            // Avoids recreating the listener object on every rebind
            binding.getRoot().setOnClickListener(v -> {
                int position = getAdapterPosition();
                if (position != RecyclerView.NO_POSITION) {
                    onNoteClick.onNoteClick(getItem(position));
                }
            });

            binding.getRoot().setOnLongClickListener(v -> {
                int position = getAdapterPosition();
                if (position != RecyclerView.NO_POSITION && onNoteLongClick != null) {
                    onNoteLongClick.onNoteClick(getItem(position));
                    return true;
                }
                return false;
            });
        }

        /**
         * Binds a Note's data to the card's views.
         */
        void bind(Note note) {
            android.content.Context ctx = binding.getRoot().getContext();

            binding.tvNoteTitle.setText(note.getTitle());
            binding.ivPin.setVisibility(note.isPinned()
                    ? android.view.View.VISIBLE : android.view.View.GONE);

            // Content preview with checklist markers shown as box glyphs
            String content = note.getContent();
            if (content != null && !content.trim().isEmpty()) {
                binding.tvNoteContent.setText(ChecklistUtils.toPreview(content));
                binding.tvNoteContent.setVisibility(android.view.View.VISIBLE);
            } else {
                binding.tvNoteContent.setVisibility(android.view.View.GONE);
            }

            // Notion-style tag
            binding.chipCategory.setText(note.getCategory());
            binding.chipCategory.setChipBackgroundColor(ColorStateList.valueOf(
                    CategoryUtils.getTagBg(ctx, note.getCategory())));
            binding.chipCategory.setTextColor(CategoryUtils.getTagText(ctx, note.getCategory()));

            // Checklist progress
            int[] progress = ChecklistUtils.progress(content);
            if (progress[1] > 0) {
                binding.tvChecklist.setText("\u2611 " + ctx.getString(
                        com.notionapp.mynotes.R.string.checklist_progress, progress[0], progress[1]));
                binding.tvChecklist.setVisibility(android.view.View.VISIBLE);
            } else {
                binding.tvChecklist.setVisibility(android.view.View.GONE);
            }

            // Reminder
            if (note.getReminderTime() > 0) {
                binding.tvReminder.setText("\u23F0 " + android.text.format.DateUtils.formatDateTime(
                        ctx, note.getReminderTime(),
                        android.text.format.DateUtils.FORMAT_SHOW_DATE
                                | android.text.format.DateUtils.FORMAT_SHOW_TIME
                                | android.text.format.DateUtils.FORMAT_ABBREV_MONTH));
                binding.tvReminder.setVisibility(android.view.View.VISIBLE);
            } else {
                binding.tvReminder.setVisibility(android.view.View.GONE);
            }

            binding.tvNoteDate.setText(CategoryUtils.formatDate(note.getUpdatedDate()));
        }
    }

    // ==============================
    // Adapter Overrides
    // ==============================

    @NonNull
    @Override
    public NoteViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemNoteBinding binding = ItemNoteBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new NoteViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull NoteViewHolder holder, int position) {
        holder.bind(getItem(position));
    }
}
