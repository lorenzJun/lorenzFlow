package com.lorenzflow.app.ui;

import android.content.res.Configuration;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.MenuProvider;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.Lifecycle;
import androidx.recyclerview.widget.StaggeredGridLayoutManager;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import com.lorenzflow.app.R;
import com.lorenzflow.app.data.AppDatabase;
import com.lorenzflow.app.data.Note;
import com.lorenzflow.app.data.NoteDao;
import com.lorenzflow.app.databinding.DialogAddNoteBinding;
import com.lorenzflow.app.databinding.FragmentNotesBinding;

import java.util.List;
import java.util.concurrent.Executors;

public class NotesFragment extends Fragment implements NoteAdapter.OnNoteClickListener {

    private FragmentNotesBinding binding;
    private NoteAdapter adapter;
    private NoteDao noteDao;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentNotesBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        noteDao = AppDatabase.getInstance(requireContext()).noteDao();

        adapter = new NoteAdapter(this);
        binding.recyclerNotes.setLayoutManager(
                new StaggeredGridLayoutManager(2, StaggeredGridLayoutManager.VERTICAL));
        binding.recyclerNotes.setAdapter(adapter);

        // Attach ItemTouchHelper for manual reordering
        androidx.recyclerview.widget.ItemTouchHelper itemTouchHelper = new androidx.recyclerview.widget.ItemTouchHelper(
                new androidx.recyclerview.widget.ItemTouchHelper.SimpleCallback(
                        androidx.recyclerview.widget.ItemTouchHelper.UP | androidx.recyclerview.widget.ItemTouchHelper.DOWN |
                        androidx.recyclerview.widget.ItemTouchHelper.LEFT | androidx.recyclerview.widget.ItemTouchHelper.RIGHT, 0) {
            @Override
            public boolean onMove(@NonNull androidx.recyclerview.widget.RecyclerView recyclerView,
                                  @NonNull androidx.recyclerview.widget.RecyclerView.ViewHolder viewHolder,
                                  @NonNull androidx.recyclerview.widget.RecyclerView.ViewHolder target) {
                int fromPosition = viewHolder.getAdapterPosition();
                int toPosition = target.getAdapterPosition();

                List<Note> notes = adapter.getNotes();
                if (fromPosition < 0 || fromPosition >= notes.size() || toPosition < 0 || toPosition >= notes.size()) {
                    return false;
                }

                Note movedNote = notes.remove(fromPosition);
                notes.add(toPosition, movedNote);
                adapter.notifyItemMoved(fromPosition, toPosition);
                return true;
            }

            @Override
            public void onSwiped(@NonNull androidx.recyclerview.widget.RecyclerView.ViewHolder viewHolder, int direction) {
                // Swipe to dismiss not supported for notes
            }

            @Override
            public void clearView(@NonNull androidx.recyclerview.widget.RecyclerView recyclerView,
                                  @NonNull androidx.recyclerview.widget.RecyclerView.ViewHolder viewHolder) {
                super.clearView(recyclerView, viewHolder);
                // Save updated positions in background thread
                List<Note> notes = adapter.getNotes();
                Executors.newSingleThreadExecutor().execute(() -> {
                    for (int i = 0; i < notes.size(); i++) {
                        Note note = notes.get(i);
                        note.setPosition(i);
                        noteDao.update(note);
                    }
                });
            }
        });
        itemTouchHelper.attachToRecyclerView(binding.recyclerNotes);

        binding.fabAddNote.setOnClickListener(v -> showAddNoteDialog(null));

        // Toolbar menu for archive
        requireActivity().addMenuProvider(new MenuProvider() {
            @Override
            public void onCreateMenu(@NonNull Menu menu, @NonNull MenuInflater menuInflater) {
                menuInflater.inflate(R.menu.menu_notes, menu);
            }

            @Override
            public boolean onMenuItemSelected(@NonNull MenuItem menuItem) {
                if (menuItem.getItemId() == R.id.action_view_notes_archive) {
                    requireActivity().getSupportFragmentManager().beginTransaction()
                            .replace(R.id.fragment_container, new ArchiveNotesFragment())
                            .addToBackStack(null)
                            .commit();
                    return true;
                }
                return false;
            }
        }, getViewLifecycleOwner(), Lifecycle.State.RESUMED);

