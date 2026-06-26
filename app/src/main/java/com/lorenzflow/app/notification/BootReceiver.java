package com.lorenzflow.app.notification;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import com.lorenzflow.app.data.AppDatabase;
import com.lorenzflow.app.data.Task;

import java.util.List;
import java.util.concurrent.Executors;

/**
 * Re-schedules all pending task alarms after a device reboot.
 */
public class BootReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || !Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            return;
        }

        // Re-register alarms on a background thread
        Executors.newSingleThreadExecutor().execute(() -> {
            AppDatabase db = AppDatabase.getInstance(context);
            List<Task> tasks = db.taskDao().getActiveTasksWithDeadline();
            long now = System.currentTimeMillis();

            for (Task task : tasks) {
                if (task.getDeadline() > now) {
                    scheduleAlarm(context, task);
                }
            }
        });
    }

    private void scheduleAlarm(Context context, Task task) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) return;

        Intent alarmIntent = new Intent(context, AlarmReceiver.class);
        alarmIntent.putExtra(AlarmReceiver.EXTRA_TASK_ID, (int) task.getId());
        alarmIntent.putExtra(AlarmReceiver.EXTRA_TASK_TITLE, task.getTitle());

        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                context,
                (int) task.getId(),
                alarmIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        // Try exact alarm, fall back to inexact if permission not granted
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (alarmManager.canScheduleExactAlarms()) {
                alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP, task.getDeadline(), pendingIntent);
            } else {
                alarmManager.setAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP, task.getDeadline(), pendingIntent);
            }
        } else {
            alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP, task.getDeadline(), pendingIntent);
        }
    }
}
