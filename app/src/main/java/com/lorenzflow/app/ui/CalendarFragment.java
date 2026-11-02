package com.lorenzflow.app.ui;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.lorenzflow.app.R;
import com.lorenzflow.app.data.AppDatabase;
import com.lorenzflow.app.data.Task;
import com.lorenzflow.app.data.TaskDao;
import com.lorenzflow.app.databinding.FragmentCalendarBinding;
import com.lorenzflow.app.util.AlarmScheduler;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executors;

/**
 * Day view of tasks that have a deadline. The task dialog, delete confirmation
 * and share sheet are shared with TasksFragment via {@link TaskDialogHelper}.
 */
public class CalendarFragment extends Fragment
        implements TaskAdapter.OnTaskActionListener, TaskDialogHelper.Host {

    private FragmentCalendarBinding binding;
    private TaskAdapter adapter;
    private TaskDao taskDao;

    private List<Task> allTasksWithDeadline = new ArrayList<>();
    private final Calendar selectedCalendar = Calendar.getInstance();

    private final SimpleDateFormat sdfHeader =
            new SimpleDateFormat("MMMM dd, yyyy", Locale.getDefault());

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentCalendarBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        taskDao = AppDatabase.getInstance(requireContext()).taskDao();

        setupRecyclerView();
        setupCalendarView();
        loadAllTasks();
    }

    private void setupRecyclerView() {
        adapter = new TaskAdapter(this);
        binding.recyclerCalendarTasks.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.recyclerCalendarTasks.setAdapter(adapter);
    }

    private void setupCalendarView() {
        updateHeaderLabel();

        binding.calendarView.setOnDateChangeListener((calendarView, year, month, dayOfMonth) -> {
            selectedCalendar.set(Calendar.YEAR, year);
            selectedCalendar.set(Calendar.MONTH, month);
            selectedCalendar.set(Calendar.DAY_OF_MONTH, dayOfMonth);

            updateHeaderLabel();
            filterTasksForSelectedDate();
        });
    }

    private void updateHeaderLabel() {
        String formattedDate = sdfHeader.format(selectedCalendar.getTime());
        binding.textSelectedDate.setText(getString(R.string.tasks_due_on, formattedDate));
    }

    private void loadAllTasks() {
        Executors.newSingleThreadExecutor().execute(() -> {
            List<Task> tasks = taskDao.getActiveTasksWithDeadline();
            allTasksWithDeadline = tasks != null ? tasks : new ArrayList<>();

            if (getActivity() == null) return;
            requireActivity().runOnUiThread(this::filterTasksForSelectedDate);
        });
    }

    private void filterTasksForSelectedDate() {
        if (binding == null) return;

        List<Task> filtered = new ArrayList<>();
        for (Task task : allTasksWithDeadline) {
            if (isSameDay(task.getDeadline(), selectedCalendar.getTimeInMillis())) {
                filtered.add(task);
            }
        }

        adapter.setTasks(filtered);

        boolean empty = filtered.isEmpty();
        binding.emptyStateCalendar.setVisibility(empty ? View.VISIBLE : View.GONE);
        binding.recyclerCalendarTasks.setVisibility(empty ? View.GONE : View.VISIBLE);
    }

    private boolean isSameDay(long time1, long time2) {
        Calendar cal1 = Calendar.getInstance();
        cal1.setTimeInMillis(time1);
        Calendar cal2 = Calendar.getInstance();
        cal2.setTimeInMillis(time2);
        return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR)
                && cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR);
    }

    @Override
    public void onTasksChanged() {
        loadAllTasks();
    }

    // ==================== TASK CALLBACKS ====================

    @Override
    public void onTaskCheckedChanged(Task task, boolean isChecked) {
        task.setCompleted(isChecked);
        Context appContext = requireContext().getApplicationContext();
        Executors.newSingleThreadExecutor().execute(() -> {
            taskDao.update(task);
            if (isChecked) {
                AlarmScheduler.cancelAlarm(appContext, task);
            } else if (task.getDeadline() > System.currentTimeMillis()) {
                AlarmScheduler.scheduleAlarm(appContext, task);
            }
            if (getActivity() != null) {
                requireActivity().runOnUiThread(this::loadAllTasks);
            }
        });
    }

    @Override
    public void onTaskClick(Task task) {
        TaskDialogHelper.showTaskDialog(this, task, taskDao, this);
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
        Context appContext = requireContext().getApplicationContext();
        Executors.newSingleThreadExecutor().execute(() -> {
            taskDao.update(task);
            AlarmScheduler.cancelAlarm(appContext, task);
            if (getActivity() == null) return;
            requireActivity().runOnUiThread(() -> {
                loadAllTasks();
                com.google.android.material.snackbar.Snackbar
                        .make(binding.getRoot(), R.string.task_archived,
                                com.google.android.material.snackbar.Snackbar.LENGTH_LONG)
                        .setAction(R.string.undo, v -> {
                            task.setArchived(false);
                            Executors.newSingleThreadExecutor().execute(() -> {
                                taskDao.update(task);
                                if (!task.isCompleted()
                                        && task.getDeadline() > System.currentTimeMillis()) {
                                    AlarmScheduler.scheduleAlarm(appContext, task);
                                }
                                if (getActivity() != null) {
                                    requireActivity().runOnUiThread(CalendarFragment.this::loadAllTasks);
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
