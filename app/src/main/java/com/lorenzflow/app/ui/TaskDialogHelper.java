package com.lorenzflow.app.ui;

import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.Context;
import android.content.Intent;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.lorenzflow.app.R;
import com.lorenzflow.app.data.Task;
import com.lorenzflow.app.data.TaskDao;
import com.lorenzflow.app.databinding.DialogAddTaskBinding;
import com.lorenzflow.app.util.AlarmScheduler;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.Executors;

/**
 * Single implementation of the task dialog, delete confirmation and share
 * sheet. TasksFragment and CalendarFragment used to carry near-identical
 * copies of this code; both now delegate here so the behaviour (including
 * alarm scheduling) can only be fixed in one place.
 */
public final class TaskDialogHelper {

    /** Callback for changes that need a list refresh. Always invoked on the UI thread. */
    public interface Host {
        void onTasksChanged();
    }

    private static final SimpleDateFormat DEADLINE_FORMAT =
            new SimpleDateFormat("MMM dd, yyyy · hh:mm a", Locale.getDefault());

    private TaskDialogHelper() {
    }

    /**
     * Shows the add (existingTask == null) or edit task dialog.
     */
    public static void showTaskDialog(Fragment fragment,
                                      @Nullable Task existingTask,
                                      TaskDao taskDao,
                                      Host host) {
        if (fragment.getContext() == null) return;

        final Context appContext = fragment.requireContext().getApplicationContext();
        DialogAddTaskBinding dialogBinding = DialogAddTaskBinding.inflate(fragment.getLayoutInflater());
        final long[] selectedDeadline = {existingTask != null ? existingTask.getDeadline() : 0};

        if (existingTask != null) {
            dialogBinding.editTaskTitle.setText(existingTask.getTitle());
            dialogBinding.editTaskDescription.setText(existingTask.getDescription());
            if (existingTask.getDeadline() > 0) {
                dialogBinding.btnSetDeadline.setText(DEADLINE_FORMAT.format(new Date(existingTask.getDeadline())));
            }
            preselectCategory(dialogBinding, existingTask.getCategory());
        }

        dialogBinding.btnSetDeadline.setOnClickListener(v -> pickDeadline(fragment, dialogBinding, selectedDeadline));

        String title = existingTask != null
                ? fragment.getString(R.string.edit_task)
                : fragment.getString(R.string.add_task);

        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(fragment.requireContext())
                .setTitle(title)
                .setView(dialogBinding.getRoot())
                .setPositiveButton(R.string.save, (dialog, which) -> {
                    String taskTitle = textOf(dialogBinding.editTaskTitle.getText());
                    if (taskTitle.isEmpty()) return;
                    String description = textOf(dialogBinding.editTaskDescription.getText());
                    String category = selectedCategory(dialogBinding);

                    final Task task = existingTask != null ? existingTask : new Task();
                    task.setTitle(taskTitle);
                    task.setDescription(description);
                    task.setDeadline(selectedDeadline[0]);
                    task.setCategory(category);

                    Executors.newSingleThreadExecutor().execute(() -> {
                        if (existingTask != null) {
                            taskDao.update(task);
                        } else {
                            long id = taskDao.insert(task);
                            task.setId(id);
                        }

                        AlarmScheduler.cancelAlarm(appContext, task);
                        if (!task.isCompleted()
                                && task.getDeadline() > System.currentTimeMillis()) {
                            AlarmScheduler.scheduleAlarm(appContext, task);
                        }

                        if (fragment.getActivity() != null) {
                            fragment.requireActivity().runOnUiThread(host::onTasksChanged);
                        }
                    });
                })
                .setNegativeButton(R.string.cancel, null);

        if (existingTask != null) {
            builder.setNeutralButton(R.string.delete,
                    (dialog, which) -> deleteTaskConfirm(fragment, existingTask, taskDao, host));
        }

        builder.show();
    }

    /** Confirms and deletes a task, cancelling its deadline alarm. */
    public static void deleteTaskConfirm(Fragment fragment,
                                         Task task,
                                         TaskDao taskDao,
                                         Host host) {
        if (fragment.getContext() == null) return;

        final Context appContext = fragment.requireContext().getApplicationContext();

        new MaterialAlertDialogBuilder(fragment.requireContext())
                .setTitle(R.string.delete)
                .setMessage(fragment.getString(R.string.delete_task_confirm, task.getTitle()))
                .setPositiveButton(R.string.delete, (dialog, which) ->
                        Executors.newSingleThreadExecutor().execute(() -> {
                            taskDao.delete(task);
                            AlarmScheduler.cancelAlarm(appContext, task);
                            if (fragment.getActivity() == null) return;
                            fragment.requireActivity().runOnUiThread(() -> {
                                Toast.makeText(appContext, R.string.task_deleted, Toast.LENGTH_SHORT).show();
                                host.onTasksChanged();
                            });
                        }))
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    /** Opens the system share sheet for a task. */
    public static void shareTask(Fragment fragment, Task task) {
        if (fragment.getContext() == null) return;

        StringBuilder content = new StringBuilder(task.getTitle() == null ? "" : task.getTitle());
        if (task.getDescription() != null && !task.getDescription().isEmpty()) {
            content.append("\n\n").append(task.getDescription());
        }
        if (task.getDeadline() > 0) {
            content.append("\n\n").append(
                    fragment.getString(R.string.share_deadline,
                            DEADLINE_FORMAT.format(new Date(task.getDeadline()))));
        }

        Intent sendIntent = new Intent();
        sendIntent.setAction(Intent.ACTION_SEND);
        sendIntent.putExtra(Intent.EXTRA_TEXT, content.toString());
        sendIntent.setType("text/plain");
        fragment.startActivity(Intent.createChooser(
                sendIntent, fragment.getString(R.string.transfer_task_to)));
    }

    private static void pickDeadline(Fragment fragment,
                                     DialogAddTaskBinding dialogBinding,
                                     long[] selectedDeadline) {
        Calendar cal = Calendar.getInstance();
        if (selectedDeadline[0] > 0) {
            cal.setTimeInMillis(selectedDeadline[0]);
        }

        new DatePickerDialog(fragment.requireContext(), (view, year, month, day) -> {
            Calendar deadlineCal = Calendar.getInstance();
            deadlineCal.set(year, month, day);

            new TimePickerDialog(fragment.requireContext(), (timeView, hour, minute) -> {
                deadlineCal.set(Calendar.HOUR_OF_DAY, hour);
                deadlineCal.set(Calendar.MINUTE, minute);
                deadlineCal.set(Calendar.SECOND, 0);
                selectedDeadline[0] = deadlineCal.getTimeInMillis();
                dialogBinding.btnSetDeadline.setText(DEADLINE_FORMAT.format(new Date(selectedDeadline[0])));
            }, cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), false).show();
        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show();
    }

    private static void preselectCategory(DialogAddTaskBinding dialogBinding, @Nullable String category) {
        if (category == null) return;
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

    private static String selectedCategory(DialogAddTaskBinding dialogBinding) {
        int checkedChipId = dialogBinding.chipGroupCategory.getCheckedChipId();
        if (checkedChipId == R.id.chip_work) return "Work";
        if (checkedChipId == R.id.chip_personal) return "Personal";
        if (checkedChipId == R.id.chip_school) return "School";
        if (checkedChipId == R.id.chip_wellness) return "Wellness";
        if (checkedChipId == R.id.chip_urgent) return "Urgent";
        return "General";
    }

    private static String textOf(@Nullable CharSequence value) {
        return value == null ? "" : value.toString().trim();
    }
}
