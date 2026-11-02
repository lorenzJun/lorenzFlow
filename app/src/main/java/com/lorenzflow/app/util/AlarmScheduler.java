package com.lorenzflow.app.util;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import com.lorenzflow.app.data.Task;
import com.lorenzflow.app.notification.AlarmReceiver;

/**
 * Shared task-deadline alarm helpers. The single canonical implementation
 * of the scheduling convention documented in CLAUDE.md:
 * setExactAndAllowWhileIdle + canScheduleExactAlarms() check +
 * SecurityException fallback to an inexact alarm.
 *
 * All callers pass a Context (app or activity — an app context is derived
 * internally); PendingIntent codes stay (int) task.getId() with
 * FLAG_UPDATE_CURRENT | FLAG_IMMUTABLE.
 */
public final class AlarmScheduler {

    private AlarmScheduler() {
    }

    /** Schedules an exact (or best-effort inexact) deadline alarm for a task. */
    public static void scheduleAlarm(Context context, Task task) {
        Context ctx = context.getApplicationContext();
        AlarmManager alarmManager = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) return;

        Intent intent = new Intent(ctx, AlarmReceiver.class);
        intent.putExtra(AlarmReceiver.EXTRA_TASK_ID, (int) task.getId());
        intent.putExtra(AlarmReceiver.EXTRA_TASK_TITLE, task.getTitle());

        PendingIntent pi = PendingIntent.getBroadcast(
                ctx, (int) task.getId(), intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        try {
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
        } catch (SecurityException e) {
            e.printStackTrace();
            // Fallback to inexact alarm if permission is missing/revoked
            alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP, task.getDeadline(), pi);
        }
    }

    /** Cancels the task's deadline alarm, if one is registered. */
    public static void cancelAlarm(Context context, Task task) {
        Context ctx = context.getApplicationContext();
        AlarmManager alarmManager = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) return;

        Intent intent = new Intent(ctx, AlarmReceiver.class);
        PendingIntent pi = PendingIntent.getBroadcast(
                ctx, (int) task.getId(), intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        alarmManager.cancel(pi);
    }
}
