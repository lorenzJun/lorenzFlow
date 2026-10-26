package com.lorenzflow.app.ui;

import android.content.res.Configuration;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.lorenzflow.app.R;
import com.lorenzflow.app.data.Note;
import com.lorenzflow.app.databinding.ItemNoteBinding;
import com.lorenzflow.app.util.AnimUtil;

import java.util.ArrayList;
import java.util.List;

public class NoteAdapter extends RecyclerView.Adapter<NoteAdapter.NoteViewHolder> {

    public interface OnNoteClickListener {
        void onNoteClick(Note note);
        void onNoteLongClick(Note note);
        void onNoteArchive(Note note);
    }

    // Light mode note colors
    private static final int[] LIGHT_COLORS = {
            0xFFFFFBFF, // default
            0xFFFACDCD, // red
            0xFFFFE0B2, // orange
            0xFFFFF9C4, // yellow
            0xFFC8E6C9, // green
            0xFFB2DFDB, // teal
            0xFFBBDEFB, // blue
            0xFFE1BEE7  // purple
    };

    // Dark mode note colors
    private static final int[] DARK_COLORS = {
            0xFF2D2D30, // default
            0xFF5C2B29, // red
            0xFF614A19, // orange
            0xFF635D19, // yellow
            0xFF2D4A2E, // green
            0xFF16504B, // teal
            0xFF1E3A5F, // blue
            0xFF42275E  // purple
    };

    private List<Note> notes = new ArrayList<>();
    private final OnNoteClickListener listener;

    public NoteAdapter(OnNoteClickListener listener) {
        this.listener = listener;
    }

    public void setNotes(List<Note> notes) {
        this.notes = new ArrayList<>(notes);
        notifyDataSetChanged();
    }

    public List<Note> getNotes() {
        return notes;
    }

    /**
     * Returns the appropriate color array for the current theme mode.
     */
    public static int[] getColorPalette(boolean isDarkMode) {
        return isDarkMode ? DARK_COLORS : LIGHT_COLORS;
    }

    /**
     * Maps a color index (0-7) to the actual color integer.
     */
    public static int getColorForIndex(int index, boolean isDarkMode) {
        int[] palette = getColorPalette(isDarkMode);
        if (index >= 0 && index < palette.length) {
            return palette[index];
        }
        return palette[0];
    }

    @NonNull
    @Override
    public NoteViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemNoteBinding binding = ItemNoteBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new NoteViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull NoteViewHolder holder, int position) {
        holder.bind(notes.get(position));
        holder.fadeIn(position);
    }

    @Override
    public int getItemCount() {
        return notes.size();
    }

    class NoteViewHolder extends RecyclerView.ViewHolder {
        private final ItemNoteBinding binding;
        private boolean animatedIn;

        NoteViewHolder(ItemNoteBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        /** Subtle fade-in, run once per ViewHolder instance. */
        void fadeIn(int position) {
            animatedIn = AnimUtil.fadeInOnce(binding.getRoot(), position, animatedIn);
        }

        void bind(Note note) {
            // Title
            if (note.getTitle() != null && !note.getTitle().isEmpty()) {
                binding.textNoteTitle.setText(note.getTitle());
                binding.textNoteTitle.setVisibility(View.VISIBLE);
            } else {
                binding.textNoteTitle.setVisibility(View.GONE);
            }

            // Body
            binding.textNoteBody.setText(note.getBody());

            // Card color
            boolean isDark = (binding.getRoot().getContext().getResources().getConfiguration().uiMode
                    & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
            int cardColor = getColorForIndex(note.getColor(), isDark);
            binding.getRoot().setCardBackgroundColor(cardColor);

            // Click → edit
            binding.getRoot().setOnClickListener(v -> {
                if (listener != null) listener.onNoteClick(note);
            });

            // Long-press → options dialog (Archive or Delete)
            binding.getRoot().setOnLongClickListener(v -> {
                if (listener != null) listener.onNoteLongClick(note);
                return true;
            });
        }
    }
}
