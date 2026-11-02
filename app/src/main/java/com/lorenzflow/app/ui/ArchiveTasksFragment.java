package com.lorenzflow.app.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.lorenzflow.app.R;
import com.lorenzflow.app.data.AppDatabase;
import com.lorenzflow.app.data.Task;
import com.lorenzflow.app.data.TaskDao;
import com.lorenzflow.app.databinding.FragmentArchiveTasksBinding;
import com.lorenzflow.app.databinding.ItemArchivedTaskBinding;
import com.lorenzflow.app.util.AlarmScheduler;

import com.lorenzflow.app.util.AppExecutors;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;

public class ArchiveTasksFragment extends Fragment {

    private FragmentArchiveTasksBinding binding;
    private TaskDao taskDao;
    private ArchivedTaskAdapter adapter;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentArchiveTasksBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        taskDao = AppDatabase.getInstance(requireContext()).taskDao();

        // Back navigation via toolbar nav icon
        binding.archiveToolbar.setNavigationOnClickListener(v ->
                requireActivity().onBackPressed());

        // RecyclerView setup
        adapter = new ArchivedTaskAdapter();
        binding.recyclerArchivedTasks.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.recyclerArchivedTasks.setAdapter(adapter);

        // Clear archive button
        binding.btnClearArchive.setOnClickListener(v -> confirmClearArchive());

        loadArchivedTasks();
    }

    private void loadArchivedTasks() {
        AppExecutors.io().execute(() -> {
            List<Task> archived = taskDao.getArchivedTasks();
            if (getActivity() == null) return;
            requireActivity().runOnUiThread(() -> {
                adapter.setTasks(archived);
                boolean empty = archived.isEmpty();
                binding.archiveEmptyState.setVisibility(empty ? View.VISIBLE : View.GONE);
                binding.recyclerArchivedTasks.setVisibility(empty ? View.GONE : View.VISIBLE);
                binding.btnClearArchive.setVisibility(empty ? View.GONE : View.VISIBLE);
            });
        });
    }

    private void restoreTask(Task task) {
        task.setArchived(false);
        AppExecutors.io().execute(() -> {
            taskDao.update(task);

            // Re-schedule the deadline alarm, mirroring TasksFragment's
            // undo-restore: only for incomplete tasks with a future deadline
            if (!task.isCompleted() && task.getDeadline() > System.currentTimeMillis()) {
                AlarmScheduler.scheduleAlarm(requireContext(), task);
            }

            if (getActivity() != null) {
                requireActivity().runOnUiThread(this::loadArchivedTasks);
            }
        });
    }

    private void deleteArchivedTask(Task task) {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.delete)
                .setMessage(getString(R.string.permanently_delete_confirm, task.getTitle()))
                .setPositiveButton(R.string.delete, (dialog, which) ->
                        AppExecutors.io().execute(() -> {
                            taskDao.delete(task);
                            if (getActivity() != null) {
                                requireActivity().runOnUiThread(this::loadArchivedTasks);
                            }
                        }))
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void confirmClearArchive() {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.clear_archive)
                .setMessage(getString(R.string.delete_all_archived_tasks_confirm))
                .setPositiveButton(R.string.clear_archive, (dialog, which) ->
                        AppExecutors.io().execute(() -> {
                            taskDao.deleteAllArchived();
                            if (getActivity() != null) {
                                requireActivity().runOnUiThread(this::loadArchivedTasks);
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

    private class ArchivedTaskAdapter extends RecyclerView.Adapter<ArchivedTaskAdapter.ViewHolder> {

        private List<Task> tasks = new ArrayList<>();

        void setTasks(List<Task> tasks) {
            this.tasks = new ArrayList<>(tasks);
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            ItemArchivedTaskBinding b = ItemArchivedTaskBinding.inflate(
                    LayoutInflater.from(parent.getContext()), parent, false);
            return new ViewHolder(b);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            holder.bind(tasks.get(position));
        }

        @Override
        public int getItemCount() {
            return tasks.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            private final ItemArchivedTaskBinding b;

            ViewHolder(ItemArchivedTaskBinding b) {
                super(b.getRoot());
                this.b = b;
            }

            void bind(Task task) {
                b.textArchivedTaskTitle.setText(task.getTitle());

                if (task.getDescription() != null && !task.getDescription().isEmpty()) {
                    b.textArchivedTaskDescription.setText(task.getDescription());
                    b.textArchivedTaskDescription.setVisibility(View.VISIBLE);
                } else {
                    b.textArchivedTaskDescription.setVisibility(View.GONE);
                }

                if (task.getCategory() != null && !task.getCategory().isEmpty()) {
                    b.textArchivedTaskCategory.setText(task.getCategory());
                    b.textArchivedTaskCategory.setVisibility(View.VISIBLE);

                    int textColor;
                    int bgColor;
                    switch (task.getCategory()) {
                        case "Work":      textColor = 0xFF5856D6; bgColor = 0x1A5856D6; break;
                        case "Personal":  textColor = 0xFF34C759; bgColor = 0x1A34C759; break;
                        case "School":    textColor = 0xFFFF9500; bgColor = 0x1AFF9500; break;
                        case "Wellness":  textColor = 0xFF5AC8FA; bgColor = 0x1A5AC8FA; break;
                        case "Urgent":    textColor = 0xFFFF3B30; bgColor = 0x1AFF3B30; break;
                        default:          textColor = 0xFF8E8E93; bgColor = 0x1A8E8E93; break;
                    }
                    b.textArchivedTaskCategory.setTextColor(textColor);
                    b.textArchivedTaskCategory.getBackground().setTint(bgColor);
                } else {
                    b.textArchivedTaskCategory.setVisibility(View.GONE);
                }

                b.btnRestoreTask.setOnClickListener(v -> restoreTask(task));
                b.btnDeleteArchivedTask.setOnClickListener(v -> deleteArchivedTask(task));
            }
        }
    }
}