        loadNotes();
    }

    private void loadNotes() {
        Executors.newSingleThreadExecutor().execute(() -> {
            List<Note> notes = noteDao.getAllNotes();
            if (getActivity() == null) return;
            requireActivity().runOnUiThread(() -> {
                adapter.setNotes(notes);
                binding.emptyState.setVisibility(notes.isEmpty() ? View.VISIBLE : View.GONE);
                binding.recyclerNotes.setVisibility(notes.isEmpty() ? View.GONE : View.VISIBLE);
                updateScrollability();
            });
        });
    }

    private void updateScrollability() {
        if (binding == null) return;
        binding.recyclerNotes.getViewTreeObserver().addOnGlobalLayoutListener(
                new android.view.ViewTreeObserver.OnGlobalLayoutListener() {
                    @Override
                    public void onGlobalLayout() {
                        if (binding == null) return;
                        binding.recyclerNotes.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                        boolean canScroll = binding.recyclerNotes.canScrollVertically(1)
                                || binding.recyclerNotes.canScrollVertically(-1);
                        if (binding.recyclerNotes.isNestedScrollingEnabled() != canScroll) {
                            binding.recyclerNotes.setNestedScrollingEnabled(canScroll);
                        }
                    }
                });
    }

    private boolean isDarkMode() {
        return (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                == Configuration.UI_MODE_NIGHT_YES;
    }

    /**
     * Shows the add/edit note dialog. If existingNote is not null, pre-fills fields for editing.
     */
    private void showAddNoteDialog(@Nullable Note existingNote) {
        DialogAddNoteBinding dialogBinding = DialogAddNoteBinding.inflate(getLayoutInflater());

        final int[] selectedColor = {existingNote != null ? existingNote.getColor() : 0};

        // Build color picker circles
        int[] palette = NoteAdapter.getColorPalette(isDarkMode());
        int circleSize = (int) (getResources().getDimension(R.dimen.color_circle_size));
        int margin = 6;

        for (int i = 0; i < palette.length; i++) {
            View circle = new View(requireContext());
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(circleSize, circleSize);
            params.setMargins(margin, margin, margin, margin);
            circle.setLayoutParams(params);

            GradientDrawable drawable = new GradientDrawable();
            drawable.setShape(GradientDrawable.OVAL);
            drawable.setColor(palette[i]);
            drawable.setStroke(selectedColor[0] == i ? 4 : 2,
                    isDarkMode() ? 0xFFC7C5D0 : 0xFF777680);
            circle.setBackground(drawable);

            final int colorIndex = i;
            circle.setOnClickListener(v -> {
                selectedColor[0] = colorIndex;
                // Update all circle strokes
                for (int j = 0; j < dialogBinding.colorPickerRow.getChildCount(); j++) {
                    View child = dialogBinding.colorPickerRow.getChildAt(j);
                    GradientDrawable bg = (GradientDrawable) child.getBackground();
                    bg.setStroke(j == colorIndex ? 4 : 2,
                            isDarkMode() ? 0xFFC7C5D0 : 0xFF777680);
                }
            });

            dialogBinding.colorPickerRow.addView(circle);
        }

        // Pre-fill if editing
        if (existingNote != null) {
            dialogBinding.editNoteTitle.setText(existingNote.getTitle());
            dialogBinding.editNoteBody.setText(existingNote.getBody());
        }

        String dialogTitle = existingNote != null ? "Edit Note" : getString(R.string.add_note);

        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(requireContext())
                .setTitle(dialogTitle)
                .setView(dialogBinding.getRoot())
                .setPositiveButton(R.string.save, (dialog, which) -> {
                    String title = dialogBinding.editNoteTitle.getText() != null
                            ? dialogBinding.editNoteTitle.getText().toString().trim() : "";
                    String body = dialogBinding.editNoteBody.getText() != null
                            ? dialogBinding.editNoteBody.getText().toString().trim() : "";

                    if (title.isEmpty() && body.isEmpty()) return;

                    Executors.newSingleThreadExecutor().execute(() -> {
                        if (existingNote != null) {
                            existingNote.setTitle(title);
                            existingNote.setBody(body);
                            existingNote.setColor(selectedColor[0]);
                            noteDao.update(existingNote);
                        } else {
                            Note note = new Note();
                            note.setTitle(title);
                            note.setBody(body);
                            note.setColor(selectedColor[0]);
                            
                            // Make new note appear at the top
                            List<Note> existingNotes = noteDao.getAllNotes();
                            for (int i = 0; i < existingNotes.size(); i++) {
                                Note existing = existingNotes.get(i);
                                existing.setPosition(i + 1);
                                noteDao.update(existing);
                            }
                            
                            note.setPosition(0);
                            noteDao.insert(note);
                        }
                        if (getActivity() != null) {
                            requireActivity().runOnUiThread(this::loadNotes);
                        }
                    });
                })
                .setNegativeButton(R.string.cancel, null);

        // Add delete button if editing
        if (existingNote != null) {
            builder.setNeutralButton(R.string.delete, (dialog, which) -> {
                Executors.newSingleThreadExecutor().execute(() -> {
                    noteDao.delete(existingNote);
                    if (getActivity() != null) {
                        requireActivity().runOnUiThread(() -> {
                            Toast.makeText(requireContext(), R.string.note_deleted, Toast.LENGTH_SHORT).show();
                            loadNotes();
                        });
                    }
                });
            });
        }

        builder.show();
    }

    @Override
    public void onNoteClick(Note note) {
        showAddNoteDialog(note);
    }

    @Override
    public void onNoteLongClick(Note note) {
        // Show options: Archive or Delete
        String noteLabel = (note.getTitle() != null && !note.getTitle().isEmpty())
                ? note.getTitle() : "this note";

        String[] options = {getString(R.string.archive), getString(R.string.delete)};

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(noteLabel)
                .setItems(options, (dialog, which) -> {
                    if (which == 0) {
                        // Archive
                        onNoteArchive(note);
                    } else {
                        // Delete
                        new MaterialAlertDialogBuilder(requireContext())
                                .setTitle(R.string.delete)
                                .setMessage("Delete \"" + noteLabel + "\"?")
                                .setPositiveButton(R.string.delete, (d, w) -> {
                                    Executors.newSingleThreadExecutor().execute(() -> {
                                        noteDao.delete(note);
                                        if (getActivity() != null) {
                                            requireActivity().runOnUiThread(() -> {
                                                Toast.makeText(requireContext(), R.string.note_deleted,
                                                        Toast.LENGTH_SHORT).show();
                                                loadNotes();
                                            });
                                        }
                                    });
                                })
                                .setNegativeButton(R.string.cancel, null)
                                .show();
                    }
                })
                .show();
    }

    @Override
    public void onNoteArchive(Note note) {
        note.setArchived(true);
        Executors.newSingleThreadExecutor().execute(() -> {
            noteDao.update(note);
            if (getActivity() == null) return;
            requireActivity().runOnUiThread(() -> {
                loadNotes();
                Snackbar.make(binding.getRoot(), R.string.note_archived, Snackbar.LENGTH_LONG)
                        .setAction(R.string.undo, v -> {
                            note.setArchived(false);
                            Executors.newSingleThreadExecutor().execute(() -> {
                                noteDao.update(note);
                                if (getActivity() != null) {
                                    requireActivity().runOnUiThread(NotesFragment.this::loadNotes);
                                }
                            });
                        })
                        .show();
            });
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
