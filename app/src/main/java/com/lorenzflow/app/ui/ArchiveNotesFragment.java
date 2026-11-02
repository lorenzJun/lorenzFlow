package com.lorenzflow.app.ui;

import android.content.res.Configuration;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.StaggeredGridLayoutManager;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.lorenzflow.app.R;
import com.lorenzflow.app.data.AppDatabase;
import com.lorenzflow.app.data.Note;
import com.lorenzflow.app.data.NoteDao;
import com.lorenzflow.app.databinding.FragmentArchiveNotesBinding;
import com.lorenzflow.app.databinding.ItemNoteBinding;

import com.lorenzflow.app.util.AppExecutors;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;

public class ArchiveNotesFragment extends Fragment {

    private FragmentArchiveNotesBinding binding;
    private NoteDao noteDao;
    private ArchivedNoteAdapter adapter;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentArchiveNotesBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        noteDao = AppDatabase.getInstance(requireContext()).noteDao();

        // Back navigation
        binding.archiveNotesToolbar.setNavigationOnClickListener(v ->
                requireActivity().onBackPressed());

        // RecyclerView
        adapter = new ArchivedNoteAdapter();
        binding.recyclerArchivedNotes.setLayoutManager(
                new StaggeredGridLayoutManager(2, StaggeredGridLayoutManager.VERTICAL));
        binding.recyclerArchivedNotes.setAdapter(adapter);

        loadArchivedNotes();
    }

    private void loadArchivedNotes() {
        AppExecutors.io().execute(() -> {
            List<Note> archived = noteDao.getArchivedNotes();
            if (getActivity() == null) return;
            requireActivity().runOnUiThread(() -> {
                adapter.setNotes(archived);
                boolean empty = archived.isEmpty();
                binding.archiveNotesEmptyState.setVisibility(empty ? View.VISIBLE : View.GONE);
                binding.recyclerArchivedNotes.setVisibility(empty ? View.GONE : View.VISIBLE);
            });
        });
    }

    private void showNoteOptions(Note note) {
        String label = (note.getTitle() != null && !note.getTitle().isEmpty())
                ? note.getTitle() : getString(R.string.this_note);
        String[] options = {getString(R.string.unarchive), getString(R.string.delete)};

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(label)
                .setItems(options, (dialog, which) -> {
                    if (which == 0) {
                        restoreNote(note);
                    } else {
                        confirmDeleteNote(note, label);
                    }
                })
                .show();
    }

    private void restoreNote(Note note) {
        note.setArchived(false);
        AppExecutors.io().execute(() -> {
            noteDao.update(note);
            if (getActivity() != null) {
                requireActivity().runOnUiThread(() -> {
                    Toast.makeText(requireContext(), R.string.unarchive, Toast.LENGTH_SHORT).show();
                    loadArchivedNotes();
                });
            }
        });
    }

    private void confirmDeleteNote(Note note, String label) {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.delete)
                .setMessage(getString(R.string.permanently_delete_confirm, label))
                .setPositiveButton(R.string.delete, (d, w) ->
                        AppExecutors.io().execute(() -> {
                            noteDao.delete(note);
                            if (getActivity() != null) {
                                requireActivity().runOnUiThread(() -> {
                                    Toast.makeText(requireContext(), R.string.note_deleted,
                                            Toast.LENGTH_SHORT).show();
                                    loadArchivedNotes();
                                });
                            }
                        }))
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    // ===================== Inner Adapter =====================

    private class ArchivedNoteAdapter extends RecyclerView.Adapter<ArchivedNoteAdapter.ViewHolder> {

        private List<Note> notes = new ArrayList<>();

        void setNotes(List<Note> notes) {
            this.notes = new ArrayList<>(notes);
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            ItemNoteBinding b = ItemNoteBinding.inflate(
                    LayoutInflater.from(parent.getContext()), parent, false);
            return new ViewHolder(b);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            holder.bind(notes.get(position));
        }

        @Override
        public int getItemCount() {
            return notes.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            private final ItemNoteBinding b;

            ViewHolder(ItemNoteBinding b) {
                super(b.getRoot());
                this.b = b;
            }

            void bind(Note note) {
                if (note.getTitle() != null && !note.getTitle().isEmpty()) {
                    b.textNoteTitle.setText(note.getTitle());
                    b.textNoteTitle.setVisibility(View.VISIBLE);
                } else {
                    b.textNoteTitle.setVisibility(View.GONE);
                }
                b.textNoteBody.setText(note.getBody());

                // Card color — dimmed to indicate archived state
                boolean isDark = (b.getRoot().getContext().getResources().getConfiguration().uiMode
                        & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
                int cardColor = NoteAdapter.getColorForIndex(note.getColor(), isDark);
                b.getRoot().setCardBackgroundColor(cardColor);
                b.getRoot().setAlpha(0.7f);

                // Tap → show Restore / Delete options
                b.getRoot().setOnClickListener(v -> showNoteOptions(note));
                b.getRoot().setOnLongClickListener(v -> {
                    showNoteOptions(note);
                    return true;
                });
            }
        }
    }
}
