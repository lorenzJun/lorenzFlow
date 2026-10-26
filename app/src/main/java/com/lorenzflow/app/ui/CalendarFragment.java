package com.lorenzflow.app.ui;

import android.app.AlarmManager;
import android.app.DatePickerDialog;
import android.app.PendingIntent;
import android.app.TimePickerDialog;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import com.lorenzflow.app.R;
import com.lorenzflow.app.data.AppDatabase;
import com.lorenzflow.app.data.Task;
import com.lorenzflow.app.data.TaskDao;
import com.lorenzflow.app.databinding.DialogAddTaskBinding;
import com.lorenzflow.app.databinding.FragmentCalendarBinding;
import com.lorenzflow.app.notification.AlarmReceiver;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executors;

public class CalendarFragment extends Fragment implements TaskAdapter.OnTaskActionListener {

    private FragmentCalendarBinding binding;
    private TaskAdapter adapter;
    private TaskDao taskDao;

    private List<Task> allTasksWithDeadline = new ArrayList<>();
    private final Calendar selectedCalendar = Calendar.getInstance();

    private final SimpleDateFormat sdfHeader =
            new SimpleDateFormat("MMMM dd, yyyy", Locale.getDefault());
    private final SimpleDateFormat sdfDialog =
            new SimpleDateFormat("MMM dd, yyyy · hh:mm a", Locale.getDefault());

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
        // Set initial header to today
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

    // ==================== TASK CALLBACKS ====================

    @Override
    public void onTaskCheckedChanged(Task task, boolean isChecked) {
        task.setCompleted(isChecked);
        Executors.newSingleThreadExecutor().execute(() -> {
            taskDao.update(task);
            if (isChecked) {
                cancelAlarm(task);
            } else if (task.getDeadline() > System.currentTimeMillis()) {
                scheduleAlarm(task);
            }
            // Reload all tasks from database and refresh
            loadAllTasks();
        });
    }

    @Override
    public void onTaskClick(Task task) {
        showTaskDialog(task);
    }

    @Override
    public void onTaskDelete(Task task) {
        deleteTaskConfirm(task);
    }

    @Override
    public void onTaskShare(Task task) {
        Intent sendIntent = new Intent();
        sendIntent.setAction(Intent.ACTION_SEND);

        String shareContent = task.getTitle();
        if (task.getDescription() != null && !task.getDescription().isEmpty()) {
            shareContent += "\n\n" + task.getDescription();
        }
        if (task.getDeadline() > 0) {
            shareContent += "\n\nDeadline: " + sdfDialog.format(new Date(task.getDeadline()));
        }

        sendIntent.putExtra(Intent.EXTRA_TEXT, shareContent);
        sendIntent.setType("text/plain");
        Intent shareIntent = Intent.createChooser(sendIntent, getString(R.string.transfer_task_to));
        startActivity(shareIntent);
    }

