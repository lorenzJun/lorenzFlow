package com.lorenzflow.app.ui;

import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.MenuProvider;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.Lifecycle;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import com.lorenzflow.app.R;
import com.lorenzflow.app.data.AppDatabase;
import com.lorenzflow.app.data.Task;
import com.lorenzflow.app.data.TaskDao;
import com.lorenzflow.app.databinding.FragmentTasksBinding;
import com.lorenzflow.app.util.AlarmScheduler;
import com.lorenzflow.app.util.PdfExportUtil;

import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executors;

public class TasksFragment extends Fragment
        implements TaskAdapter.OnTaskActionListener, TaskDialogHelper.Host {

    private FragmentTasksBinding binding;
    private TaskAdapter activeAdapter;
    private TaskAdapter completedAdapter;
    private TaskDao taskDao;
    private boolean completedExpanded = false;
    private android.content.SharedPreferences prefs;
    private String selectedFilterCategory = "All";
    private String searchQuery = "";

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentTasksBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        taskDao = AppDatabase.getInstance(requireContext()).taskDao();
        prefs = requireContext().getSharedPreferences("lorenz_flow_prefs", Context.MODE_PRIVATE);

        setupRecyclerViews();
        setupSwipeToDelete();
        setupCompletedSection();
        setupFab();
        setupMenu();
        setupCategoryFilter();
        setupQuickActions();
        setupHeaderAvatar();
        setupSearch();

        binding.btnExportPdf.setOnClickListener(v -> exportPdf());
        binding.btnClearCompleted.setOnClickListener(v -> clearCompletedTasks());

        loadTasks();
    }

    /** Shows the saved profile photo (same source as ProfileFragment) or the
     *  default avatar when no photo was chosen yet. */
    private void setupHeaderAvatar() {
        String imageUri = prefs.getString("profile_image_uri", null);
        if (imageUri != null && !imageUri.isEmpty()) {
            binding.avatarImage.setImageURI(Uri.parse(imageUri));
            if (binding.avatarImage.getDrawable() == null) {
                binding.avatarImage.setImageResource(R.drawable.ic_default_avatar);
            }
        } else {
            binding.avatarImage.setImageResource(R.drawable.ic_default_avatar);
        }
    }

    /** Quick-action tiles on the redesigned header. */
    private void setupQuickActions() {
        binding.quickAddTask.setOnClickListener(v -> showTaskDialog(null));
        binding.quickNewNote.setOnClickListener(v ->
                requireActivity().getSupportFragmentManager().beginTransaction()
                        .replace(R.id.fragment_container, new NotesFragment())
                        .addToBackStack(null)
                        .commit());
        binding.quickStartFocus.setOnClickListener(v ->
                requireActivity().getSupportFragmentManager().beginTransaction()
                        .replace(R.id.fragment_container, new PomodoroFragment())
                        .addToBackStack(null)
                        .commit());
    }

    /** Client-side title search over the already-loaded task lists. */
    private void setupSearch() {
        binding.editSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) { }
            @Override
            public void afterTextChanged(Editable s) {
                String query = s.toString().trim();
                if (query.equals(searchQuery)) return;
                searchQuery = query;
                loadTasks();
            }
        });
    }

    private void setupCategoryFilter() {
        binding.chipGroupFilter.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (checkedIds.isEmpty()) return;
            int checkedId = checkedIds.get(0);
            if (checkedId == R.id.filter_all) {
                selectedFilterCategory = "All";
            } else if (checkedId == R.id.filter_general) {
                selectedFilterCategory = "General";
            } else if (checkedId == R.id.filter_work) {
                selectedFilterCategory = "Work";
            } else if (checkedId == R.id.filter_personal) {
                selectedFilterCategory = "Personal";
            } else if (checkedId == R.id.filter_school) {
                selectedFilterCategory = "School";
            } else if (checkedId == R.id.filter_wellness) {
                selectedFilterCategory = "Wellness";
            } else if (checkedId == R.id.filter_urgent) {
                selectedFilterCategory = "Urgent";
            }
            loadTasks();
        });
    }

    private void setupRecyclerViews() {
        // Active tasks
        activeAdapter = new TaskAdapter(this);
        binding.recyclerActiveTasks.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.recyclerActiveTasks.setAdapter(activeAdapter);

        // Completed tasks
        completedAdapter = new TaskAdapter(this);
        binding.recyclerCompletedTasks.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.recyclerCompletedTasks.setAdapter(completedAdapter);
    }

    private void setupSwipeToDelete() {
        ItemTouchHelper.SimpleCallback swipeCallback =
                new ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT) {
            @Override
            public boolean onMove(@NonNull RecyclerView recyclerView,
                                  @NonNull RecyclerView.ViewHolder viewHolder,
                                  @NonNull RecyclerView.ViewHolder target) {
                return false;
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
                int position = viewHolder.getAdapterPosition();
                Task task = activeAdapter.getTaskAt(position);
                if (task == null) return;

                activeAdapter.removeAt(position);
                updateEmptyState();

                // Cancel alarm
                AlarmScheduler.cancelAlarm(requireContext(), task);

                Snackbar.make(binding.getRoot(), R.string.task_deleted, Snackbar.LENGTH_LONG)
                        .setAction(R.string.undo, v -> {
                            // Re-insert on undo
                            Executors.newSingleThreadExecutor().execute(() -> {
                                taskDao.insert(task);
                                if (task.getDeadline() > 0 && task.getDeadline() > System.currentTimeMillis()) {
                                    AlarmScheduler.scheduleAlarm(requireContext(), task);
                                }
                                requireActivity().runOnUiThread(TasksFragment.this::loadTasks);
                            });
                        })
                        .addCallback(new Snackbar.Callback() {
                            @Override
                            public void onDismissed(Snackbar snackbar, int event) {
                                if (event != DISMISS_EVENT_ACTION) {
                                    // Actually delete from DB if not undone
                                    Executors.newSingleThreadExecutor().execute(() -> taskDao.delete(task));
                                }
                            }
                        })
                        .show();
            }
        };

        new ItemTouchHelper(swipeCallback).attachToRecyclerView(binding.recyclerActiveTasks);
    }

    private void setupCompletedSection() {
        binding.completedHeader.setOnClickListener(v -> {
            completedExpanded = !completedExpanded;
            binding.recyclerCompletedTasks.setVisibility(
                    completedExpanded ? View.VISIBLE : View.GONE);
            binding.expandIcon.setImageResource(
                    completedExpanded ? R.drawable.ic_expand_less : R.drawable.ic_expand_more);
        });
    }

    private void setupFab() {
        binding.fabAddTask.setOnClickListener(v -> showTaskDialog(null));
    }

    private void setupMenu() {
        requireActivity().addMenuProvider(new MenuProvider() {
            @Override
            public void onCreateMenu(@NonNull Menu menu, @NonNull MenuInflater menuInflater) {
                menuInflater.inflate(R.menu.menu_tasks, menu);
            }

            @Override
            public boolean onMenuItemSelected(@NonNull MenuItem menuItem) {
                int id = menuItem.getItemId();
                if (id == R.id.action_export_pdf) {
                    exportPdf();
                    return true;
                } else if (id == R.id.action_view_archive) {
                    requireActivity().getSupportFragmentManager().beginTransaction()
                            .replace(R.id.fragment_container, new ArchiveTasksFragment())
                            .addToBackStack(null)
                            .commit();
                    return true;
                } else if (id == R.id.sort_deadline) {
                    prefs.edit().putInt("pref_tasks_sort", 0).apply();
                    loadTasks();
                    return true;
                } else if (id == R.id.sort_newest) {
                    prefs.edit().putInt("pref_tasks_sort", 1).apply();
                    loadTasks();
                    return true;
                } else if (id == R.id.sort_oldest) {
                    prefs.edit().putInt("pref_tasks_sort", 2).apply();
                    loadTasks();
                    return true;
                }
                return false;
            }
        }, getViewLifecycleOwner(), Lifecycle.State.RESUMED);
    }

    private void loadTasks() {
        Executors.newSingleThreadExecutor().execute(() -> {
            List<Task> active = taskDao.getActiveTasks();
            List<Task> completed = taskDao.getCompletedTasks();

            // Hero counts every active task due today, before filters/sorting
            final int dueToday = countDueToday(active);

            // Filter by category if not "All"
            if (!selectedFilterCategory.equals("All")) {
                active.removeIf(t -> !selectedFilterCategory.equalsIgnoreCase(t.getCategory()));
                completed.removeIf(t -> !selectedFilterCategory.equalsIgnoreCase(t.getCategory()));
            }

            // Filter by search query (title match)
            if (!searchQuery.isEmpty()) {
                String q = searchQuery.toLowerCase(Locale.ROOT);
                active.removeIf(t -> t.getTitle() == null
                        || !t.getTitle().toLowerCase(Locale.ROOT).contains(q));
                completed.removeIf(t -> t.getTitle() == null
                        || !t.getTitle().toLowerCase(Locale.ROOT).contains(q));
            }

            // Apply Sort
            sortTasksList(active);

            if (getActivity() == null) return;
            requireActivity().runOnUiThread(() -> {
                activeAdapter.setTasks(active);
                completedAdapter.setTasks(completed);
                updateHeroDueToday(dueToday);

                // Completed section visibility
                if (completed.isEmpty()) {
                    binding.completedSection.setVisibility(View.GONE);
                } else {
                    binding.completedSection.setVisibility(View.VISIBLE);
                    binding.completedLabel.setText(
                            getString(R.string.completed_tasks, completed.size()));
                }

                updateEmptyState();
            });
        });
    }

    /** 
     * Number of active tasks whose deadline falls on today's calendar date.
     */
    private int countDueToday(List<Task> tasks) {
        Calendar start = Calendar.getInstance();
        start.set(Calendar.HOUR_OF_DAY, 0);
        start.set(Calendar.MINUTE, 0);
        start.set(Calendar.SECOND, 0);
        start.set(Calendar.MILLISECOND, 0);
        Calendar end = (Calendar) start.clone();
        end.add(Calendar.DAY_OF_YEAR, 1);
        long startMs = start.getTimeInMillis();
        long endMs = end.getTimeInMillis();

        int count = 0;
        for (Task t : tasks) {
            long d = t.getDeadline();
            if (d >= startMs && d < endMs) {
                count++;
            }
        }
        return count;
    }

    private void updateHeroDueToday(int count) {
        if (binding == null || getActivity() == null) return;
        binding.textHeroCount.setText(String.valueOf(count));
        binding.textHeroDueLabel.setText(
                getResources().getQuantityString(R.plurals.hero_tasks_due, count, count));
    }

    private void sortTasksList(List<Task> tasks) {
        int sortType = prefs.getInt("pref_tasks_sort", 0); // 0 = Default (deadline), 1 = Newest, 2 = Oldest
        if (sortType == 1) {
            tasks.sort((t1, t2) -> Long.compare(t2.getCreatedAt(), t1.getCreatedAt()));
        } else if (sortType == 2) {
            tasks.sort((t1, t2) -> Long.compare(t1.getCreatedAt(), t2.getCreatedAt()));
        } else {
            // Default: tasks with deadlines first (ascending), then by newest created
            tasks.sort((t1, t2) -> {
                long d1 = t1.getDeadline();
                long d2 = t2.getDeadline();
                if (d1 > 0 && d2 > 0) {
                    return Long.compare(d1, d2);
                } else if (d1 > 0) {
                    return -1;
                } else if (d2 > 0) {
                    return 1;
                } else {
                    return Long.compare(t2.getCreatedAt(), t1.getCreatedAt());
                }
            });
        }
    }

    private void updateEmptyState() {
        boolean empty = activeAdapter.getItemCount() == 0
                && completedAdapter.getItemCount() == 0;
        binding.emptyState.setVisibility(empty ? View.VISIBLE : View.GONE);
        updateScrollability();
    }

    private void updateScrollability() {
        if (binding == null) return;
        binding.nestedScrollView.getViewTreeObserver().addOnGlobalLayoutListener(
                new android.view.ViewTreeObserver.OnGlobalLayoutListener() {
                    @Override
                    public void onGlobalLayout() {
                        if (binding == null) return;
                        binding.nestedScrollView.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                        View child = binding.nestedScrollView.getChildAt(0);
                        if (child != null) {
                            boolean canScroll = child.getHeight() > binding.nestedScrollView.getHeight();
                            if (binding.nestedScrollView.isNestedScrollingEnabled() != canScroll) {
                                binding.nestedScrollView.setNestedScrollingEnabled(canScroll);
                            }
                        }
                    }
                });
    }

    // ==================== ADD / EDIT TASK DIALOG ====================

    /** Add/edit dialog, delete confirmation and share all live in TaskDialogHelper. */
    private void showTaskDialog(@Nullable Task existingTask) {
        TaskDialogHelper.showTaskDialog(this, existingTask, taskDao, this);
    }

    @Override
    public void onTasksChanged() {
        loadTasks();
    }

    // ==================== TASK CALLBACKS ====================

    @Override
    public void onTaskCheckedChanged(Task task, boolean isChecked) {
        task.setCompleted(isChecked);
        Executors.newSingleThreadExecutor().execute(() -> {
            taskDao.update(task);
            if (isChecked) {
                AlarmScheduler.cancelAlarm(requireContext(), task);
            } else if (task.getDeadline() > System.currentTimeMillis()) {
                AlarmScheduler.scheduleAlarm(requireContext(), task);
            }
            if (getActivity() != null) {
                requireActivity().runOnUiThread(this::loadTasks);
            }
        });
    }

    @Override
    public void onTaskClick(Task task) {
        showTaskDialog(task);
    }

    @Override
    public void onTaskDelete(Task task) {
        TaskDialogHelper.deleteTaskConfirm(this, task, taskDao, this);
    }

    @Override
    public void onTaskShare(Task task) {
        TaskDialogHelper.shareTask(this, task);
    }

    @Override
    public void onTaskArchive(Task task) {
        task.setArchived(true);
        Executors.newSingleThreadExecutor().execute(() -> {
            taskDao.update(task);
            AlarmScheduler.cancelAlarm(requireContext(), task);
            if (getActivity() == null) return;
            requireActivity().runOnUiThread(() -> {
                loadTasks();
                Snackbar.make(binding.getRoot(), R.string.task_archived, Snackbar.LENGTH_LONG)
                        .setAction(R.string.undo, v -> {
                            task.setArchived(false);
                            Executors.newSingleThreadExecutor().execute(() -> {
                                taskDao.update(task);
                                if (task.getDeadline() > 0 && task.getDeadline() > System.currentTimeMillis() && !task.isCompleted()) {
                                    AlarmScheduler.scheduleAlarm(requireContext(), task);
                                }
                                if (getActivity() != null) {
                                    requireActivity().runOnUiThread(TasksFragment.this::loadTasks);
                                }
                            });
                        })
                        .show();
            });
        });
    }

    private void clearCompletedTasks() {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(getString(R.string.clear_completed_title))
                .setMessage(getString(R.string.clear_completed_message))
                .setPositiveButton(getString(R.string.clear_all), (dialog, which) -> {
                    Executors.newSingleThreadExecutor().execute(() -> {
                        taskDao.deleteAllCompleted();
                        if (getActivity() != null) {
                            requireActivity().runOnUiThread(this::loadTasks);
                        }
                    });
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    // ==================== PDF EXPORT ====================

    private void exportPdf() {
        Executors.newSingleThreadExecutor().execute(() -> {
            List<Task> tasks = taskDao.getActiveTasks();

            if (tasks.isEmpty()) {
                if (getActivity() != null) {
                    requireActivity().runOnUiThread(() ->
                            Toast.makeText(requireContext(), R.string.pdf_no_tasks, Toast.LENGTH_SHORT).show());
                }
                return;
            }

            boolean success = PdfExportUtil.exportTasks(requireContext(), tasks);

            if (getActivity() != null) {
                requireActivity().runOnUiThread(() -> {
                    Toast.makeText(requireContext(),
                            success ? R.string.pdf_exported : R.string.pdf_export_failed,
                            Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