    @Override
    public void onTaskArchive(Task task) {
        task.setArchived(true);
        Executors.newSingleThreadExecutor().execute(() -> {
            taskDao.update(task);
            cancelAlarm(task);
            if (getActivity() == null) return;
            requireActivity().runOnUiThread(() -> {
                loadAllTasks();
                Snackbar.make(binding.getRoot(), R.string.task_archived, Snackbar.LENGTH_LONG)
                        .setAction(R.string.undo, v -> {
                            task.setArchived(false);
                            Executors.newSingleThreadExecutor().execute(() -> {
                                taskDao.update(task);
                                if (task.getDeadline() > 0 && task.getDeadline() > System.currentTimeMillis() && !task.isCompleted()) {
                                    scheduleAlarm(task);
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

    private void showTaskDialog(Task task) {
        DialogAddTaskBinding dialogBinding = DialogAddTaskBinding.inflate(getLayoutInflater());
        final long[] selectedDeadline = {task.getDeadline()};

        dialogBinding.editTaskTitle.setText(task.getTitle());
        dialogBinding.editTaskDescription.setText(task.getDescription());
        if (task.getDeadline() > 0) {
            dialogBinding.btnSetDeadline.setText(sdfDialog.format(new Date(task.getDeadline())));
        }

        // Pre-select category
        String category = task.getCategory();
        if (category != null) {
            switch (category) {
                case "Work":
                    dialogBinding.chipWork.setChecked(true);
                    break;
                case "Personal":
                    dialogBinding.chipPersonal.setChecked(true);
                    break;
                case "School":
                    dialogBinding.chipSchool.setChecked(true);
                    break;
                case "Wellness":
                    dialogBinding.chipWellness.setChecked(true);
                    break;
                case "Urgent":
                    dialogBinding.chipUrgent.setChecked(true);
                    break;
                case "General":
                default:
                    dialogBinding.chipGeneral.setChecked(true);
                    break;
            }
        }

        // Deadline picker
        dialogBinding.btnSetDeadline.setOnClickListener(v -> {
            Calendar cal = Calendar.getInstance();
            if (selectedDeadline[0] > 0) {
                cal.setTimeInMillis(selectedDeadline[0]);
            }
            new DatePickerDialog(requireContext(), (view, year, month, day) -> {
                Calendar deadlineCal = Calendar.getInstance();
                deadlineCal.set(year, month, day);

                new TimePickerDialog(requireContext(), (timeView, hour, minute) -> {
                    deadlineCal.set(Calendar.HOUR_OF_DAY, hour);
                    deadlineCal.set(Calendar.MINUTE, minute);
                    deadlineCal.set(Calendar.SECOND, 0);
                    selectedDeadline[0] = deadlineCal.getTimeInMillis();
                    dialogBinding.btnSetDeadline.setText(sdfDialog.format(new Date(selectedDeadline[0])));
                }, cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), false).show();
            }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show();
        });

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(getString(R.string.edit_task))
                .setView(dialogBinding.getRoot())
                .setPositiveButton(R.string.save, (dialog, which) -> {
                    String title = dialogBinding.editTaskTitle.getText() != null
                            ? dialogBinding.editTaskTitle.getText().toString().trim() : "";
                    String desc = dialogBinding.editTaskDescription.getText() != null
                            ? dialogBinding.editTaskDescription.getText().toString().trim() : "";

                    if (title.isEmpty()) return;

                    // Get category
                    String selectedCat = "General";
                    int checkedChipId = dialogBinding.chipGroupCategory.getCheckedChipId();
                    if (checkedChipId == R.id.chip_work) {
                        selectedCat = "Work";
                    } else if (checkedChipId == R.id.chip_personal) {
                        selectedCat = "Personal";
                    } else if (checkedChipId == R.id.chip_school) {
                        selectedCat = "School";
                    } else if (checkedChipId == R.id.chip_wellness) {
                        selectedCat = "Wellness";
                    } else if (checkedChipId == R.id.chip_urgent) {
                        selectedCat = "Urgent";
                    }

                    task.setTitle(title);
                    task.setDescription(desc);
                    task.setDeadline(selectedDeadline[0]);
                    task.setCategory(selectedCat);

                    Executors.newSingleThreadExecutor().execute(() -> {
                        taskDao.update(task);
                        cancelAlarm(task);
                        if (task.getDeadline() > System.currentTimeMillis() && !task.isCompleted()) {
                            scheduleAlarm(task);
                        }
                        loadAllTasks();
                    });
                })
                .setNegativeButton(R.string.cancel, null)
                .setNeutralButton(R.string.delete, (dialog, which) -> deleteTaskConfirm(task))
                .show();
    }

    private void deleteTaskConfirm(Task task) {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.delete)
                .setMessage(getString(R.string.delete_task_confirm, task.getTitle()))
                .setPositiveButton(R.string.delete, (dialog, which) -> {
                    Executors.newSingleThreadExecutor().execute(() -> {
                        taskDao.delete(task);
                        cancelAlarm(task);
                        if (getActivity() != null) {
                            requireActivity().runOnUiThread(() -> {
                                Toast.makeText(requireContext(), R.string.task_deleted, Toast.LENGTH_SHORT).show();
                                loadAllTasks();
                            });
                        }
                    });
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    // ==================== ALARM MANAGEMENT ====================

    private void scheduleAlarm(Task task) {
        Context ctx = requireContext().getApplicationContext();
        AlarmManager alarmManager = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) return;

        Intent intent = new Intent(ctx, AlarmReceiver.class);
        intent.putExtra(AlarmReceiver.EXTRA_TASK_ID, (int) task.getId());
        intent.putExtra(AlarmReceiver.EXTRA_TASK_TITLE, task.getTitle());

        PendingIntent pi = PendingIntent.getBroadcast(
                ctx, (int) task.getId(), intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (alarmManager.canScheduleExactAlarms()) {
                alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP, task.getDeadline(), pi);
            } else {
                alarmManager.setAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP, task.getDeadline(), pi);
            }
        } else {
            alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP, task.getDeadline(), pi);
        }
    }

    private void cancelAlarm(Task task) {
        Context ctx = requireContext().getApplicationContext();
        AlarmManager alarmManager = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) return;

        Intent intent = new Intent(ctx, AlarmReceiver.class);
        PendingIntent pi = PendingIntent.getBroadcast(
                ctx, (int) task.getId(), intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        alarmManager.cancel(pi);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
